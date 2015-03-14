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

import groovy.util.logging.Log
import bpipe.executor.CommandExecutor
import bpipe.executor.CustomCommandExecutor
import bpipe.executor.LocalCommandExecutor;
import bpipe.executor.ThrottledDelegatingCommandExecutor;

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
     * The location under which running command information will be stored
     */
    File commandDir
    
    /**
     * The location under which completed command information will be stored
     */
    File completedDir
    
    
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
    CommandExecutor start(String name, Command command, String configName, Collection inputs, File outputDirectory, Map resources, boolean deferred, Appendable outputLog) {
        
        String cmd = command.command
        Map cfg = command.getConfig(inputs)
        
        // Record this as the time the command is "created" (which is
        // really relevant to queuing systems - we wish to be able to see
        // later how much time the command waited in the queue)
        command.createTimeMs = System.currentTimeMillis()

        String executor = cfg.executor
        
        log.info "Using config $cfg for command"
        
        // Try and resolve the executor several ways
        // It can be a file on the file system,
        // or it can map to a class
        CommandExecutor cmdExec = null
        File executorFile = new File(executor)
        String name1 = "bpipe.executor."+executor.capitalize()
        if(executorFile.exists()) {
            cmdExec = new CustomCommandExecutor(executorFile)
        }
        else {
            /* find out the command executor class */
            Class cmdClazz = null
            try {
                cmdClazz = Class.forName(name1)
            }
            catch(ClassNotFoundException e) {
                String name2 = "bpipe.executor."+executor.capitalize() + "CommandExecutor"
                try {
                    cmdClazz = Class.forName(name2)
                }
                catch(ClassNotFoundException e2) {
                    log.info("Unable to create command executor using class $name2 : $e2")
                    String name3 = executor
                    try {
                        cmdClazz = Class.forName(name3)
                    }
                    catch(ClassNotFoundException e3) {
                        throw new PipelineError("Could not resolve specified command executor ${executor} as a valid file path or a class named any of $name1, $name2, $name3")
                    }
                }
            }

            /* let's instantiate the found command executor class */
            try {
                cmdExec = cmdClazz.newInstance()
            }
            catch( PipelineError e ) {
                // just re-trow
                throw e
            }
            catch( Exception e ) {
                throw new PipelineError( "Cannot instantiate command executor: ${executor}", e )
            }
        }

        
        if(Runner.opts.t || Config.config.breakTriggered) {
            
          String msg = command.branch.name ? "Branch $command.branch.name would execute: $cmd" : "Would execute $cmd"
          if(cmdExec instanceof LocalCommandExecutor)
              throw new PipelineTestAbort(msg)
          else {
              if(cfg && command.configName) {
                  cfg.name = configName
              }
              throw new PipelineTestAbort("$msg\n\n                using $cmdExec with config $cfg")
          }
        }
        
        // Create a command id for the job
        command.id = CommandId.newId()
        
        log.info "Created bpipe command id " + command.id
        
        // Temporary hack until we figure out design for how output log gets passed through
        OutputLog commandLog = new OutputLog(outputLog, command.id)
        if(cmdExec instanceof LocalCommandExecutor) {
        	cmdExec.outputLog = commandLog
        	cmdExec.errorLog = commandLog
        }

        ThrottledDelegatingCommandExecutor wrapped = new ThrottledDelegatingCommandExecutor(cmdExec, resources)
        if(deferred)
            wrapped.deferred = true
        
        command.name = name
        wrapped.start(cfg, command, outputDirectory)
    		
		this.commandIds[cmdExec] = command.id
		this.commandIds[wrapped] = command.id
        this.executedCommands << command
            
        new File(commandDir, command.id).withObjectOutputStream { it << cmdExec }
        
        return wrapped
    }
    
    /**
     * Stop all known commands.
     * <p>
     * Looks in the file system for known commands and iterates over them all,
     * stopping each one.
     */
    int stopAll() { 
        int count = 0
        commandDir.eachFileMatch(~/[0-9]+/) { File f ->
            log.info "Loading command info from $f.absolutePath"
            CommandExecutor cmd
            log.info "Stopping command $cmd"
            try {
                f.withObjectInputStream { cmd = it.readObject() }
                cmd.stop() 
                cleanup(f.name)
                log.info "Successfully stopped command $cmd"
            }
            catch(PipelineError e) {
              System.err.println("Failed to stop command: $cmd.\n\n${Utils.indent(e.message)}\n\nThe job may already be stopped; use 'bpipe cleancommands' to clear old commands.")      
            }
            catch(Throwable t) {
              System.err.println("An unexpected error occured while stopping command: $cmd.\n\n${Utils.indent(t.message)}\n\nThe job may already be stopped; use 'bpipe cleancommands' to clear old commands.")      
            }            
            ++count
        }
        log.info "Successfully stopped $count commands"
        return count
    }
    
    public void cleanup(Command cmd) {
        
        CommandExecutor e = cmd.executor
        cmd.stopTimeMs = System.currentTimeMillis()
        
        // Ignore these as they do not need cleaning up and are sometimes created
        // spontaneously if commands are skipped (see PipelineContext#async)
        if(e instanceof bpipe.executor.ProbeCommandExecutor)
            return
            
        if(e instanceof ThrottledDelegatingCommandExecutor)
            e = e.commandExecutor
        
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
        if(!new File(this.commandDir, commandId).renameTo(new File(this.completedDir, commandId)))
            log.warning("Unable to cleanup persisted file for command $commandId")
    }
    
    public static List<CommandExecutor> getCurrentCommands() {
        
        List<CommandExecutor> result = []
        
        File commandsDir = new File(DEFAULT_COMMAND_DIR)
        if(!commandsDir.exists()) {
            log.info "No commands directory exists: empty status results"
            return result
        }
        
        List<String> statuses = [CommandStatus.RUNNING, CommandStatus.QUEUEING, CommandStatus.WAITING]*.name()
        commandsDir.eachFileMatch(~/[0-9]+/) { File f ->
            log.info "Loading command info from $f.absolutePath"
            CommandExecutor cmd
            try {
                f.withObjectInputStream { 
                    log.info "Loading command $cmd"
                    cmd = it.readObject()
                    String status
                    try {
                        status = cmd.status()
                    }
                    catch(Exception e) {
                        log.info "Status probe for command $cmd failed: $e"
                    }
                    if(status in statuses)
                        result.add(cmd) 
                    else
                        log.info "Skip command with status $status"
                }
            }
            catch(PipelineError e) {
              System.err.println("Failed to read details for command $f.name: $cmd.\n\n${Utils.indent(e.message)}")
            }
            catch(Throwable t) {
              System.err.println("An unexpected error occured while reading details for command $f.name : $cmd.\n\n${Utils.indent(t.message)}")
            }
        }
        return result
    }
}
