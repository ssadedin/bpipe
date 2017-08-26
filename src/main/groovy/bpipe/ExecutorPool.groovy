package bpipe

import java.nio.file.Files
import java.nio.file.Path
import java.util.List
import java.util.Map
import java.util.regex.Pattern
import org.apache.ivy.plugins.report.LogReportOutputter

import bpipe.executor.CommandExecutor
import bpipe.executor.CommandTemplate
import bpipe.executor.ThrottledDelegatingCommandExecutor
import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.util.logging.Log

enum RescheduleResult {
        SUCCEEDED,
        FAILED
}

 /**
  * An executor that starts a single job that can be reused by 
  * multiple commands.
  * <p>
  * The PooledExecutor starts a job which sits idle until it is 
  * passed a command to run. A command is passed by writing a file in a
  * specific format to its commandtmp directory. When this file appears, 
  * the pooled executor picks it up and executes it.
  */
@Log
@Mixin(ForwardHost)
class PooledExecutor implements CommandExecutor {
    
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
        cmdScriptTmp.text = cmd.command
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
                    log.severe "Pooled job ${this.hostCommandId} in pool ${poolConfig.name} ended unexpectedly: failing command ${currentCommandId}"
                    return 1;
                }
            }
            ++loopIterations
        }
        Thread.sleep(100)
        
        int exitCode =  Utils.withRetries(3, message:"Read $exitCodeFile from ${storage.class.name}") {
            String text = new String(Files.readAllBytes(exitCodeFile),"UTF-8")
            println "Read [$text] from exit code file"
            return text.trim().toInteger()
        }
    }
    
    File getPoolFile() {
       new File(".bpipe/pools/${poolConfig.name}/${hostCommandId}") 
    }
    
    void resolveStorage() {
        String storageName = poolConfig.containsKey('storage') ? poolConfig.storage : null
        this.storage = StorageLayer.create(storageName)
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
        this.forward(".bpipe/commandtmp/$command.id/pool.out", outputLog)
        this.forward(".bpipe/commandtmp/$command.id/pool.err", outputLog)
    }
    
    void stopPooledExecutor() {
        
        try {
            this.executor.stop()
        }
        catch(Exception e) {
            log.warning("Attempt to stop pooled executor returned error: " + e)
        }
            
        // Write out the stop flag
        log.info "Writing stop file: $stopFile"
        this.stopFile.text = String.valueOf(System.currentTimeMillis())
        this.heartBeatFile.delete()
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

    @Override
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
}

@Log
class ExecutorPool {
    
    public static final String POOLED_COMMAND_FILENAME = "pool_cmd"
    
    public static final String POOLED_COMMAND_STOP_FILENAME = "stop"
    
    /**
     * Fraction of walltime below which an existing executor will not be considered
     * usable if discovered as a persistent pool
     */
    public static final DEFAULT_POOL_RECREATE_THRESHOLD = 0.20
    
    Map cfg
    
    List<PooledExecutor> availableExecutors = []
    
    List<PooledExecutor> activeExecutors = []
    
    ExecutorFactory executorFactory
    
    long startTimeMs 
    
    int poolSize
    
    boolean persistent
    
    Map jobCfg
    
    List<String> configs
    
    ExecutorPool(ExecutorFactory executorFactory, Map poolCfg) {
        
        this.executorFactory = executorFactory
        this.cfg = poolCfg
        this.jobCfg = jobCfg
        
        def configs = poolCfg.get('configs') ?: [ poolCfg.name ]
        if(configs instanceof String) {
            configs = configs.tokenize(',')*.trim()
        }
        this.configs = configs

        this.poolSize = String.valueOf(poolCfg.get('jobs') ?: 1).toInteger()
        
        this.persistent = poolCfg.get('persist', false)

        log.info "Found pre-allocation pool $poolCfg.name for ${configs.join(',')} of size $poolSize"
    }
    
    /**
     * Launches a wrapper job that waits for child jobs to be assigned and executes them.
     * <p>
     * This job loops "forever" waiting for child jobs, which are assigned by writing a file
     * in the file "pool_cmd.<job_id>.sh". When such a file is observed, the wrapper job 
     * parses out the job id and executes the job.
     * <p>
     * One key aspect is how the wrapper job knows when to exit. It occurs via multiple
     * mechanisms:
     * <li> Bpipe writes a "stop" file as a flag that it should exit; this is checked
     *      once every second and the job exits as soon as this flag is observed.
     * <li> In case Bpipe is killed, a "heartbeat" file is checked every 15 seconds.
     *      at each 15 second interval, the file is removed. If the file is not recreated,
     *      the wrapper script exits. This ensures that it Bpipe exits, so too does the
     *      wrapper script, so we do not "leak" jobs.
     * <li> The job can be stopped by the regular "stop" mechanism that Bpipe uses 
     *      as well
     */
    synchronized void start() {
        
        startTimeMs = System.currentTimeMillis()
        
        List<PooledExecutor> existingPools = this.persistent ? searchForExistingPools() : []
        
        log.info "Found ${existingPools.size()} persistent commands in existing pool for $cfg.name "
        
        log.info "Creating / locating ${poolSize} executors for pool ${cfg.name}"
        
        // When some executors are from previously created persistent pools,
        // they may not be "usable" in that they may have too little time left
        // We still connect to them in case they are useful, but we do not count them
        // in the pool executors to be created since we want to ensure we have enough
        // usable executors
        int usablePoolExecutors = 0
        
        double timeFractionThreshold = 
            cfg.get('timeFractionThreshold', DEFAULT_POOL_RECREATE_THRESHOLD)
        
        while(usablePoolExecutors < poolSize) {
            
            // TODO: does a pre-existing command exist? if so, use it!
            PooledExecutor pe = acquirePooledExecutor(existingPools)
            double fracRemaining = pe.timeFractionRemaining
            if(fracRemaining > timeFractionThreshold){
                
                log.info "Executor $pe.command.id added as usable pooled command executor (has $fracRemaining of time left)"
                
                ++usablePoolExecutors
            }
            else {
                log.info "Executor $pe.command.id added but is below usable threshold (has only $fracRemaining of time left)"
            }
            
        }
        
        Poller.instance.timer.schedule(
            this.&replaceUnusableExecutors,CHECK_EXECUTORS_INTERVAL_SECONDS*1000, CHECK_EXECUTORS_INTERVAL_SECONDS*1000) 
    }
    
    /**
     * Acquire a usable executor and add it to our pool.
     * <p>
     * If a usable exeuctor is found in the provided list, it is initialised
     * and added to our pool. If not, a new executor is started.
     * 
     * @param existingExecutors
     * 
     * @return  the executor acquired
     */
    PooledExecutor acquirePooledExecutor(List<PooledExecutor> existingExecutors) {
        PooledExecutor pe  = null
        if(!existingExecutors.isEmpty()) {
            pe = existingExecutors.pop()
            log.info "Connecting pooled command of type $cfg.name to existing persistent pool: $pe.hostCommandId"

            connectPooledExecutor(pe)
        }

        if(pe == null) {
            pe = startNewPooledExecutor()
            log.info "Started command $pe.hostCommandId as pooled command of type $cfg.name"
        }

        pe.onFinish = {
            processFinishedExecutor(pe)
        }
        
        this.availableExecutors << pe
        return pe
    }
    
    void verify() {
        int timeoutSecs = 30
        Utils.waitWithTimeout(timeoutSecs*1000) {
            availableExecutors.any { PooledExecutor pe ->
                pe.executor.status() == CommandStatus.WAITING.name()
            }
        }.ok {
            
            // A little bit of time to start / run / fail
            Thread.sleep(5000)
            
            int countRunning = availableExecutors.count { PooledExecutor pe ->
                pe.executor.status() == CommandStatus.WAITING.name()
            }            
            
            if(countRunning == availableExecutors.size())
                return
                
            List<PooledExecutor> completeExecutors = availableExecutors.grep { PooledExecutor pe ->
                pe.executor.status() == CommandStatus.COMPLETE.name()
            }            
            
            if(!completeExecutors.isEmpty())
                throw new RuntimeException("ERROR: ${completeExecutors} executors terminated with exit codes: " + completeExecutors*.waitFor()*.toString().join(","))
            
        }.timeout {
            throw new RuntimeException("After waiting $timeoutSecs seconds, not all executors were running. Please check logs for details")
        }
    }
    
    void processFinishedExecutor(PooledExecutor pe) {
        
        // Is there a waiting job that can run using this job?
        if(allocateToExistingWaitingJob(pe)) {
            log.info "Pooled executor $pe.hostCommandId has been rescheduled"
            return
        }
        else {
            this.returnExecutorToPool(pe)
        }
    }
    
    synchronized boolean allocateToExistingWaitingJob(PooledExecutor pe) {
        
        log.info "Searching for waiting command to use pooled executor $pe.hostCommandId"
        
        List<Command> waitingCommands = CommandManager.executedCommands.grep {
            it.status == CommandStatus.WAITING
        }
        
        log.info "Checking ${waitingCommands.size()} commands for compatibility."
        Command compatibleCommand = waitingCommands.grep { Command command ->
            command.cfg.name in this.configs
        }.grep { Command command ->
            !(command.executor instanceof PooledExecutor)
        }.find { Command command ->
            pe.canAccept(command.cfg)
        }
        
        if(compatibleCommand == null) {
            log.info "No compatible waiting command for $pe.hostCommandId"
            return false
        }
        else {
            log.info "Command $compatibleCommand.id is compatible to reschedule to pooled executor $pe.hostCommandId"
            
            ThrottledDelegatingCommandExecutor delegator = compatibleCommand.executor
            
            // Ideally would move this inside the block below, but it prevents
            // initial messages from the stage from getting forwarded
            pe.execute(compatibleCommand, delegator.outputLog)
            
            RescheduleResult result = delegator.reschedule(pe)
            if(result == RescheduleResult.SUCCEEDED) {
                return true
            }
            else {
                pe.outputLog.wrapped = null
                return false
            }
        }
    }
    
    void returnExecutorToPool(PooledExecutor pe) {
        log.info "Adding pooled executor $pe.hostCommandId back into pool"
        availableExecutors << pe
        activeExecutors.remove(pe)
    }
    
    /**
     * Connect a previously started executor pool to this Bpipe
     * instance.
     * 
     * @param pe
     */
    void connectPooledExecutor(PooledExecutor pe) {
        pe.captureOutput(null)
        pe.onFinish = {
            log.info "Adding persistent pooled executor $pe.hostCommandId back into pool"
            availableExecutors << pe
        } 
    }
   
    static Pattern ALL_NUMBERS = ~/[0-9]{1,}/
    
    
    /**
     * Set of statuses for internal executor that are considered to still be active
     * ie: able to potentially run a job in the future.
     */
    static Set<CommandStatus> ACTIVE_STATUSES = [CommandStatus.WAITING, CommandStatus.RUNNING] as Set
    
    /**
     * Searches in the .bpipe/pools/<pool name> directory for any command pools 
     * that may still be running and usuable for executing commands.
     * 
     * @param name
     * @return
     */
    List<PooledExecutor> searchForExistingPools() {
        
        String name = cfg.name
        
        File poolDir = new File(".bpipe/pools/$name")
        if(!poolDir.exists()) {
            log.info "No existing pool witih name $name"
            return []
        }
        
        Map<CommandStatus,List<PooledExecutor>> executors = poolDir.listFiles().grep {
            it.name.matches(ALL_NUMBERS)
        }.collect { File f ->
            try {
                f.withObjectInputStream {  ois ->
                    ois.readObject()
                }
            }
            catch(Exception e) {
                log.severe "Unable to load command from $f: $e"
                // could clean up command here?
            }
        }.grep { 
            it != null // can happen if starting pooled executor fails?
        }.groupBy { PooledExecutor pe ->
            
            CommandStatus status = pe.command.executor.status() 
            
            log.info "Command state for $pe.command.id is $status"
            
            return status 
        }
        
        // Clean up old executors - anything not in active state
        List<PooledExecutor> nonRunningExecutors = executors.collect { !(it.key in ACTIVE_STATUSES) ? it.value : [] }.sum()
        if(nonRunningExecutors.size()>0) {
            log.info "Deleting files for ${nonRunningExecutors.size()} expired executors"
            nonRunningExecutors*.deletePoolFiles()
        }
        
        // Return anything in an active state
        return ACTIVE_STATUSES.collect { executors[it]?:[] }.sum()
    }
    
    static final long CHECK_EXECUTORS_INTERVAL_SECONDS = 120
    
    static final long HEARTBEAT_INTERVAL_SECONDS = 10
    
    PooledExecutor startNewPooledExecutor() {
        
        long nowMs = System.currentTimeMillis()
        Command cmd = new Command(
            name:cfg.name, 
            configName: cfg.name, 
            createTimeMs: nowMs,
            startTimeMs: nowMs,
            id: CommandId.newId(),
            dir: new File(".bpipe/pools/commands")
        )
        
        assert cfg.name != null
        assert cmd.configName != null
            
        Map execCfg = cmd.getConfig(null)
            
        CommandExecutor cmdExec = executorFactory.createExecutor(execCfg)
        cmd.executor = cmdExec
            
        String pooledCommandScript = ".bpipe/commandtmp/$cmd.id/$POOLED_COMMAND_FILENAME"
        String poolCommandStopFlag = ".bpipe/commandtmp/$cmd.id/$POOLED_COMMAND_STOP_FILENAME"
        File heartBeatFile = new File(".bpipe/commandtmp/$cmd.id/heartbeat")
            
        File stopFile = new File(".bpipe/commandtmp/${cmd.id}/$POOLED_COMMAND_STOP_FILENAME")
            
        def debugLog = ('debugPooledExecutor' in cfg) ? "true" : "false"
        
        PooledExecutor pe = new PooledExecutor(
            executor:cmdExec, 
            command: cmd, 
            hostCommandId: cmd.id
        )
        
        pe.poolConfig = this.cfg.collectEntries { it }
        pe.resolveStorage()
        
        
        
        cmd.command = new CommandTemplate().renderCommandTemplate("executor/pool-command.template.sh", 
            [
                cfg: cfg, 
                cmd: cmd,
                pooledExecutor: pe,
                persistent: persistent,
                poolFile: pe.getPoolFile().absolutePath,
                debugLog: debugLog,
                pooledCommandScript: pooledCommandScript,
                heartBeatFile: heartBeatFile,
                bpipeHome: Runner.BPIPE_HOME,
                HEARTBEAT_INTERVAL_SECONDS: HEARTBEAT_INTERVAL_SECONDS,
                stopFile: stopFile,
                bpipeUtilsShellCode: new File("${Runner.BPIPE_HOME}/bin/bpipe-utils.sh").text
            ])
            
        OutputLog outputLog = new ForwardingOutputLog()
            
        // Sometimes this can be the first code that runs and needs this
        new File(".bpipe/commands").mkdirs()
        new File(".bpipe/commandtmp").mkdirs()
            
        cmdExec.start(cfg.collectEntries { it }, cmd, outputLog, outputLog)
            
        cmd.createTimeMs = System.currentTimeMillis()
            
        Poller.instance.timer.schedule({
            if(!heartBeatFile.exists()) {
                heartBeatFile.absoluteFile.parentFile.mkdirs()
                heartBeatFile.text = String.valueOf(System.currentTimeMillis())
            }
        }, 0, HEARTBEAT_INTERVAL_SECONDS*1000)
        
        pe.captureOutput(outputLog)
        pe.stopFile = stopFile
        pe.heartBeatFile = heartBeatFile
        
        if(persistent) // If this is a persistent pool, save the executor in the shared pools directory (.bpipe/pools)
            pe.savePoolFile()
        return pe
    }
    
    synchronized void replaceUnusableExecutors() {
        
        double threshold = cfg.get('timeFractionThreshold', DEFAULT_POOL_RECREATE_THRESHOLD) 
        
        log.info "Checking if ${availableExecutors.size()} executors are still usable (threshold=$threshold)"
        
        // Remove executors that aren't running any more
        availableExecutors.grep { PooledExecutor pe ->
            pe.executor.status() != CommandStatus.RUNNING.name() && 
            pe.executor.status() != CommandStatus.WAITING.name()  
        }.each { PooledExecutor pe ->
            log.info "Removing pooled executor in state ${pe.status()} because no longer running"
            pe.stopPooledExecutor()
            pe.deletePoolFiles()
            availableExecutors.remove(pe)
        }
        
        // Count the usable executors
        int usableExecutors = (availableExecutors + activeExecutors).count { PooledExecutor pe ->
            if(pe.timeFractionRemaining >= threshold) {
                // usable
                return true
            }
            else {
                log.info "Executor $pe.hostCommandId is not usable: timeFracRemaining = $pe.timeFractionRemaining"
            }
        }
        
        log.info "There are ${usableExecutors} / $poolSize desired usable executors"
        
        while(usableExecutors<poolSize) {
            PooledExecutor pe = acquirePooledExecutor([])
            if(persistent)
                pe.savePoolFile()
            ++usableExecutors
        }
    }
    
    /**
     * Attempts to find a compatible executor in this pool.
     * If one is found, the exececutor is assigned and true is 
     * returned. Otherwise, false is returned and the executor is
     * unassigned.
     * 
     * @param command
     * @param outputLog
     * @return
     */
    synchronized boolean take(Command command, OutputLog outputLog) {
        
        if(availableExecutors.isEmpty())
            return false
        
        // Take the executor with the smallest time remaining that is
        // still usable
        PooledExecutor pe = availableExecutors.grep { PooledExecutor cpe ->
            cpe.canAccept(command.processedConfig)
        }.min { PooledExecutor cpe ->
            cpe.timeFractionRemaining
        }
        
        if(!pe) {
            log.info "No compatible pooled executor available from ${availableExecutors.size()} pooled commands for command $command.id (config=$command.configName)"
            return false
        }
        
        availableExecutors.remove(pe)
        activeExecutors.add(pe)
        
        pe.execute(command, outputLog)
        
        return true
    }
    
    @CompileStatic
    synchronized void shutdown() {
        log.info "Shutting down ${availableExecutors.size()} jobs in preallocation pool ${cfg.name}"
        for(PooledExecutor pe in availableExecutors) {
            pe.stop()
        }
    }
    
    /**
     * Preallocation pools, indexed by the name of the pool
     */
    static Map<String,ExecutorPool> pools = [:]
    
    /**
     * Read all the pools configuration and start the configured pools
     * 
     * @return  the number of distinct pools that were started
     */
    static int startPools(ExecutorFactory executorFactory, ConfigObject userConfig, boolean persistentOnly=false, boolean checkPools=false) {
        
        initPools(executorFactory, userConfig, persistentOnly)
        
        // Start all the pools
        pools*.value*.start()
        
        if(checkPools) {
            pools*.value*.verify()
        }
        
        return pools.size()
    }
    
    /**
     * Read the given userConfig and parse the "preallocate" section to find configurations
     * which should have preallocation pools created.
     * 
     * @param executorFactory
     * @param userConfig
     */
    @CompileStatic
    synchronized static void initPools(ExecutorFactory executorFactory, ConfigObject userConfig, boolean persistentOnly=false) {
        // Each prealloc section looks like
        //
        // small { // name of pool, by default the name of the configs it applies to
        //     jobs=10
        //     configs="bwa,gatk" // optional: now it will be limited to these configs, otherwise "small"
        // }
        
        // Create the pools
        ConfigObject prealloc = (ConfigObject) userConfig.get("preallocate")
        for(Map.Entry e in prealloc) {
            
            if(!(e.value instanceof ConfigObject))
                throw new PipelineError("An entry in your preallocate section had the wrong type: each entry should be a subconfiguration. You specified: " + e.value.class?.name)
            
            ConfigObject cfg = e.value
            
            if(!('configs' in cfg))
                cfg.configs = e.key
           
            if(!('name' in cfg))
                cfg.name = e.key
                
            assert !('name' in Config.userConfig)
            
            
            Map mergedCfg = Config.userConfig.clone().merge(cfg)
            
            // These are large entries in the root of the default config that are not 
            // wanted for executors (mainly they just pollute the logs)
            mergedCfg.remove("commands")
            mergedCfg.remove("tools")
            mergedCfg.remove("libs")
            
            if(persistentOnly && !(mergedCfg.get('persist')?:false)) {
                log.info "Not starting pool $cfg.name because it is not a persistent pool, and persistent mode required"
                continue
            }
            
            // Convert to plain map (so it is serializable)
            mergedCfg = mergedCfg.grep { Map.Entry me ->  me.value instanceof Serializable }.collectEntries()
            
            assert mergedCfg.name != null
            
            pools[(String)cfg.name] = new ExecutorPool(executorFactory, mergedCfg)
        }
    }
    
    /**
     * Shut down all the preallocation pools, if they are not persistent. If any jobs are still 
     * waiting pending commands then they will be terminated.
     */
    synchronized static void shutdownAll() {
        log.info "Shutting down ${pools.size()} preallocation pools"
        for(ExecutorPool pool in pools*.value) {
            try {
                if(!pool.persistent)
                    pool.shutdown()
            }
            catch(Exception e) {
                log.warning("Failed to shutdown preallocation pool: " + pool.cfg.name)
            }
        }
    }
    
    /**
     * If a pooled executor that is compatible with the given command is available,
     * returns a new command with the executor assigned. Otherwise returns the
     * original command without changing the executor.
     * 
     * @param command
     * @param cfg
     * @param outputLog
     * 
     * @return  A Command object that is either unchanged, or has an executor assigned.
     */
    synchronized static Command requestExecutor(Command command, Map cfg, OutputLog outputLog) {
        pools.each { name, pool -> 
            pool.configs.contains(cfg.name) 
        }?.value
        
        for(Map.Entry poolEntry in pools) {
            ExecutorPool pool = poolEntry.value
            if(!pool.configs.contains(cfg.name))
                continue
            
            if(pool.take(command, outputLog)) {
                log.info "Allocated command $command.id to pooled job $command.executor.hostCommandId, from pool $pool.cfg.name"
                break
            }
        }
        
        return command
    }
}
