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

import java.util.logging.Logger
import bpipe.executor.CommandExecutor
import bpipe.executor.CustomCommandExecutor
import bpipe.executor.LocalCommandExecutor;

/**
 * Manages execution, persistence and stopping of commands executed
 * by {@link CommandExecutor} objects.  Uses information in 
 * the local configuration to decide which {@link CommandExecutor} should be 
 * used, creates the {@link CommandExecutor} and passes the execution options 
 * to it.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
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
     * Logger for this class to use
     */
    private static Logger log = Logger.getLogger("bpipe.CommandManager");
    
    /**
     * The location under which running command information will be stored
     */
    File commandDir
    
    /**
     * The location under which completed command information will be stored
     */
    File completedDir
	
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
     * @param name    a human readable name for the command
     * @param cmd     the command line to run
     * @return the {@link CommandExecutor} that is executing the job.
     */
    CommandExecutor start(String name, String cmd, String configName, Collection inputs) {
        
        // How to run the job?  look in user config
		if(!configName) 
            configName = cmd.trim().split(/\s/)[0].trim()
        
        log.info "Checking for configuration for command $configName"
        
        // Use default properties from root entries into user config
        def defaultConfig = Config.userConfig.findAll { !(it.value instanceof Map) }
        log.info "Default command properties: $defaultConfig"
        
        def rawCfg = defaultConfig
		
        def cmdConfig = Config.userConfig.containsKey("commands")?Config.userConfig.commands[configName]:null
        if(cmdConfig && cmdConfig instanceof Map)  {
            // override properties in default config with those for the
            // specific command
            rawCfg = defaultConfig + cmdConfig
        }
        
        // Make a new map
        def cfg = rawCfg.clone()
        
        // Resolve inputs to files
        List fileInputs = inputs.collect { new File(it) }
        
        // Execute any executable properties that are closures
        rawCfg.each { key, value ->
            if(value instanceof Closure) {
                cfg[key] = value(fileInputs)                
            }
            
            // Special case - walltime can be specified as integer number of seconds
            if(key == 'walltime' && !(cfg[key] instanceof String)) {
                cfg[key] = formatWalltime(cfg[key])
                log.info "Converted walltime is " + cfg[key]
            }
        }

        String executor = cfg.executor
        
        log.info "Using config $rawCfg for command"
        
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

        
        if(Runner.opts.t) {
            if(cmdExec instanceof LocalCommandExecutor)
              throw new PipelineTestAbort("Would execute: $cmd")
          else
              throw new PipelineTestAbort("Would execute: $cmd\n\n                using $cmdExec with config $cfg")
        }

        // Create a command id for the job
        String commandId = CommandId.newId()
        log.info "Created bpipe command id " + commandId
        
        cmdExec.start(cfg, commandId, name, cmd)
		
		this.commandIds[cmdExec] = commandId
        
        new File(commandDir, commandId).withObjectOutputStream { it << cmdExec }
        
        return cmdExec
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
            f.withObjectInputStream { cmd = it.readObject() }
            log.info "Stopping command $cmd"
            try {
                cmd.stop() 
                cleanup(f.name)
                log.info "Successfully stopped command $cmd"
            }
            catch(PipelineError e) {
              System.err.println("Failed to stop command: $cmd.\n\n${Utils.indent(e.message)}\n\nThe job may already be stopped; use 'bpipe cleancommands' to clear old commands.")      
            }
            ++count
        }
        log.info "Successfully stopped $count commands"
        return count
    }
    
    public void cleanup(CommandExecutor cmd) {
        
        // Ignore these as they do not need cleaning up and are sometimes created
        // spontaneously if commands are skipped (see PipelineContext#async)
        if(cmd instanceof ProbeCommandExecutor)
            return
        
		if(!commandIds.containsKey(cmd))
			throw new IllegalStateException("Attempt to clean up commmand $cmd that was not launched by this command manager / context")
			
		this.cleanup(this.commandIds[cmd])
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
    
    private String formatWalltime(def walltime) {
       // Treat as integer, convert to string
       int hours = (int)Math.floor(walltime / 3600)
       int minutes = (int)Math.floor((walltime - hours*3600)/60)
       int seconds = walltime % 60
       return String.format('%02d:%02d:%02d', hours, minutes, seconds )
    }
}
