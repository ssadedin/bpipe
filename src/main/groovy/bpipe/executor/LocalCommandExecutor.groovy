/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Log

import java.beans.PropertyChangeEvent
import java.lang.ProcessBuilder.Redirect;

import bpipe.Command;
import bpipe.OutputLog;
import bpipe.Utils
import bpipe.storage.StorageLayer
import bpipe.CommandStatus
import bpipe.ExecutedProcess
import bpipe.ForwardHost;
import bpipe.CommandManager;

/**
 * Implementation of a command executor that executes 
 * commands through a local Bash shell.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@Mixin(ForwardHost)
class LocalCommandExecutor implements CommandExecutor {
    
    public static final long serialVersionUID = 0L
    
    transient Process process
    
    transient Command command
    
    private transient Object lock = new Object()
    
    /**
     * The exit code returned by the process, only
     * available after the process has exited and
     * status() or waitFor() has been called.
     */
    Integer exitValue = null
    
    Integer pid = -1L
    
    Integer id
    
    String runningCommand
    
    Date startedAt = null
    
    /**
     * Set to true if the process is terminated forcibly
     */
    boolean destroyed = false
    
    String jobDir
    
    LocalCommandExecutor() {
    }
    
    /**
     * Starts a command using a process on the local machine.
     * <p>
     * If command.command is null, the command is started in "waiting" mode and
     * will only run when "activated" by .
     */
    void start(Map cfg, Command command, Appendable outputLog, Appendable errorLog) {
        
      String cmd = command.command
      
      
      Closure executeClosure = {
          
          // Special case for Windows / Cygwin
          // On Windows Java detects spaces in arguments and if it finds them
          // wraps the whole argument in double quotes.  However it doesn't 
          // escape quotes embedded in the argument body, so it actually 
          // creates invalid arguments
          if(Utils.isWindows()) {
              // See java.lang.ProcessImpl for this test. It is not really correct
              // but it is important for it to be the same as what is in the Java src
              String origCmd = cmd
              if(cmd.indexOf(' ') >=0 || cmd.indexOf('\t') >=0) {
                  cmd = cmd.replaceAll(/"/, /\\"/)
              }
              log.info "Converted $origCmd to $cmd to account for broken Java argument escaping"
          }
          
          // Create the job directory
          this.id = command.id.toInteger()
          this.jobDir = ".bpipe/commandtmp/$id"
          new File(jobDir).mkdirs()
          
          File cmdScript = new File(jobDir, CMD_FILENAME)
          if(cmd != null) {
              log.info "No preallocated command executor: running command $id directly"
              cmdScript.text = cmd
          }
          else {
              log.info "Local command executor is preallocated: will wait for command"
              command.commandListener =  { Command e ->
                  log.info "Local pooled command executor now executing: " + e.command
              }
          }
          
          Map props = cfg + [
            config : cfg,
            cmd : cmd,
            name : command.name,
            jobDir : jobDir,
            CMD_FILENAME : cmdScript.path,
            CMD_PID_FILE : "${CommandManager.DEFAULT_COMMAND_DIR}/${command.id}.pid",
            CMD_OUT_FILENAME : CMD_OUT_FILENAME,
            CMD_ERR_FILENAME : CMD_ERR_FILENAME,
            CMD_EXIT_FILENAME : CMD_EXIT_FILENAME,
            SETSID : Utils.isLinux() ? "setsid" : ""
          ]

          File wrapperScript = 
              new CommandTemplate().populateCommandTemplate(new File(jobDir), "executor/local-command.template.sh", props)
          
          this.runningCommand = cmd
          this.startedAt = new Date()
          
          log.info "Launching command wrapper script ${wrapperScript.path}"
          
          List<String> shell = command.shell ?: ['bash']
          
          List<String> args = ['nohup'] + shell  + [wrapperScript.path]
          
          ProcessBuilder pb = new ProcessBuilder(args*.toString())
          pb.redirectOutput(Redirect.to(new File(jobDir, CMD_OUT_FILENAME)))
          pb.redirectError(Redirect.to(new File(jobDir, CMD_ERR_FILENAME)))
          
          forward("$jobDir/$CMD_OUT_FILENAME", outputLog)
          forward("$jobDir/$CMD_ERR_FILENAME", errorLog)
          
          process = pb.start()
          
          command.status = CommandStatus.RUNNING.name()
          command.startTimeMs = System.currentTimeMillis()
          command.save()
          
          exitValue = process.waitFor()

          // Once we know the streams are closed, THEN destroy the process
          // This guarantees that file handles are cleaned up, even if
          // other things above went horribly wrong
          try { this.process.destroy() } catch(Throwable t) {}

          command.stopTimeMs = System.currentTimeMillis()
          
          synchronized(lock) {
              lock.notifyAll()
          }
      }
      
      Thread runnerThread = new Thread(executeClosure)
      runnerThread.daemon = true
      runnerThread.start()
      
      while(!process) 
        Thread.sleep(100)
    }
    
    transient String lastStatus = CommandStatus.UNKNOWN.name()
   
    String status() {
        String result = statusImpl()
        if(command && (result != lastStatus)) {
            if(result == CommandStatus.RUNNING.name()) {
                this.command.startTimeMs = System.currentTimeMillis()
                this.command.save()
            }
            lastStatus = result
        }
        return result  
    }
    
    String statusImpl() {
        
        // Try to read PID
        readPID()
       
        if(!this.process && this.pid != -1L) {
            return getStatusOfSavedProcess()
        }
        else
        if(this.process != null) {
            if(this.process.isAlive()) {
                return CommandStatus.RUNNING
            }
            else {
                if(this.pid != -1L)
                    this.exitValue = readStoredExitCode()
                
                log.info "Command " + this.toString() + " returning complete status after reading exit code $exitValue"
                return CommandStatus.COMPLETE
            }
        }
        else
        if(exitValue != null) {
            return CommandStatus.COMPLETE
        }
        else
            return CommandStatus.RUNNING
    }

    /**
     * Read the status of the process where we do not have the live process
     * avaialble to query its status
     * 
     * @return
     */
    @CompileStatic
    private CommandStatus getStatusOfSavedProcess() {
        try {

            String info = "ps -o ppid,ruser -p ${this.pid}".execute().text

            def lines = info.split("\n")*.trim()
            if(lines.size()>1)  {
                info = lines[1].split(" ")[1];
                if(info == System.properties["user.name"]) {
                    return CommandStatus.RUNNING.name()
                }
            }

            // Not found? look at exit code if we can
            Integer storedExitCode = readStoredExitCode()
            if(storedExitCode != null) {
                exitValue = storedExitCode
                return CommandStatus.COMPLETE
            }
        }
        catch(Exception e) {
            log.info "Exception occurred while probing status of command $id: $e"
        }

        return CommandStatus.UNKNOWN
    }
    
    /**
     * Wait for this job and return exit code 0 for success
     * or non-zero for an error.
     * @return
     */
    int waitFor() {
        
        if(this.pid != -1L) {
            Integer exitCode =  readStoredExitCode()
            log.info "Read stored exit code for $pid: $exitCode"
            if(exitCode != null) {
                this.exitValue = exitCode
                return exitValue
            }
        }
        
        while(true) {
            if(status() == CommandStatus.COMPLETE.name()) {
                
                if(this.exitValue == null) {
                    Integer exitCode =  readStoredExitCode()
                    if(exitCode != null)
                        exitValue = exitCode
                }
                
                return exitValue == null ? -1L : exitValue
            }
             synchronized(lock) {
                 lock.wait(500)
             }
        }
    }
    
    /**
     * Attempts to read PID for the launched command, taking into account it may
     * be in dynamic state (not started, starting, started, finished)
     */
    void readPID() {
        
        //  If we already know the PID, don't read it again
        if(pid != -1L) 
            return
            
        // If we do not have an id, don't attempt to read PID
        if(id == null)
            return
            
        File pidFile = new File("${CommandManager.DEFAULT_COMMAND_DIR}/${id}.pid")
        log.info "Checking for pid file $pidFile for local command $id"
        if(pidFile.exists()) {

            String pidText = pidFile.text
            if(pidText.trim().isEmpty()) {
                log.info "PID file for command $id is empty; presume race condition on read - wait for contents"
            }
            else {
                pid = pidText.trim().toInteger()
                log.info "Read pid=$pid for local command $id"
            }
        }
    }
    
    @CompileStatic
    Integer readStoredExitCode() {
        File exitFile = new File(".bpipe/commandtmp/$id/$CMD_EXIT_FILENAME")
        if(exitFile.exists()) {
            log.info "Exit file $exitFile exists: reading exit code"
            return exitFile.text.trim().toInteger()
        }
        else {
            log.info "Exit file $exitFile does not exist yet"
            return null
        }
    }
    
    List<String> getIgnorableOutputs() {
        return null;
    }
    
    void stop() {
        
        // There are scenarios where we could try to stop a command before it got to read
        // its own PID (note PID is only read lazily above as part of status call)
        readPID()
        
        if(pid < 0) {
            log.info "Not stopping job because PID is unknown ($pid)"
            return
        }
        
        log.info "Shutting down local job $id with pid $pid"
        
        List killCmd = ["bash","-c","source ${bpipe.Runner.BPIPE_HOME}/bin/bpipe-utils.sh; killtree $pid"]
        ExecutedProcess killResult = Utils.executeCommand(killCmd)
        if(killResult.exitValue != 0) {
            throw new RuntimeException("Attempt to kill process returned exit code " + 
                "$killResult.exitValue using command:\n\n$killCmd\n\nOutput:\n\n$killResult.out\n\n$killResult.err")
        }
        
        // Store an exit code so that future status calls will know that the command 
        // exited via a normal process and not abruptly killed
        File exitFile = new File(".bpipe/commandtmp/$id/$CMD_EXIT_FILENAME")
        exitFile.text = "-1"
    }
    
    @Override
    void cleanup() {
        
        log.info "Cleanup command $id"
        
        this.stopForwarding()
    }
    
    String statusMessage() {
        "$runningCommand, running since $startedAt (local command, pid=$pid)"
    }
    
    Map mountedStorage = [:]

    @Override
    public String localPath(String storageName) {
        if(mountedStorage.containsKey(storageName))
            return mountedStorage[storageName]
        
        StorageLayer storage = StorageLayer.create(storageName)
        
        // TODO: execute the command!
        storage.getMountCommand(this)
        
        mountedStorage[storageName] 
    }

    @Override
    public void mountStorage(StorageLayer storage) {
        // noop - for now!
    }
    
    @Override
    String toString() {
        "LocalCommandExecutor(job=$id, pid=$pid, started=$startedAt, exitValue=$exitValue)"
    }
}
