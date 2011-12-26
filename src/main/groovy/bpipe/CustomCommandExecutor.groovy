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

import java.io.File;
import java.util.logging.Logger;

/**
 * Custom command executors are implemented by a shell script that implements the
 * contract for the commands start, status and stop.  This 
 * implementation acts as a proxy to the shell script to
 * the rest of Bpipe internals so that it looks like a 
 * normal {@link CommandExecutor}.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class CustomCommandExecutor implements CommandExecutor {
    
   /**
    * Logger for this class to use
    */
   private static Logger log = Logger.getLogger("bpipe.CustomCommandExecutor");
    
    /**
     * The path to the script that is used to manage the custom command
     */
    File managementScript
    
    /**
     * The id assigned to this command
     */
    String commandId
    
    /**
     * Create a custom command with the specified script as its
     * management script.  The script must exist and be an 
     * executable file that conforms to the defined protocol for 
     * starting, stopping and retrieving status from job scripts.
     * 
     * @param managementScript
     */
    public CustomCommandExecutor(File managementScript) {
        super();
        this.managementScript = managementScript;
        if(!this.managementScript.exists()) 
            throw new IllegalArgumentException("Unable to locate specified script for custom command ${this.class.name} "
		                     + managementScript.toString())
    }

    @Override
    public void start(String cmd) {
        
        log.info "Executing command using custom command runner ${managementScript.absolutePath}:  ${Utils.truncnl(cmd,100)}"
        String startCmd = "bash -c ${managementScript.absolutePath} start" 
        
        ProcessBuilder pb = new ProcessBuilder(managementScript.absolutePath, "start")
        Map env = pb.environment()
        
        // Environment variables that can be used to transmit 
        // essential information
        env.COMMAND = startCmd
        
        // TODO:  support reporting the stage name in here?  or a user specified name?
        env.NAME = "BpipeJob"
        
        Process p = pb.start()
        StringBuilder out = new StringBuilder()
        StringBuilder err = new StringBuilder()
        p.consumeProcessOutput(out, err)
        int exitValue = p.waitFor()
        if(exitValue != 0) {
            reportStartError(startCmd, out,err,exitValue)
            throw new PipelineError("Failed to start command:\n\n$cmd")
        }
        this.commandId = out.toString().trim()
        if(this.commandId.isEmpty())
            throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$startCmd")
            
        log.info "Started command with id $commandId"
    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public int waitFor() {
        String cmd = managementScript.absolutePath + " status ${commandId}"
        while(true) {
            
            log.info "Polling status of job $commandId with command $cmd"
            
            Process p = Runtime.runtime.exec(cmd)
            StringBuilder out = new StringBuilder()
            p.consumeProcessOutput(out, out)
            
            int exitValue = p.waitFor()
            if(exitValue > 0) 
                throw new PipelineError("Failed to poll status for job $commandId using command $cmd:\n\n  $out")
            
            String result = out.toString()
            log.info "Poll returned output: $result"
            def parts = result.split()
            String status = parts[0]
            if(status == CommandStatus.COMPLETE.name()) {
	            if(parts.size() != 2)
	                throw new PipelineError("Unexpected format in output of job status command [$cmd]:  $result")
	            return Integer.parseInt(parts[1])
            }
            
            // Job not complete, keep waiting
            Thread.sleep(2000)
        }
    }
    
    void reportStartError(String cmd, def out, def err, int exitValue) {
        log.severe "Error starting custom command using command line: " + cmd
        System.err << "\nFailed to execute command using command line: $cmd\n\nReturned exit value $exitValue\n\nOutput:\n\n$out\n\n$err"
    }
}
