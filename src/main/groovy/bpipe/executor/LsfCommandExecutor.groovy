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

import groovy.util.logging.Log
import java.util.regex.Pattern
import java.util.regex.Matcher

import bpipe.Command;
import bpipe.ForwardHost
import bpipe.Utils
import bpipe.PipelineError
import bpipe.CommandStatus

/**
 * Implements a command executor submitting jobs to a Load Sharing Facility (LSF) cluster
 * <p>
 * See http://en.wikipedia.org/wiki/Platform_LSF
 *     http://www.platform.com/workload-management/high-performance-computing
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Mixin(ForwardHost)
@Log
class LsfCommandExecutor extends TemplateBasedExecutor implements CommandExecutor {

    private static String CMD_LSF_OUT_FILENAME = "cmd.lsf.out"
    
    private static final int NO_EXIT_FILE_STATUS = -2
    
    /**
     * The output from bjobs after the first successful query of the job status
     * For a non-array job, this will only have one entry. However when an array
     * job runs, it will have 'n' entries where 'n' is the number of jobs in the array
     * Each entry is a job index (an integer number, although it is a string)
     */
    private List<String> jobIndexes
    
    /**
     * Exit codes for all the jobs that ran
     */
    private List<Integer> exitCodes 
    
	/**
	 * Start the execution of the command in the LSF environment.
	 * <p> 
	 * The command have to be wrapper by a script shell that will be specified on the 'bsub' command line.
	 * This method does the following:
	 * - Create a command script wrapper named {@link #CMD_SCRIPT_FILENAME} in the job execution directory
	 * - Redirect the command stdout to the file {@link #CMD_OUT_FILENAME}
	 * - Redirect the command stderr to the file {@link #CMD_ERR_FILENAME}
	 * - The script wrapper save the command exit code in a file named {@link #CMD_EXIT_FILENAME} containing
	 *   the job exit code. To monitor for job termination will will wait for that file to exist
	 */
    @Override
    void start(Map cfg, Command command, File outputDirectory, Appendable outputLog, Appendable errorLog) {
        
        this.config = cfg
        this.id = command.id
        this.name = command.name;
        this.cmd = command.command ?.trim();
        this.command = command

        this.jobDir = ".bpipe/commandtmp/$id"
        File jobDirFile = new File(this.jobDir)
        if(!jobDirFile.exists()) {
            jobDirFile.mkdirs()
        }

        // If an account is specified by the config then use that
        log.info "Using account: $config?.account"
		
		/*
		 * Prepare the 'bsub' cmdline. The following options are used:
		 * - o: redirect the standard output to the specified file
		 * - e: redirect the error output to the specified file
         *      NOTE: used to be -eo, but this is not compatible with OpenLava,
         *      so for cross compatibility, we stick with -e. This means in LSF
         *      it will write in append mode, but that should be OK because it is
         *      a newly created file for each command in any case.
		 * - J: defines the job name
		 * - q: declares the queue to use
		 *
		 * Note: since LSF append a noise report information to the standard out
		 * we suppress it, and save the 'cmd' output in the above script
		 */
		def startCmd = "bsub -o $jobDir/$CMD_LSF_OUT_FILENAME -e $jobDir/$CMD_ERR_FILENAME "
        
		// add other parameters (if any)
		if(config?.queue) {
			startCmd += "-q ${config.queue} "
		}

        if(config?.walltime) {
            startCmd += "-W ${config.walltime} "
        }
                
        if(config?.procs) {
            startCmd += "-n $config.procs "
        }

        if( config?.lsf_request_options ) {
            startCmd += config.lsf_request_options + ' '
        }
        
		// at the end append the command script wrapped file name
		startCmd += "< $jobDir/$CMD_SCRIPT_FILENAME"
		
		log.info "Submitting LSF job with command: ${startCmd}"
		
        submitJobTemplate(startCmd, cmd, "executor/lsf-command.template.sh", outputLog, errorLog)
    }


    static final Pattern JOB_PATTERN = Pattern.compile('^Job <(\\d+)> .*$');

    /**
     * Parse the 'bsub' text output fetching the ID of the submitted job
     *
     * @param text The text as returned by the {@code bsub} command
     * @return The ID of the new newly submitted job
     */
    protected String parseCommandId(String text) {
        def reader = new BufferedReader(new StringReader(text));
        def line
        try {
            while( (line=reader.readLine()?.trim()) != null ) {

                Matcher matcher = JOB_PATTERN.matcher(line);
                if(matcher.matches()) {
                    return matcher.group(1);
                }
            }
        }
        finally {
            reader.close();
        }

        return null
    }

    @Override
    String status() {
        String result = statusImpl()
        return this.command.status = result
    }
    
    private static LSF_ARRAY_JOB_PATTERN =  ~/[a-zA-Z0-9]*\[([0-9]*)\]/
 
    String statusImpl() {
        
        log.info "Querying status for LSF job $commandId"
		
		if( !new File(jobDir, CMD_SCRIPT_FILENAME).exists() ) {
			return CommandStatus.UNKNOWN
		}
		
		if(!commandId ) {
			return CommandStatus.QUEUEING	
		}
        
        if(this.jobIndexes == null) {
            this.jobIndexes = queryJobIndexes(commandId)
            log.info "Found job indexes $jobIndexes for LSF job"
        }
        
        // I'm not sure if this is a valid state: it's been submitted and has
        // a job id, but bjobs did not return any reference to the job?
        if(!jobIndexes)
            return CommandStatus.QUEUEING
        
        // For the job to be complete, all the exit files must exist
        this.exitCodes = jobIndexes.collect { jobIndex ->
            File jobExitFile = new File(jobDir, CMD_EXIT_FILENAME + "." + jobIndex)
    		if(jobExitFile.exists()) {
                try {
                    jobExitFile.text.trim().toInteger()
                }
                catch(Exception e) {
                    log.warning "Failed to parse LSF exit code file $jobExitFile"
                    NO_EXIT_FILE_STATUS // parse exception? the file was half written?!
                }
            }
            else {
                NO_EXIT_FILE_STATUS
            }
        }
        
        int countRunning = exitCodes.count { it == NO_EXIT_FILE_STATUS }
        if(countRunning>0) {
            log.info "There are still $countRunning LSF jobs without an exit status (presumed still running)"
            return CommandStatus.RUNNING
        }
        else
            return CommandStatus.COMPLETE
    }
    
    /**
     * Query the job indexes for the given LSF / OpenLava job id.
     * <p>
     * Job indexes are assigned after the job id is assigned. For a non-array job, the
     * job index is 0. However for an array job, the job indexes are assigned from
     * the array pattern specified in the job name.
     * 
     * @param commandId     the LSF/OpenLava job id for the job
     * @return  a list of strings representing the array indices (in practice, integers)
     */
    List<String> queryJobIndexes(String commandId) {
        String bjobsCommand = "bjobs -w -a $commandId"
        
        String bjobsOutput = bjobsCommand.execute().text
        if(bjobsOutput == null || bjobsOutput.trim().isEmpty()) {
            log.warning "bjobs command [$bjobsCommand] did not return output"
            return null
        }
        
        log.info "Parsing bjobs output:\n\n$bjobsOutput\n"
        
        // Read the lines and parse into space separated fields
        List<List> lines = bjobsOutput.split(/[\r\n]/)*.split(/[ \t]{1,}/)
        
        // Convert them to maps for easy reference
        List<Map> jobs = lines[1..-1].collect { [ lines[0], it ].transpose().collectEntries() }
        
        // Now we have a list of all the jobs we need to wait for
        return jobs.collect { Map job ->
            log.info "Extracting job index from: $job"
            def matches = LSF_ARRAY_JOB_PATTERN.matcher(job.JOB_NAME)
            if(matches) {
                matches[0][1]
            }
            else {
                "0"
            }
        }
    }

    /**
     * Wait for the sub termination
     * @return The program exit code. Zero when everything is OK or a non-zero on error
     */
    @Override
    int waitFor() {
        
		while( !stopped ) {
            
            String statusValue = status()
            if(statusValue == "COMPLETE")
                return this.exitCodes.find { it != 0 } ?: 0
	
			Thread.sleep(5000)	
		}

        return -1
    }

    /**
     * Kill the job execution
     *
     */
    @Override
    void stop() {

        // mark the job as stopped
        // this will break the {@link #waitFor} method as well
        stopped = true


		String cmd = "bkill $commandId"
		log.info "Executing command to stop command $id: $cmd"

		int exitValue
		StringBuilder err
		StringBuilder out

		err = new StringBuilder()
		out = new StringBuilder()
		Process p = Runtime.runtime.exec(cmd)
		Utils.withStreams(p) {
			p.waitForProcessOutput(out,err)
			exitValue = p.waitFor()
		}
		
		if(exitValue != 0 ) {
			
			def msg = "LSF failed to stop command $id, returned exit code $exitValue from command line: $cmd"
			log.severe "Failed stop command produced output: \n$out\n$err"
			if(!err.toString().trim().isEmpty()) {
				msg += "\n" + Utils.indent(err.toString())
			}
			throw new PipelineError(msg)

		}
        
		// Successful stop command
		log.info "Successfully called script to stop command $id"
    }
    
    @Override
    public String statusMessage() {
        return "LSF [$name] JobID: $commandId, command: $cmd"
    }
}
