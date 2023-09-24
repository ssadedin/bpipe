/*
* Copyright (c) 2012 MCRI, authors
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

import groovy.transform.CompileStatic
import groovy.util.logging.Log
import bpipe.Command;
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutedProcess
import bpipe.ForwardHost;
import bpipe.Utils

/**
 * Implementation of support for TORQUE resource manager.
 * <p>
 * The actual implementation is a shell script written by 
 * Bernie Pope <bjpope@unimelb.edu.au>.  This is just a 
 * wrapper class that provides access to it via the
 * CustomJob parent class support for shell script job
 * managers.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class TorqueCommandExecutor extends CustomCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L
    
    /**
     * If set to true, exec the status shell script for every status poll, otherwise use pooled job polling
     * with direct calls to <code>qstat -x</code>
     */
    boolean useLegacyJobPolling = bpipe.Config.userConfig.containsKey('useLegacyTorqueJobPolling') && bpipe.Config.userConfig.useLegacyTorqueJobPolling

    /**
     * Constructor
     */
    TorqueCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-torque.sh"))
    }
    
    TorqueCommandExecutor(File executorScript) {
        super(executorScript)
    }

    /**
     * Adds forwarding of standard err & out for processes started using Torque.
     * These appear as files in the local directory.
     */
    @Override
    void start(Map cfg, Command command, Appendable outputLog, Appendable errorLog) {
        
        super.start(cfg, command, outputLog, errorLog);
        
        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        log.info "Forwarding file " + this.jobDir+"/${command.id}.out"
        forward(this.jobDir+"/${command.id}.out", outputLog)
        forward(this.jobDir+"/${command.id}.err", errorLog)
    }
    
    @Override
    public int waitFor() {
        
        if(useLegacyJobPolling) {
            log.info "Using legacy torque status polling"
            return super.waitFor()
        }
        
        int exitCode = TorqueStatusMonitor.getInstance().waitFor(command, commandId)
        log.info "Exit code $exitCode returned for $commandId from TorqueStatusMonitor"
        
        if(this.command)
            this.command.status = CommandStatus.COMPLETE
        
        this.cleanup()
        
        return exitCode
    }

    /**
     * Adds custom cleanup of torque created files and stop any threads forwarding output 
     */
    @Override
    @CompileStatic
    public void stop() {
        super.stop();
        
        // this is a basic throttle to prevent slamming a server with too many requests at once
        Thread.sleep((long)Config.userConfig.getOrDefault('torque.stopIntervalMs', 500L))
        
        cleanup()
    }

	void cleanup() {
        this.stopForwarding()
		File of = new File(this.name+".o"+this.commandId)
		if(of.exists())
			of.delete()
		File ef = new File(this.name+".e"+this.commandId)
		if(ef.exists())
			ef.delete()
	}

    /**
     * The torque script / system produces files like BpipeJob.o133722 and 
     * BpipeJob.e133722 that contain standard output and error.  We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     */
    @CompileStatic
    List<String> getIgnorableOutputs() {
        return [ this.name + '.o.*$', this.name + '.e.*$' ]
    }
    
    @CompileStatic
    void setJobName(String jobName) {
        log.info("Setting job name for $commandId to $jobName")
        ExecutedProcess result = Utils.executeCommand((List<Object>)["qalter","-N",jobName, this.commandId])
        if(result.exitValue != 0) {
            log.warning("Unable to set job name to $jobName for $commandId. Command output: " + result.out)
        }
    }

    @CompileStatic
    String toString() {
        "Torque Job [" + super.toString() + "]"
    }
}
