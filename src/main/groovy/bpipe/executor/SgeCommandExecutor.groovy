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
import bpipe.Command;
import bpipe.ForwardHost
import bpipe.Utils
import bpipe.PipelineError
import bpipe.CommandStatus

/**
 * Implements a command executor based on the Sun Grid Engine (SGE) resource manager 
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Mixin(ForwardHost)
@Log
class SgeCommandExecutor implements CommandExecutor {

    private Map config;

    private String id;

    private String name;

    /* The command to execute through SGE 'qsub' command */
    private String cmd;

    private String jobDir;
	
    /* Mark the job as stopped by the user */
    private boolean stopped

    /* The ID of the job as returned by the JOB scheduler */
	private String commandId;
    
    /** Command object - only used for updating status */
    Command command
	
	private static String CMD_EXIT_FILENAME = "cmd.exit"
	
	private static String CMD_SCRIPT_FILENAME = "cmd.sh"

    private static String CMD_OUT_FILENAME = "cmd.out"

    private static String CMD_ERR_FILENAME = "cmd.err"

	/**
	 * Start the execution of the command in the SGE environment. 
	 * <p> 
	 * The command have to be wrapper by a script shell that will be specified on the 'qsub' command line. 
	 * This method does the following 
	 * - Create a command script wrapper named '.cmd.sh' in the job execution directory 
	 * - Redirect the command stdout to the file ..
	 * - Redirect the command stderr to the file ..
	 * - The script wrapper save the command exit code in a file named '.cmd.exit' containing
	 *   the job exit code. To monitor for job termination will will wait for that file to exist
	 */
    @Override
    void start(Map cfg, Command command, File outputDirectory) {
        this.config = cfg
        this.id = command.id
        this.name = command.name;
        this.cmd = command.command?.trim();
        this.command = command
        
        this.jobDir = ".bpipe/commandtmp/$id"
        File jobDirFile = new File(this.jobDir)
        if(!jobDirFile.exists()) {
            jobDirFile.mkdirs()
        }

        // If an account is specified by the config then use that
        log.info "Using account: $config?.account"
		
		/*
		 * Create '.cmd.sh' wrapper used by the 'qsub' command
		 */
		def cmdWrapperScript = new File(jobDir, CMD_SCRIPT_FILENAME)
		cmdWrapperScript.text =  
			"""\
			#!/bin/sh
			${cmd}
			result=\$?
			echo -n \$result > $jobDir/$CMD_EXIT_FILENAME
			exit \$result
			"""
			.stripIndent()
		
		/*
		 * Prepare the 'qsub' cmdline. The following options are used
		 * - wd: define the job working directory 
		 * - terse: output just the job id on the output stream
		 * - o: define the file to which redirect the standard output
		 * - e: define the file to which redirect the error output 
		 */
		def startCmd = "qsub -N \"$name\" -wd \$PWD -terse -o $jobDir/$CMD_OUT_FILENAME -e $jobDir/$CMD_ERR_FILENAME -V "
		// add other parameters (if any)
		if(config?.queue) {
			startCmd += "-q ${config.queue} "
		}

        if( config?.walltime )  {
            startCmd += "-l h_rt=${config.walltime} "
        }

        if( config?.procs && config.procs.toString().isInteger() ) {
            startCmd +=  "-l slots=${config.procs} "
        }
        else if ( config?.procs ) {
            startCmd += "-pe ${config.procs} "
        }

        if( config?.memory ) {
            /*
             * Read more about SGE virtual_free vs mem_free at the following links
             * http://gridengine.org/pipermail/users/2011-December/002215.html
             * http://www.gridengine.info/tag/virtual_free/
             */
            startCmd += "-l virtual_free=${config.memory} "
        }

        if( config?.sge_request_options ) {
            startCmd += config.sge_request_options + ' '
        }
		
		// at the end append the command script wrapped file name
		startCmd += "$jobDir/$CMD_SCRIPT_FILENAME"
		
		/*
		 * prepare the command to invoke
		 */
		log.info "Starting command: '${startCmd}'"
		
		ProcessBuilder pb = new ProcessBuilder("bash", "-c", startCmd)
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
				throw new PipelineError("Job runner ${this.class.name} failed to return a job id despite reporting success exit code for command:\n\n$startCmd\n\nRaw output was:[" + out.toString() + "]")
				
			log.info "Started command with id $commandId"
		}



        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        forward("$jobDir/$CMD_OUT_FILENAME", System.out)
        forward("$jobDir/$CMD_ERR_FILENAME", System.err)
		
    }
	
	
	void reportStartError(String cmd, def out, def err, int exitValue) {
		log.severe "Error starting custom command using command line: " + cmd
		System.err << "\nFailed to execute command using command line: $cmd\n\nReturned exit value $exitValue\n\nOutput:\n\n$out\n\n$err"
	}

    @Override
    String status() {
        String result = statusImpl()
        return this.command.status = result
    }
    
    /**
     * @return The current status as defined by {@link bpipe.CommandStatus}
     */
    String statusImpl() {
		
		if( !new File(jobDir, CMD_SCRIPT_FILENAME).exists() ) {
			return CommandStatus.UNKNOWN
		}
		
		if( !commandId ) {
			return CommandStatus.QUEUEING	
		}
		
		File resultExitFile = new File(jobDir, CMD_EXIT_FILENAME )
		if( !resultExitFile.exists() ) {
			return CommandStatus.RUNNING
		}  
		
		return CommandStatus.COMPLETE
    }

    /**
     * Wait for the sub termination
     * @return The program exit code. Zero when everything is OK or a non-zero on error
     */
    @Override
    int waitFor() {
		
		int count=0
		File exitFile = new File( jobDir, CMD_EXIT_FILENAME )
		while( !stopped ) {
			
			if( exitFile.exists() ) {
				def val = exitFile.text?.trim()
				if( val.isInteger() ) {
					// ok. we get the value as integer
                    command.status = CommandStatus.COMPLETE.name()
					return new Integer(val)	
				}	
				
				/*
				 * This could happen if there are latency in the file system.
				 * Try to wait and re-try .. if nothing change make it fail after a fixed amount of time
				 */
				Thread.sleep(500)
				if( count++ < 10 ) { continue }
				log.warn("Missing exit code value for command: '${id}'. Retuning -1 by default")
				return -1
			}
			
		
			Thread.sleep(5000)	
		}

        return -1
    }

    /**
     * Kill the job execution
     */
    @Override
    void stop() {

        // mark the job as stopped
        // this will break the {@link #waitFor} method as well
        stopped = true
		
		String cmd = "qdel $commandId"
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
		
		if( !exitValue ) {
			
			def msg = "SGE failed to stop command $id, returned exit code $exitValue from command line: $cmd"
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
    void cleanup() {
        this.forwarders*.cancel()
    }

    @Override
    List<String> getIgnorableOutputs() {
		//TODO ?? 
        return null 
    }
}
