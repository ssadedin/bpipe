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

import groovy.util.logging.Log
import bpipe.Command;
import bpipe.OutputLog;
import bpipe.Utils
import bpipe.CommandStatus;
import bpipe.CommandManager;

/**
 * Implementation of a command executor that executes 
 * commands through a local Bash shell.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class LocalCommandExecutor implements CommandExecutor {
    
    public static final long serialVersionUID = 0L
    
    transient Process process
    
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
    
    LocalCommandExecutor() {
    }
    
    void start(Map cfg, Command command, File outputDirectory, Appendable outputLog, Appendable errorLog) {
        
      String cmd = command.command
      new Thread({
          
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
          
          List commandLines = []
          
          // Write out the pid to file
          commandLines << "echo \$\$ > ${CommandManager.DEFAULT_COMMAND_DIR}/${command.id}.pid;"
          
          if(cfg.use) 
              commandLines << "use $cfg.use;"
          
          if(cfg.modules)
              commandLines << "module load $cfg.modules"
          
          commandLines << cmd
          
          this.runningCommand = cmd
          this.startedAt = new Date()
          process = Runtime.getRuntime().exec((String[])(['bash','-e','-c',commandLines.join('\n')].toArray()))
          command.status = CommandStatus.RUNNING.name()
          command.startTimeMs = System.currentTimeMillis()
          this.id = command.id.toInteger()
          command.save()

          Thread t1 = process.consumeProcessOutputStream(outputLog);
          Thread t2 = process.consumeProcessErrorStream(errorLog)
          exitValue = process.waitFor()

          // Make sure to wait until the output streams are actually closed
          try { t1.join(); } catch(Exception e) {}
          try { t2.join(); } catch(Exception e) {}

          // Once we know the streams are closed, THEN destroy the process
          // This guarantees that file handles are cleaned up, even if
          // other things above went horribly wrong
          try { this.process.destroy() } catch(Throwable t) {}

          command.stopTimeMs = System.currentTimeMillis()
          
          synchronized(this) {
              this.notifyAll()
          }
      }).start()
      while(!process) 
        Thread.sleep(100)
    }
   
    String status() {
        String result = statusImpl()
        return result  
    }
    
    String statusImpl() {
        
        // Try to read PID
        if(pid == -1L && id != null) {
            File pidFile = new File("${CommandManager.DEFAULT_COMMAND_DIR}/${id}.pid")
            if(pidFile.exists()) {
                pid = pidFile.text.trim().toInteger()
            }
        }
        
        if(!this.process && this.pid != -1L) {
            try {
                String info = "ps -o ppid,ruser -p ${this.pid}".execute().text
                def lines = info.split("\n")*.trim()
                if(lines.size()>1)  {
                    info = lines[1].split(" ")[1]; 
                    if(info == System.properties["user.name"]) {
                        return CommandStatus.RUNNING.name()
                    }
                }
            }
            catch(Exception e) {
                
            }
            return CommandStatus.UNKNOWN
        }
        else
        if(exitValue != null)
            return CommandStatus.COMPLETE
        else
            return CommandStatus.RUNNING
    }
    
    /**
     * Wait for this job and return exit code 0 for success
     * or non-zero for an error.
     * @return
     */
    int waitFor() {
        while(true) {
            if(status() == CommandStatus.COMPLETE.name())
                return exitValue
             synchronized(this) {
                 this.wait(500)
             }
        }
    }
    
    List<String> getIgnorableOutputs() {
        return null;
    }
    
    void stop() {
        // Not implemented.  Java is too stupid to stop a process it previously started.
        // So how do commands get stopped then? Well, the 'bpipe stop' first calls the 
        // Java way (necessary for other executors), then it scans the processes
        // that are a child of the Bpipe process and kills them too. This means
        // if the Bpipe process dies another way, it's possible that we can't stop
        // the children any more (a bug)
    }
    
    void cleanup() {
    }
    
    String statusMessage() {
        "$runningCommand, running since $startedAt (local command, pid=$pid)"
    }
}
