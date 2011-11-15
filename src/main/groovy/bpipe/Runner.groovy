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
package bpipe

import java.text.*;

import java.util.logging.*;

/**
 * A small wrapper that parses command line arguments and forwards execution
 * to the user's script
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Runner {
    
    private static Logger log = Logger.getLogger("bpipe.Runner");
    
    static CliBuilder runCli = new CliBuilder(usage: 
   """bpipe [run|test] [-h] [-t] [-d] [-v] <pipeline> <in1> <in2>...
history 
log
jobs
diagram
diagrameditor""")
    
    static CliBuilder diagramCli = new CliBuilder(usage: 'bpipe diagram [-e] <pipeline> <input1> <input2> ...\n')
    
    public static OptionAccessor opts = runCli.parse([])
    
    public static void main(String [] args) {
        
        def db = new File(".bpipe")
        if(!db.exists())
            if(!db.mkdir())
                throw new RuntimeException("Bpipe was not able to make its database directory, .bpipe in the local folder.  Is this folder read-only?")
        
        String pid = resolvePID()
	        
        // Before we do anything else, add a shutdown hook so that termination of the process causes the job to 
        // to be removed from the user's folder
        System.addShutdownHook { 
            def home = System.getProperty("user.home")
            def jobFile = new File("$home/.bpipedb/jobs/$pid")
            if(jobFile.exists()) {
                log.info("Deleting job file $jobFile")
                if(!jobFile.delete()) {
                    log.warn("Unable to delete job file for job $pid")
                    println("WARN: Unable to delete job file for job $pid")
                }
            }
        }
                
        def parentLog = log.getParent()
        parentLog.getHandlers().each { parentLog.removeHandler(it) }
        
        // The current log file
        FileHandler fh = new FileHandler(".bpipe/bpipe.log")
        fh.setFormatter(new BpipeLogFormatter())
        parentLog.addHandler(fh)
        
        // Another log file for history
        new File(".bpipe/logs").mkdirs()
        FileHandler pidLog = new FileHandler(".bpipe/logs/${pid}.bpipe.log")
        pidLog.setFormatter(new BpipeLogFormatter())
        parentLog.addHandler(pidLog)
        
        log.info("Starting")
        
        def cli
        
        def groovyArgs = ["--main", "groovy.ui.GroovyMain"] 
        String mode = System.getProperty("bpipe.mode")
        if(mode == "diagram")  {
            log.info("Mode is diagram")
            cli = diagramCli
            Config.config["mode"] = "diagram"
        }
        else 
        if(mode == "diagrameditor") {
            log.info("Mode is diagram editor")
            cli = diagramCli
            Config.config["mode"] = "diagrameditor"
            
        }
        else {
            cli = runCli
	        cli.with {
	             h longOpt:'help', 'usage information'
	             d longOpt:'dir', 'output directory', args:1
	             t longOpt:'test', 'test mode'
	             v longOpt:'verbose', 'print internal logging to standard error'
	        }
        }
        
        def opt = cli.parse(args)
        if(!opt)
            System.exit(1)
            
        if(!opt.arguments()) {
            cli.usage()
            println "\n"
            System.exit(1)
        }
        groovyArgs += opt.arguments()
        
        opts = opt
        
		if(opts.v) {
            ConsoleHandler console = new ConsoleHandler()
            console.setFormatter(new BpipeLogFormatter())
            console.setLevel(Level.FINE)
            parentLog.addHandler(console)
		}
        
        if(opts.d) {
            Config.config.defaultOutputDirectory = opts.d
        }
        
        String pipelineFile = opts.arguments()[0]
        if(!new File(pipelineFile).exists()) {
            println "\nCould not understand command $pipelineFile or find it as a file\n"
            cli.usage()
            println "\n"
            System.exit(1)
        }
        
        org.codehaus.groovy.tools.GroovyStarter.main(groovyArgs as String[])
    }

	private static String resolvePID() {
        // If we weren't given a host pid, assume we are running as a generic
        // command and just put the log files, etc, under this name
		String pid = "command"

		// This property is passed from the bpipe shell command line runner
		// It is the PID of the bash shell that started this process, not
		// the PID of this actual process
		String hostPid = System.getProperty("bpipe.pid")
		if(hostPid) {
			File hostPidFile = new File(".bpipe/launch/${hostPid}")
			int count = 0
			while(true) {
				if(hostPidFile.exists()) {
                    println "Found host pid file $hostPidFile"
					pid = hostPidFile.text.trim()
					hostPidFile.delete()
					break
				}

				if(count > 100) {
					println "ERROR: Bpipe was unable to read its startup PID file from $hostPidFile"
					println "ERROR: This may indicate you are in a read-only directory or one to which you do not have full permissions"
					System.exit(1)
				}

				// Spin a short time waiting
				Thread.sleep(20)
				++count
			}
		}
		return pid
	}
}
