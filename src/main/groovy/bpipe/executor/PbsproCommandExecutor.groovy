/*
* Copyright (c) 2013, davide.rambaldi@gmail.com
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

import groovy.util.logging.Log;
import bpipe.ForwardHost;
import bpipe.Command;
import bpipe.PipelineError;
import bpipe.Utils;

/**
 * Implementation of support for PBS Professional resource manager.
 * <p>
 * The actual implementation is a shell script written by
 * Davide Rambaldi <davide.rambaldi@gmail.com>.  This is just a
 * wrapper class that provides access to it via the
 * CustomJob parent class support for shell script job
 * managers.
 *
 * @author davide.rambaldi@gmail.com
 */
@Mixin(ForwardHost)
@Log
class PbsproCommandExecutor extends CustomCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Constructor
     */
    PbsproCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-pbspro.sh"))
    }

    /**
     * Adds forwarding of standard err & out for processes started using Torque.
     * These appear as files in the local directory.
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

        super.setEnvironment(env)

        // Davide Rambaldi: write to env the positon of PBS log files
        env.PBSOUTPUT = jobDirFile.absolutePath + "/${id}.pbs.log"
        env.PBSERROR = jobDirFile.absolutePath + "/${id}.pbs.err.log"

        if(config?.project) {env.PROJECT = config.project}

        // instead of resources we have a select statement
        if(config?.select_statement) {env.SELECT_STATEMENT = config.select_statement}

        log.info "Using account: $env?.account"

        String startCmd = pb.command().join(' ')
        log.info "Starting command: " + startCmd

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
                this.commandId = out.toString().trim()
                if(this.commandId.isEmpty())
                    throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$startCmd\n\nRaw output was:[" + out.toString() + "]")

                log.info "Started command with id $commandId"
            }
        }

        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs

        // FIXME; I don't see any forward to stderr and stdout
        // Seems due to Forwarder.groovy class that at line 90 can't find the files ...
        // let's try to force file creation
        new File(jobDirFile.absolutePath+"/${id}.out").createNewFile()
        new File(jobDirFile.absolutePath+"/${id}.err").createNewFile()

        forward(jobDirFile.absolutePath+"/${id}.out", outputLog)
        forward(jobDirFile.absolutePath+"/${id}.err", errorLog)
    }

    /**
     * Adds custom cleanup of pbspro created files and stop any threads forwarding output
     */
    @Override
    public void stop() {
        super.stop();
        cleanup()
    }

	void cleanup() {
		this.forwarders*.cancel()
		File of = new File(this.name+".o"+this.commandId)
		if(of.exists())
			of.delete()
		File ef = new File(this.name+".e"+this.commandId)
		if(ef.exists())
			ef.delete()
	}

    /**
     * The pbspro script / system produces files like BpipeJob.o133722 and
     * BpipeJob.e133722 that contain standard output and error.  We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     * Note that:
     * PBS pro -N name have the following specs:
     * Format: string, up to 15  characters  in  length.
     * We trim in bpipe-pbspro.sh the job name to first 15 chars then we must trim also here
     */
    List<String> getIgnorableOutputs() {
        def trimmed = this.name.size() > 15 ? this.name[0..14] : this.name
        // println "Ignorable inputs trimmed are: " + trimmed + '.o.*$' + " and "  + trimmed + '.e.*$'
        return [ trimmed + '.o.*$', trimmed + '.e.*$' ]
    }

    String toString() {
        "Pbs Pro Job [" + super.toString() + "]"
    }
}
