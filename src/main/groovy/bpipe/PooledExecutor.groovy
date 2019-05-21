/*
 * Copyright (c) MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe

import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

import bpipe.executor.CommandExecutor
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic

 /**
  * An executor that starts a single job that can be reused by 
  * multiple commands.
  * <p>
  * The PooledExecutor starts a job which sits idle until it is 
  * passed a command to run. A command is passed by writing a file in a
  * specific format to its commandtmp directory. When this file appears, 
  * the pooled executor picks it up and executes it.
  * <p>
  * Terminology:
  *  <li>Host command - the permanently running command that sits idle until 
  *      a job runs.
  *  <li>Hosted command - the actual job commands that are doing the work. The
  *      host command executes the hosted commands.
  */
@Mixin(ForwardHost)
class PooledExecutor implements CommandExecutor {
    
    static Logger log = Logger.getLogger('PooledExecutor')
    
    public static final long serialVersionUID = 0L
    
    /**
     * The executor that will run the command
     */
    CommandExecutor executor
    
    /**
     * The dummy command object used to initialize the execution; not the real command!
     */
    Command command
    
    /**
     * The state of the currently active command
     */
    CommandStatus state = CommandStatus.UNKNOWN
    
    /**
     * The currently active command (if any)
     */
    Command activeCommand
    
    Map poolConfig
    
    /**
     * An output log that will forward to the real output log, when
     * the command activate
     */
    transient ForwardingOutputLog outputLog
    
    String currentCommandId = null
    
    transient Closure onFinish =  null
    
    /**
     * File that acts as a flag to tell this executor to stop
     */
    File stopFile
    
    File heartBeatFile
    
    String hostCommandId
    
    StorageLayer storage
    
    /**
     * Triggers the waiting pooled executor to start running a command.
     * <p>
     * Note: the pooled executor does not need to actually start the command: the command is already
     * waiting to start, and will start as soon as the command script appears.
     */
    @Override
    public void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        
        assert !(cfg instanceof ConfigObject)
        
        Path cmdScript = storage.toPath(".bpipe/commandtmp/$hostCommandId/" + ExecutorPool.POOLED_COMMAND_FILENAME + ".${currentCommandId}.sh")
        Path cmdScriptTmp = storage.toPath(".bpipe/commandtmp/$hostCommandId/" + ExecutorPool.POOLED_COMMAND_FILENAME + ".tmp")
        
        log.info "Write command $cmd.command to command script at $cmdScriptTmp"
        cmdScriptTmp.text = cmd.command
        
        log.info "File to move is created"
        Thread.sleep(2000)
        
        log.info "Move command from tmp file $cmdScriptTmp to $cmdScript"
        Files.move(cmdScriptTmp, cmdScript)
        
        log.info "Created pool executor command script $cmdScript using storage " + storage.class.name
        
        activeCommand = cmd
        state = CommandStatus.RUNNING 
        activeCommand.startTimeMs = System.currentTimeMillis()
        
        if(executor.respondsTo("setJobName")) {
            log.info "Setting job name"
            executor.setJobName(cmd.name)
        }
        else {
            log.info "Pooled executor unable to set job name (not supported)"
        }
    }
    
    String getStopHostedCommandFile() {
        
        assert currentCommandId != null // should only call this when a command is assigned
        
        File stopFile = new File(".bpipe/commandtmp/$hostCommandId/${currentCommandId}.pool.stop")
        return stopFile.path
    }
    
    final static List<String> ENDED_JOB_STATUSES = [CommandStatus.COMPLETE.name(), CommandStatus.UNKNOWN.name()]

   final private static int EXECUTOR_EXIT_FILE_TIMEOUT = 30000
    
    @Override
    int waitFor() {
        
        int exitCode = waitForExitFile()
        this.outputLog.flush()
        
        if(onFinish != null)
            onFinish()
        
        if(executor.respondsTo("setJobName")) {
            executor.setJobName(poolConfig.name)
        }            
        
        state = CommandStatus.COMPLETE
        
        return exitCode
    }
    
    @CompileStatic
    int waitForExitFile() {
        Path exitCodeFile = storage.toPath(".bpipe/commandtmp/$hostCommandId/${currentCommandId}.pool.exit")
        
        // wait until result status file appears
        int loopIterations = 0
        while(!Files.exists(exitCodeFile)) {
            Thread.sleep(1000)
            
            // Every 15 seconds, check that our underlying job was not killed
            if(loopIterations % 15 == 0) {
                String status = this.executor.status()
                if(status in ENDED_JOB_STATUSES) {
                    log.severe "Pooled job ${this.hostCommandId} in pool ${poolConfig.name} via storage $storage ended unexpectedly. Current command: ${currentCommandId}"
                    return 1;
                }
            }
            ++loopIterations
        }
        Thread.sleep(100)
        
        int exitCode =  (int)Utils.withRetries(3, message:"Read $exitCodeFile from ${storage.class.name}") {
            String text = new String(Files.readAllBytes(exitCodeFile),"UTF-8")
            log.info "Read [${text.trim()}] from exit code file"
            return text.trim().toInteger()
        }
    }
    
    File getPoolFile() {
       new File(".bpipe/pools/${poolConfig.name}/${hostCommandId}") 
    }
    
    void resolveStorage() {
        List<String> storageNames = poolConfig.containsKey('storage') ? poolConfig.storage.tokenize(',')*.trim() : ['local']
        
        for(storageName in storageNames) {
            StorageLayer storage = StorageLayer.create(storageName)
            if(this.storage == null)
                this.storage = storage
        }
    }
    
    /**
     * Compute the fraction of this pooled executor's lifetime that is remaining
     * before its walltime (if configured) expires.
     * <p>
     * If no walltime was configured, assumes infinite lifetime, returns 1.0
     * 
     * @return  double representing fraction of walltime remaining
     */
    double getTimeFractionRemaining() {
        
        String poolWalltimeConfig = this.poolConfig.get("walltime",null)
        
        // if no walltime set then assume this pool lasts forever
        if(!poolWalltimeConfig)
            return 1.0
            
        long poolWallTimeMs = Utils.walltimeToMs(poolWalltimeConfig)
        double timeRemainingMs = (double)(poolWallTimeMs - (System.currentTimeMillis() - this.command.createTimeMs));
        
        return timeRemainingMs  / poolWallTimeMs
    }
    
    /**
     * Return true if this pooled executor is able to run the given job,
     * based on its configuration.
     * 
     * @param cfg
     * @return
     */
    boolean canAccept(Map cfg) {
        
        // Check: is the amount of time left in my own job budget sufficient
        // to run the given command?
        String myWalltimeConfig = this.poolConfig.get("walltime",null)
        
        // How long does the job need?
        if(cfg.walltime && myWalltimeConfig) {
            
            long myWallTimeMs = Utils.walltimeToMs(myWalltimeConfig)
            long timeRemainingMs = myWallTimeMs - (System.currentTimeMillis() - this.command.createTimeMs);
            long walltimeMs = Utils.walltimeToMs(cfg.walltime)
            
            log.info "Pooled job $hostCommandId checking time remaining ($timeRemainingMs ms) compared to requirements ($walltimeMs)."
            if(walltimeMs > timeRemainingMs) {
                log.info "Pooled job $hostCommandId has insufficient time remaining ($timeRemainingMs ms) compared to requirements ($walltimeMs). Rejecting job request."
                return false;
            }
        }
        
        // TODO: check other attributes such as memory and procs
        return true
    }
    
    void execute(Command command, OutputLog log) {
        this.currentCommandId = command.id
        this.outputLog.wrapped = log
        command.executor = this
        
        // Note: this will trigger an event to the waiting command executor that
        // will make it write out the file and execute the command
        this.command.command = command.command
    }
    
    /**
     * Cause this pooled executor to start capturing from the stdout
     * and stderr files that its script will write to.
     */
    void captureOutput(ForwardingOutputLog outputLog) {
        
        if(outputLog == null)
            outputLog = new ForwardingOutputLog()
        
        this.outputLog = outputLog
        
        List<String> storageNames = Config.listValue(this.command.getProcessedConfig().getOrDefault('storage', 'local'))
        
        String storageName = storageNames[0]
        log.info "Capturing output via storage layer type $storageName"
        StorageLayer storage = StorageLayer.create(storageName)
        
        this.forward(storage.toPath(".bpipe/commandtmp/$command.id/pool.out"), outputLog)
        this.forward(storage.toPath(".bpipe/commandtmp/$command.id/pool.err"), outputLog)
    }
    
    /**
     * Stops the host command that executes jobs on behalf of the pool
     */
    void stopPooledExecutor() {
        
        try {
            this.executor.stop()
        }
        catch(Throwable e) {
            log.warning("Attempt to stop pooled executor returned error: " + e)
        }
            
        // Write out the stop flag
        Path stopFilePath = storage.toPath(this.stopFile.path)
        log.info "Writing stop file: $stopFilePath"
        stopFilePath.text = String.valueOf(System.currentTimeMillis())
        this.heartBeatFile.delete()
        
        String exitFilePath = ".bpipe/commandtmp/$hostCommandId/pool.exit"
        Utils.waitWithTimeout(EXECUTOR_EXIT_FILE_TIMEOUT) {
            storage.exists(exitFilePath)
        }.timeout {
            String msg = "Exit file $exitFilePath was not observed after ${EXECUTOR_EXIT_FILE_TIMEOUT}ms"
            log.warning(msg)
            System.err.println "WARNING: $msg"
        }
        
        log.info "Cleaning up executor for pool $command.id"
        this.executor.cleanup()
    }
    
    /**
     * Delete the persistent pool files associated with this executor.
     * These are stored in .bpipe/pools and are created to allow subsequent
     * pipelines to know about potentially running pooled executors.
     */
    void deletePoolFiles() {
        
        File poolFile = this.getPoolFile()
        log.info "Deleting pool file $poolFile for pooled executor $command.id"
        
        if(!poolFile.delete())
            poolFile.deleteOnExit()
            
        List poolFiles = (poolFile.parentFile.listFiles()?:[]) as List
        if(poolFiles.size() == 0) {
            poolFile.parentFile.deleteDir()
        }
    }
    
    void savePoolFile() {
        File poolFile = this.poolFile
        poolFile.parentFile.mkdirs()
        poolFile.withObjectOutputStream { oos -> oos.writeObject(this) }
    }

    @Override
    public String status() {
        return state.name();
    }

    /**
     * Stop the underlying command being executed by this executor. If the pool to which this
     * executor belongs is persistent, this will not stop the actual host command job, rather,
     * it will just write out a "stop" file asking the job to stop the underlying command itself
     * (accomplished with a "kill" or similar within the host job)
     */
    @Override
    @CompileStatic
    public void stop() {
        if(!poolConfig.get('persist', false)) {
            this.stopPooledExecutor()
        }
        else {
            new File(this.stopHostedCommandFile).text=System.currentTimeMillis()
        }
    }

    @Override
    public void cleanup() {
    }

    @Override
    public String statusMessage() {
        return "Pooled executor ${command.id} running underlying executor: " + this.executor;
    }

    @Override
    public List<String> getIgnorableOutputs() {
        return [];
    }

    @Override
    public String localPath(String storageName) {
        return this.executor.localPath(storageName);
    }

    @Override
    public void mountStorage(StorageLayer storage) {
        this.executor.mountStorage(storage)
    }
}