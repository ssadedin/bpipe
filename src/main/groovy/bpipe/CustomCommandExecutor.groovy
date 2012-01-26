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
    
   public static final long serialVersionUID = 0L
    
   /**
    * Logger for this class to use
    */
   private static Logger log = Logger.getLogger("bpipe.CustomCommandExecutor");
   
   /**
    * We don't rely on commands to be 100% reliable, so we allow for retries,
    * however the number of retries is limited to this value.
    */
   private static int MAX_STATUS_ERROR = 4
    
    /**
     * The path to the script that is used to manage the custom command
     */
    String managementScript
    
    /**
     * The id assigned to this command
     */
    String commandId
    
    
    /**
     * The job folder used for storing intermediate files 
     */
    String jobDir
    
    /**
     * Name of this job - this is overridden with the stage name
     * at runtime
     */
    String name = "BpipeJob"    
    
    /**
     * The configuration of the custom command.
     * Set after construction. 
     */
    Map config
    
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
        
        if(System.properties['file.separator']=='\\') {
            this.managementScript = managementScript.absolutePath.replaceAll('\\\\', "/");
        }
        else
            this.managementScript = managementScript.absolutePath;
            
        if(!managementScript.exists()) 
            throw new IllegalArgumentException("Unable to locate specified script for custom command ${this.class.name} "
		                     + managementScript.toString())
    }
    
    /**
     * Start the specified command by marshalling the correct information into environment variables and then
     * launching the specified command.
     */
    @Override
    public void start(Map cfg, String id, String name, String cmd) {
        
		this.config = cfg
        this.name = name
        
        log.info "Executing command using custom command runner ${managementScript}:  ${Utils.truncnl(cmd,100)}"
        ProcessBuilder pb = new ProcessBuilder("bash", managementScript, "start")
        Map env = pb.environment()
        
        // Environment variables that can be used to transmit 
        // essential information
        env.NAME = name
        
        this.jobDir = ".bpipe/commandtmp/$id"
		File jobDirFile = new File(this.jobDir)
        if(!jobDirFile.exists())
		    jobDirFile.mkdirs()
        env.JOBDIR = jobDirFile.absolutePath
        
        env.COMMAND = '('+ cmd + ') > .bpipe/commandtmp/'+id+'/'+id+'.out 2>  .bpipe/commandtmp/'+id+'/'+id+'.err'
        
        // If an account is specified by the config then use that
        log.info "Using account: $config?.account"
        if(config?.account)
            env.ACCOUNT = config.account
        
        if(config?.walltime) 
            env.WALLTIME = config.walltime
       
        String startCmd = pb.command().join(' ')
        log.info "Starting command: " + startCmd
        Process p = pb.start()
        StringBuilder out = new StringBuilder()
        StringBuilder err = new StringBuilder()
        p.waitForProcessOutput(out, err)
        int exitValue = p.waitFor()
        if(exitValue != 0) {
            reportStartError(startCmd, out,err,exitValue)
            throw new PipelineError("Failed to start command:\n\n$cmd")
        }
        this.commandId = out.toString().trim()
        if(this.commandId.isEmpty())
            throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$startCmd\n\nRaw output was:[" + out.toString() + "]")
            
        log.info "Started command with id $commandId"
    }

    /**
     * For custom commands status is returned by calling the shell script with the
     * 'status' argument and the stored command id.
     */
    @Override
    public String status() {
        String cmd = "bash $managementScript status ${commandId}"
        Process p = Runtime.runtime.exec(cmd)
        StringBuilder out = new StringBuilder()
        StringBuilder err = new StringBuilder()
        p.waitForProcessOutput(out, err)
        int exitValue = p.waitFor() 
        if(exitValue != 0)
            return CommandStatus.UNKNOWN.name()
        String result = out.toString()
        return result.split()[0]
    }

    @Override
    public int waitFor() {
        String cmd = "bash " + managementScript + " status ${commandId}"
        
        // Don't rely on the queueing software to be completely reliable; a single
        // failure to check shouldn't cause us to abort, so count errors 
        int errorCount = 0
        while(true) {
            
            log.info "Polling status of job $commandId with command $cmd"
            
            Process p = Runtime.runtime.exec(cmd)
            StringBuilder out = new StringBuilder()
            StringBuilder err = new StringBuilder()
            p.waitForProcessOutput(out, err)
            int exitValue = p.waitFor()
            if(exitValue > 0)  {
                String msg = "Attempt to poll status for job $commandId return status $exitValue using command $cmd:\n\n  $out"
                if(errorCount > MAX_STATUS_ERROR) 
                    throw new PipelineError(msg)
                log.warning(msg + "(retrying)")
                errorCount++
                Thread.sleep(100)
                continue
            }
            else 
                errorCount = 0
            
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
    
    List<String> getIgnorableOutputs() {
        return null;
    }
    
    void reportStartError(String cmd, def out, def err, int exitValue) {
        log.severe "Error starting custom command using command line: " + cmd
        System.err << "\nFailed to execute command using command line: $cmd\n\nReturned exit value $exitValue\n\nOutput:\n\n$out\n\n$err"
    }
    
    /**
     * Call the service script to stop the job
     */
    void stop() {
        String cmd = "bash $managementScript stop ${commandId}"
        log.info "Executing command to stop command $commandId: $cmd"
        int errorCount = 0
        while(true) {
            StringBuilder err = new StringBuilder()
            StringBuilder out = new StringBuilder()
            Process p = Runtime.runtime.exec(cmd)
            p.waitForProcessOutput(out,err)
            int exitValue = p.waitFor()
            
            // Hack - ignore failures that are due to the job
            // being unknown or completed.  These error messages are
            // specific to Torque, and this check should perhaps be in the torque script?
            if(exitValue != 0 && err.toString().indexOf("Unknown Job Id")<0 && err.toString().indexOf("invalid state for job - COMPLETE")<0) {
                def msg = "Management script $managementScript failed to stop command $commandId, returned exit code $exitValue from command line: $cmd"
                if(errorCount > MAX_STATUS_ERROR) {
                    
                    log.severe "Failed stop command produced output: \n$out\n$err"
                    if(!err.toString().trim().isEmpty()) 
                        msg += "\n" + Utils.indent(err.toString())
                    throw new PipelineError(msg)
                }
                else {
                    log.warning(msg + "(retrying)")
                    Thread.sleep(100)
                    errorCount++
                    continue
                }
            }
            
            // Successful stop command
            log.info "Successfully called script to stop command $commandId"
            break
        }
    }
    
    String toString() {
        "Command Id: $commandId " + (config?"Configuration: $config":"")
    }
}
