/*
 * Copyright (c) 2011 MCRI, authors
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

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import bpipe.executor.CommandExecutor
import bpipe.executor.CommandUtilisation
import bpipe.executor.CustomCommandExecutor
import bpipe.executor.LocalCommandExecutor;
import bpipe.executor.ThrottledDelegatingCommandExecutor;
import bpipe.executor.UtilisationCapturingExecutor;
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Manages execution, persistence and stopping of commands executed
 * by {@link CommandExecutor} objects.  Uses information in 
 * the local configuration to decide which {@link CommandExecutor} should be 
 * used, creates the {@link CommandExecutor} and passes the execution options 
 * to it.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class CommandManager {

    /**
     * Default path under which currently running command info is stored
     */
    public static final String DEFAULT_COMMAND_DIR = ".bpipe/commands"
    
    /**
     * Default path under which completed command info is stored
     */
    public static final String DEFAULT_EXECUTED_DIR = ".bpipe/executed"
    
    /**
     * File where half processed files will be listed on shutdown
     */
    public static File UNCLEAN_FILE_DIR = new File(".bpipe/inprogress")
    
    /**
     * The location under which running command information will be stored
     */
    File commandDir
    
    /**
     * The location under which completed command information will be stored
     */
    File completedDir
    
    /**
     * Factory used to create executors
     */
    ExecutorFactory executorFactory = ExecutorFactory.instance
    
    /**
     * A global list of commands executed by all command managers in this run
     */
    static List<Command> executedCommands = Collections.synchronizedList([])
	
	/**
	 * Track the ids of commands that were launched by this command manager
	 */
	private Map<CommandExecutor,String> commandIds = [:]

    /**
     * Create a command manager
     */
    CommandManager(String commandPath=DEFAULT_COMMAND_DIR, String completedPath=DEFAULT_EXECUTED_DIR) {
         new File(commandPath).mkdirs()
         this.commandDir = new File(commandPath)
         new File(completedPath).mkdirs()
         this.completedDir = new File(completedPath)
    }

    /**
     * Start a job and return it using the command executor configured
     * for the command, or the default command executor if no executor is
     * configured.
     * 
     * @param name      a human readable name for the command
     * @param cmd       the command line to run
     * @param deferred  if true, the command will be started only when "waitFor" is called
     *                  on the resulting command executor
     * @return the {@link CommandExecutor} that is executing the job.
     */
    @CompileStatic
    Command start(String name, Command command, String configName, List<PipelineFile> inputs, Map resources, boolean deferred, OutputLog outputLog) {
        
        String cmd = command.command
        Map cfg = command.getConfig(inputs)
        
        if(command.id == null)
            command.id = CommandId.newId()
        else
            log.info "Starting command with existing id: $command.id"
                
        
        // Record this as the time the command is "created" (which is
        // really relevant to queuing systems - we wish to be able to see
        // later how much time the command waited in the queue)
        command.createTimeMs = System.currentTimeMillis()

        log.info "Using config " + Utils.sanitiseConfig(cfg) + " for command"
        
        OutputLog commandLog = new OutputLog(outputLog, command.id)

        command.dir = this.commandDir
        
        // Note: the command returned back may be a new command object,
        // it is important that we replace the original with it and return
        // the new one back to the caller.
        command = createExecutor(command, cfg, outputLog)
        
        CommandExecutor cmdExec = command.executor
        if(Runner.isPaused()) {
            throw new PipelinePausedException()
        }
        
        if(!(cmdExec instanceof LocalCommandExecutor)) {
            if(cfg && command.configName) {
                cfg.name = configName
            }
        }
        
        log.info "Created bpipe command id " + command.id
        
        ThrottledDelegatingCommandExecutor wrapped = new ThrottledDelegatingCommandExecutor(cmdExec, resources)
        if(deferred)
            wrapped.deferred = true
        
        command.name = name
        command.executor = wrapped
        command.outputLog = commandLog

        wrapped.start(cfg, command, commandLog, commandLog)
    		
		this.commandIds[cmdExec] = command.id
		this.commandIds[wrapped] = command.id
        this.executedCommands << command
        
        command.save()
        
        return command
    }
    
    /**
     * Attempt to create and assign an executor to the given command.
     * <p>
     * First, the pool of pre-allocated executors will be checked for
     * a compatible executor. If no compatible executor is available in 
     * the pre-allocated pool, a new executor will be created using the
     * executorFactory.
     * 
     * @param cmd   Command to execute
     * @param cfg   Configuration of command
     * 
     * @return  A Command object (which may be different to the original 
     *          command object supplied) which has an executor 
     *          assigned
     */
    @CompileStatic
    Command createExecutor(Command cmd, Map cfg, OutputLog outputLog) {
        
        cmd = ExecutorPool.requestExecutor(cmd, cfg, outputLog)
        if(cmd.executor) {
            log.info "Using pre-allocated executor of type ${cmd.executor.class.name}"
            return cmd
        }

        CommandExecutor cmdExec = executorFactory.createExecutor(cfg)
        cmd.executor = cmdExec
        return cmd
    }
   
    /**
     * Stop all known commands.
     * <p>
     * Looks in the file system for known commands and iterates over them all,
     * stopping each one.
     */
    int stopAll() {
        int parallelism = (int)Config.userConfig.getOrDefault('stopParallelism', 4)
        long staggerMs = (long)Config.userConfig.getOrDefault('stopStaggerMs', 100L)

        // First pass: deserialise all commands before starting any stops
        List<List> commandsToStop = []
        commandDir.eachFileMatch(~/[0-9]+/) { File f ->
            log.info "Loading command info from $f.absolutePath"
            CommandExecutor exec = null
            Command cmd = null
            try {
                f.withObjectInputStream {
                    exec = it.readObject()
                    log.info "Stopping command $exec"
                    try {
                        cmd = it.readObject()
                    }
                    catch(Exception e) {
                        log.info "Unable to read command details for $f.absolutePath : maybe legacy pipeline directory?"
                    }
                }

                if(exec != null) {
                    if(exec instanceof PooledExecutor && exec.poolConfig.get('persistent',false)) {
                        println "Command $cmd.id is persistent command: ignoring"
                    }
                    else {
                        commandsToStop << [f, exec, cmd]
                    }
                }
                else {
                    println "WARNING: stored command $f.absolutePath had null executor (internal error)"
                }
            }
            catch(Throwable t) {
                System.err.println("An unexpected error occured while loading command: $exec.\n\n${Utils.indent(t.message)}\n\nThe job may already be stopped; use 'bpipe cleancommands' to clear old commands.")
            }
        }

        // Second pass: stop commands in parallel, staggering submissions to avoid scheduler bursts
        List<Command> stoppedCommands = Collections.synchronizedList([])
        ExecutorService pool = Executors.newFixedThreadPool(parallelism)
        List<Future> futures = []

        commandsToStop.eachWithIndex { entry, int i ->
            if(i > 0 && staggerMs > 0)
                Thread.sleep(staggerMs)
            File f = entry[0]
            CommandExecutor exec = entry[1]
            Command cmd = entry[2]
            futures << pool.submit({
                try {
                    safeStopExecutor(cmd, exec)
                    if(cmd)
                        stoppedCommands << cmd
                    println "Successfully stopped command $cmd.id ($exec)"
                }
                catch(PipelineError e) {
                    System.err.println("WARNING: $cmd.id\n\n${Utils.indent(e.message)}\n\nNote: this may occur if the job is already stopped; use 'bpipe cleancommands' to clear old commands.")
                }
                catch(Throwable t) {
                    System.err.println("An unexpected error occured while stopping command: $exec.\n\n${Utils.indent(t.message)}\n\nThe job may already be stopped; use 'bpipe cleancommands' to clear old commands.")
                }
                finally {
                    try {
                        cleanup(f.name)
                    }
                    catch(Exception e) {
                        log.severe "Failed to clean up command object $f.name: " + e
                    }
                }
            } as Runnable)
        }

        pool.shutdown()
        futures*.get()

        log.info "Successfully stopped ${stoppedCommands.size()} commands"

        for(Command cmd in stoppedCommands) {
            try {
                Utils.cleanup(cmd.outputs)
            }
            catch(Exception e) {
                def msg = "Failed to cleanup one or more commands from $cmd.outputs: " + e.toString()
                log.info msg
                println "WARNING: $msg"
            }
        }

        return stoppedCommands.size()
    }

    private void safeStopExecutor(Command cmd, CommandExecutor exec) {
        Exception error
        
        if(exec.hasProperty('command'))
            exec.command = cmd

        try {
            exec.stop()
        }
        catch(Exception e) {
            error = e
        }
        finally {
            exec.cleanup()
        }
        
        if(error)
            throw error
    }
    
    public void cleanup(Command cmd) {
        
        CommandExecutor e = cmd.executor
        cmd.stopTimeMs = System.currentTimeMillis()
        
        // Ignore these as they do not need cleaning up and are sometimes created
        // spontaneously if commands are skipped (see PipelineContext#async)
        if(e instanceof bpipe.executor.ProbeCommandExecutor)
            return
            
        if(e instanceof bpipe.PooledExecutor)
            return
            
         if(e instanceof ThrottledDelegatingCommandExecutor)
            e = e.commandExecutor

        // Capture utilisation before cleanup, which may delete accounting data
        if(e instanceof UtilisationCapturingExecutor) {
            def utilisationConfig = Config.userConfig.utilisation
            if(utilisationConfig?.enabled != false) {
                try {
                    cmd.utilisation = ((UtilisationCapturingExecutor)e).captureUtilisation()
                    if(cmd.utilisation != null) {
                        log.info "Captured utilisation for command ${cmd.id}: cores=${cmd.utilisation.coresUsed}, rss=${cmd.utilisation.maxRssBytes}, state=${cmd.utilisation.state}"
                    }
                    else {
                        log.info "No utilisation data available for command ${cmd.id}"
                    }
                }
                catch(Exception ex) {
                    log.warning("Failed to capture utilisation for command ${cmd.id}: ${ex.message}")
                }
            }
        }

        e.cleanup()
            
		if(!commandIds.containsKey(e))
			throw new IllegalStateException("Attempt to clean up commmand $e that was not launched by this command manager / context")
			
		this.cleanup(this.commandIds[e])
	}
	
    /**
     * Move a command information file from the running location to completed location
     * 
     * @param commandId id of the command to move
     */
    public void cleanup(String commandId) {
        File from = new File(this.commandDir, commandId)
        
        // Update the command file with its latest details (runtime, etc)
        Command command = null
        synchronized(this.executedCommands) {
            command = this.executedCommands.find { it.id == commandId }
        }
        
        if(command) {
            command.dir = completedDir
            command.save()
        }
        else
            log.warning("Unable to locate command $commandId as an executed command")
            
        from.delete()
    }
    
    Command readSavedCommand(String commandId) {
        File commandFile = new File(completedDir, commandId)
        if(!commandFile.exists()) {
            log.info "No executed command file for $commandId exists: either it is still running or it never ran"
            return null
        }
        
        log.info "Loading command $commandId"
        commandFile.withObjectInputStream { 
            // First saved object is the CommandExecutor
            it.readObject(); 
            
            // Second is the one we want, the command itself
            it.readObject() 
        }
    }
    
    public List<Command> getCurrentCommands() {
        getCommandsByStatus([CommandStatus.RUNNING, CommandStatus.QUEUEING, CommandStatus.WAITING])
    }
    
    public List<Command> getCommandsByStatus(List<CommandStatus> statusEnums) {
        
        List<Command> result = []
        if(!commandDir.exists()) {
            log.info "No commands directory exists"
        }
        
        File executedDir = new File(DEFAULT_EXECUTED_DIR)
        
        List<String> statuses = statusEnums == null ? null : statusEnums*.name()
        [executedDir, commandDir].grep {it.exists()}*.eachFileMatch(~/[0-9]+/) { File f ->
            log.info "Loading command info from $f.absolutePath"
            CommandExecutor cmdExec
            Command cmd
            try {
                f.withObjectInputStream { 
                    cmdExec = it.readObject()
                    String status
                    try {
                        status = cmdExec.status()
                    }
                    catch(Exception e) {
                        println "Status probe for command $cmdExec failed: $e"
                    }
                    
                    try {
                        cmd = it.readObject()
                    }
                    catch(Exception e) {
                        System.err.println "WARN: unable to read saved command $f: " + e.message
                        return
                    }
                    
                    // Update the status
                    if(statuses == null || status in statuses) {
                        cmd.status = CommandStatus.valueOf(status)
                        result.add(cmd) 
                    }
                    else
                        log.info "Skip command with status $status"
                }
            }
            catch(PipelineError e) {
              System.err.println("Failed to read details for command $f.name: $cmdExec.\n\n${Utils.indent(e.message)}")
            }
            catch(InvalidClassException ice) {
              log.severe("Error reading saved command state: old version of bpipe? $ice")
              System.err.println("Error reading saved command state: old version of bpipe? $ice")
            }
            catch(Throwable t) {
              System.err.println("An unexpected error occured while reading details for command $f.name : $cmdExec.\n\n${Utils.indent(t.message)}")
              t.printStackTrace()
            }
        }
        return result
    }
}
