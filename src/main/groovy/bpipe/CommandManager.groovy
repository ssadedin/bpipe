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

import java.util.logging.Logger;

/**
 * Manages execution, persistence and stopping of commands executed
 * by {@link CommandExecutor} objects.
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
    CommandExecutor start(String name, String cmd) {
        // How to run the job?  look in user config
        String leadingToken = cmd.trim().split(/\s/)[0].trim()
        
        log.info "Checking for configuration for command $leadingToken"
        
        def cfg = Config.userConfig
        def cmdConfig = Config.userConfig?.commands[leadingToken]
        if(cmdConfig && cmdConfig.executor)  {
            cfg = cmdConfig
        }

        String executor = cfg.executor
        
        log.info "Using config $cfg for command"
        
        // Try and resolve the executor several ways
        // It can be a file on the file system,
        // or it can map to a class
        CommandExecutor cmdExec = null
        File executorFile = new File(executor)
        String name1 = "bpipe."+executor.capitalize()
        if(executorFile.exists()) {
            cmdExec = new CustomCommandExecutor(executorFile)
        }
        else
        try {
            cmdExec = Class.forName(name1).newInstance()
        }
        catch(Exception e) {
            String name2 = "bpipe."+executor.capitalize() + "CommandExecutor"
            try {
                cmdExec = Class.forName(name2).newInstance()
            }
            catch(Exception e2) {
                log.info("Unable to create command executor using class $name2 : $e2")
                String name3 = executor
                try {
                    cmdExec = Class.forName(name3).newInstance()
                }
                catch(Exception e3) {
                    throw new PipelineError("Could not resolve specified command executor ${executor} as a valid file path or a class named any of $name1, $name2, $name3")
                }
            }
        }
        
        cmdExec.setConfig(cfg)
        
        if(Runner.opts.t) {
            if(cmdExec instanceof LocalCommandExecutor)
              throw new PipelineTestAbort("Would execute: $cmd")
          else
              throw new PipelineTestAbort("Would execute: $cmd using $cmdExec")
        }

        // Create a command id for the job
        String commandId = CommandId.newId()
        log.info "Created bpipe command id " + commandId
        
        cmdExec.start(name, cmd)
        
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
    
    /**
     * Move a command information file from the running location to completed location
     * 
     * @param commandId id of the command to move
     */
    private void cleanup(String commandId) {
        if(!new File(this.commandDir, commandId).renameTo(new File(this.completedDir, commandId)))
            log.warning("Unable to cleanup persisted file for command $commandId")
    }
}
