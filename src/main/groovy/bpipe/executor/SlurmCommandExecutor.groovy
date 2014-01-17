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

/* Modified from TorqueCommandExecutor.groovy for Slurm 
*
* Approach is mimic the wrapper and shell script relationship, and replace 
* Torque commands with Slurm equivalents
*/

package bpipe.executor

import groovy.util.logging.Log
import bpipe.ForwardHost;
import bpipe.PipelineError
import bpipe.Utils

/**
 * Implementation of support for Slurm resource manager.
 * <p>
 * The actual implementation is a shell script based on the one 
 * written by Bernie Pope <bjpope@unimelb.edu.au>.  This is just a 
 * wrapper class (orignally written for Torque by Simon Sadedin 
 * <simon.sadedin@mcri.edu.au>) that provides access to it via the 
 * CustomJob parent class support for shell script job
 * managers. 
 * 
 * @author simon.sadedin@mcri.edu.au
 * @author andrew.lonsdale@lonsbio.com.au
 */
@Mixin(ForwardHost)
@Log
class SlurmCommandExecutor extends CustomCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Constructor
     */
    SlurmCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-slurm.sh"))
    }

    /**
     * Adds forwarding of standard err & out for processes started using Slurm.
     * These appear as files in the local directory.
     */
    @Override
    public void start(Map cfg, String id, String name, String cmd, File outputDirectory) {
        
        //super.start(cfg, id, name, cmd, outputDirectory);
               // need custom variables for single CPU, SMP and MPI
        // replaces call to super.start() with same content, extra option for jobtype  
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

        if(config?.memory)
            env.MEMORY = (config.memory.toInteger() * 1024).toString()

        if(config?.procs)
            env.PROCS = config.procs.toString()

        if(config?.queue)
            env.QUEUE = config.queue

        // jobtype since queues (parition in slurm) may not determine computation mode
        if(config?.jobtype)
        log.info "Using jobtype: $config?.jobtype"
            env.JOBTYPE = config.jobtype

         String startCmd = pb.command().join(' ')
        log.info "Starting command: " + startCmd

        this.runningCommand = startCmd
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
                        throw new PipelineError("Failed to start command:\n\n$cmd")
                    }
                    this.commandId = out.toString().trim()
                    if(this.commandId.isEmpty())
                        throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$startCmd\n\nRaw output was:[" + out.toString() + "] Error output: + [" + err.toString() + "]\n\nEnvironment: $env")

                    log.info "Started command with id $commandId"
                }
        } 
        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        forward(this.jobDir+"/${id}.out", System.out)
        forward(this.jobDir+"/${id}.err", System.err)
    }
    /**
     * Adds custom cleanup of slurm created files and stop any threads forwarding output 
     */
    @Override
    public void stop() {
        super.stop();
        cleanup()
    }

        void cleanup() {
            this.stopForwarding()
            // slurm12 is stdout and stderr
            File slurm12= new File("slurm-"+this.commandId+".out")
            if(slurm12.exists())
                        slurm12.delete()
        }

    /**
     * The slurm script / system produces a file slurm-SLURMID.out with both stderr and
     * stdout. We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     */
    @Override
    List<String> getIgnorableOutputs() {
        return ["slurm-"+this.commandId+".out"]
    }

    String toString() {
        return "Slurm Job [" + super.toString() + "]"
    }
}
