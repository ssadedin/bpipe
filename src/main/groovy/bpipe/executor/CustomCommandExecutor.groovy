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

package bpipe.executor

import bpipe.Command;
import bpipe.CommandStatus
import bpipe.Config;
import bpipe.OSResourceThrottle;
import bpipe.PipelineError
import bpipe.Utils
import bpipe.storage.StorageLayer
import groovy.util.logging.Log

/**
 * Custom command executors are implemented by a shell script that implements the
 * contract for the commands start, status and stop.  This 
 * implementation acts as a proxy to the shell script to
 * the rest of Bpipe internals so that it looks like a 
 * normal {@link CommandExecutor}.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class CustomCommandExecutor implements PersistentExecutor {
    
    public static final long serialVersionUID = 0L
    
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
     * The id assigned to this command by the queuing system.
     * This is NOT the Bpipe id for the command
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
     * The actual command executed
     */
    String runningCommand
    
    /**
     * Date started
     */
    Date startedAt
  
    /**
     * Whether cleanup has been called 
     */
    boolean cleanedUp
    
    /**
     * The command that we are executing - only available in the live
     * Bpipe instance running the command, not when restored via serialization
     */
    transient Command command
    
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
    void start(Map cfg, Command command, Appendable outputLog, Appendable errorLog) {
		
        this.command = command
		this.config = cfg
        this.name = command.name
        
        log.info "Executing command using custom command runner ${managementScript}:  ${Utils.truncnl(command.command,100)}"
        ProcessBuilder pb = new ProcessBuilder("bash", managementScript, "start")
        Map env = pb.environment()
        
        // Environment variables that can be used to transmit 
        // essential information
        env.NAME = name
        
        String id = command.id
        
        this.jobDir = ".bpipe/commandtmp/$id"
		File jobDirFile = new File(this.jobDir)
        if(!jobDirFile.exists())
		    jobDirFile.mkdirs()
        env.JOBDIR = jobDirFile.absolutePath
        
        this.setEnvironment(env)
            
        log.info "Using account: $env?.account"
        
        String startCmd = pb.command().join(' ')
        log.info "Starting command: " + startCmd
        
        this.command.createTimeMs = System.currentTimeMillis()
        this.runningCommand = command.command
        this.startedAt = new Date()
		
		withLock(cfg) {
	        Process p = pb.start()
            Utils.withStreams(p) {
    	        StringBuilder out = new StringBuilder()
    	        StringBuilder err = new StringBuilder()
    	        p.waitForProcessOutput(out, err)
    	        int exitValue = p.waitFor()
    	        if(exitValue != 0) {
    	            reportStartError(startCmd, out,err,exitValue)
    	            throw new PipelineError("Failed to start command:\n\n$command.command")
    	        }
                String rawOutput = out.toString() + "\n" + err.toString()
    	        this.commandId = rawOutput.trim()
    	        if(this.commandId.isEmpty())
    	            throw new PipelineError("Job runner ${this.class.name} failed to return a job id for job $id ($name) despite reporting success exit code for command:\n\n$startCmd\n\nRaw output was:[" + rawOutput + "]")
    	            
    	        log.info "Started command with id $commandId"
	        }
		}
    }
    
    /**
     * Child classes can make adjustments to the environment that is passed to the 
     * command by overriding this method. Generally they should still call super()
     * to ensure any global environment is correctly set.
     * 
     * @param env   Map containing values of environment variables. 
     *              the standard set.
     */
    void setEnvironment(Map env) {
        String commandScript = '.bpipe/commandtmp/'+command.id+'/'+command.id+'.sh'
        File commandScriptFile = new File(commandScript)
        commandScriptFile.text = command.command
        commandScriptFile.setExecutable(true)
        String shell = command.shell ? command.shell.join(' ') : 'bash -e'
   
        if(config?.stdbuf) {
            env.COMMAND = 'stdbuf -o0 ' + commandScript + ' > .bpipe/commandtmp/'+command.id+'/'+command.id+'.out 2>  .bpipe/commandtmp/'+command.id+'/'+command.id+'.err'
        }
        else {
            env.COMMAND = '( ' + shell + ' ' + commandScriptFile.path + ') > .bpipe/commandtmp/'+command.id+'/'+command.id+'.out 2>  .bpipe/commandtmp/'+command.id+'/'+command.id+'.err'
        }
            
        if(config == null) {
            return
        }
        
        // If an account is specified by the config then use that
        if(config?.account)
            env.ACCOUNT = config.account
        
        if(config?.walltime)
            env.WALLTIME = config.walltime
       
        if(config?.memory)
            env.MEMORY = String.valueOf(config.memory)
             
        if(config?.procs)
            env.PROCS = config.procs.toString()
            
        if(config?.proc_mode)
            env.PROC_MODE = config.proc_mode.toString()
            
         if(config?.queue)
            env.QUEUE = config.queue
            
        if(config?.custom)
            env.CUSTOM = String.valueOf(config.custom)
            
        if(config?.post_cmd)
            env.POST_CMD = String.valueOf(config.post_cmd)
             
        if(config?.nodes)
            env.NODES = String.valueOf(config.nodes)
            
        if(config?.mem_param)
            env.MEM_PARAM = String.valueOf(config.mem_param)
            
        if('custom_submit_options' in config)
            env.CUSTOM_SUBMIT_OPTS = String.valueOf(config.custom_submit_options)
            
        if('gpus' in config) {
            env.GPUS = String.valueOf(config.gpus)
        } 
  
        // modules since we may need to load modules before the command... - Simon Gladman (slugger70) 2014
        if(config?.modules) {
            log.info "Using modules: $config?.modules"
            env.MODULES = config.modules
        }
    }
	
	static synchronized acquireLock(Map cfg) {
        OSResourceThrottle.instance.acquireLock(cfg)
	}
    
    transient String lastStatus = CommandStatus.UNKNOWN.name()
    
    @Override
    public String status() {
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

    /**
     * For custom commands status is returned by calling the shell script with the
     * 'status' argument and the stored command id.
     */
    public String statusImpl() {
        String cmd = "bash $managementScript status ${commandId}"
		String result
		withLock {
	        Process p = Runtime.runtime.exec(cmd)
            Utils.withStreams(p) {
    	        StringBuilder out = new StringBuilder()
    	        StringBuilder err = new StringBuilder()
    	        p.waitForProcessOutput(out, err)
    	        int exitValue = p.waitFor() 
    	        if(exitValue != 0)
    	            return CommandStatus.UNKNOWN.name()
    	        result = out.toString().trim()
	        }
        }
        
        String statusValue = result.split()[0]
        if(this.command) {
            try {
                this.command.setStatus(statusValue)
            }
            catch(Exception e) {
                log.warning("Failed to update status for result $result: $e")
            }
        }
        return statusValue
    }
	
	/**
	 * Execute the action within the lock that ensures the
	 * maximum concurrency for the custom script is not exceeded.
	 */
	def withLock(Closure action) {
		OSResourceThrottle.instance.withLock(null,action)
	}
	
	/**
	 * Execute the action within the lock that ensures the
	 * maximum concurrency for the custom script is not exceeded.
	 */
	def withLock(Map cfg, Closure action) {
		OSResourceThrottle.instance.withLock(cfg,action)
	}

    @Override
    public int waitFor() {
        String cmd = "bash " + managementScript + " status ${commandId}"
        
        // Don't rely on the queueing software to be completely reliable; a single
        // failure to check shouldn't cause us to abort, so count errors 
        int errorCount = 0
		
		// To save on excessive polling while avoiding large latency for short jobs
		// implement an exponential backoff for time between polling for status
		int minSleep = Config.userConfig.minimumCommandStatusPollInterval?:2000
        
        // Unfortunately my experience with VLSCI is that even 5 seconds is causing issues
        // It's very hard to pick a good value here, but I'd rather Bpipe work and use a little
        // too many resources than just not work properly. Further work on a blocking implementation
        // for Torque will resolve this.
		int maxSleep = Config.userConfig.maxCommandStatusPollInterval?:3000 
		int backoffPeriod = Config.userConfig.commandStatusBackoffPeriod?:180000 // 3 minutes default to reach maximum sleep
		
		int statusRetries = Config.userConfig.getOrDefault('statusPollRetries',MAX_STATUS_ERROR)
        
		// Calculate an exponential backoff factor
		double factor = Math.log(maxSleep - minSleep) / backoffPeriod
		
		long startTimeMillis = System.currentTimeMillis()
		int currentSleep = minSleep
        String lastStatus = "NONE" 
        while(true) {
            
            log.fine "Polling status of job $commandId with command $cmd with sleep for $currentSleep with $statusRetries"
            
            String result = Utils.withRetries(statusRetries, message: "polling command $commandId") {
                StringBuilder out = new StringBuilder()
                StringBuilder err = new StringBuilder()
                int exitValue = withLock {
                    Process p = Runtime.runtime.exec(cmd)
                    Utils.withStreams(p) {
                        p.waitForProcessOutput(out, err)
                        p.waitFor()
                    }
                }
                if(exitValue > 0)  {
                    String msg = "Attempt to poll status for job $commandId return status $exitValue using command $cmd:\n\n  $out"
                    throw new PipelineError(msg)
                }
                return out.toString()
            }
           
//            log.info "Poll returned output: $result"
            def parts = result.split()
            String status = parts[0]
            
            if(status != lastStatus)
                log.info "Poll returned new status for command $commandId: $status"
                
            if(this.command)
                this.command.status = status
                
            lastStatus = status
            
            if(status == CommandStatus.COMPLETE.name()) {
	            if(parts.size() != 2)
	                throw new PipelineError("Unexpected format in output of job status command [$cmd]:  $result")
                    
                if(!this.cleanedUp) {
                    this.cleanedUp = true
                    cleanup()
                }
                
	            return Integer.parseInt(parts[1])
            }
            else 
            if(status == CommandStatus.UNKNOWN.name()) {
                if(errorCount > MAX_STATUS_ERROR) {
                    throw new PipelineError("Job ${commandId} returned UNKNOWN status prior to completion. This may indicate your job queue is configured to time out jobs too quickly.  Please consult your administrator to see if job information can be retained longer.")
                }
                else
                  log.warning "Job status query returned UNKNOWN for job ${commandId}. Will retry."

                ++errorCount
            }
            else
                errorCount = 0
            
//            log.fine "Wait $currentSleep before next poll"
            
            // Job not complete, keep waiting
            Thread.sleep(currentSleep)
            
			
			currentSleep = minSleep + Math.min(maxSleep, Math.exp(factor * (System.currentTimeMillis() - startTimeMillis)))
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
			int exitValue
			StringBuilder err
			StringBuilder out
			withLock {
	            err = new StringBuilder()
	            out = new StringBuilder()
	            Process p = Runtime.runtime.exec(cmd)
                Utils.withStreams(p) {
    	            p.waitForProcessOutput(out,err)
    	            exitValue = p.waitFor()
	            }
			}
            
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
    
    void cleanup() {
    }
    
    String statusMessage() {
        "$runningCommand, running since $startedAt ($config), Queue ID = #${commandId}, Job ID = ${command?.id}"
    }

    /**
     * Note: because the currently implemented CustomCommandExecutors all store a Job ID which is not
     * transient, all of them are considered automatically re-connected.
     */
    @Override
    public void reconnect(Appendable outputLog, Appendable errorLog) {
        log.info "Reconnecting to command $commandId"
    }

    @Override
    public String localPath(String storageName) {
        return '';
    }

    @Override
    public void mountStorage(StorageLayer storage) {
        // noop - default assumption is that the storage is mounted already
        // TODO for futre:
    }
}
