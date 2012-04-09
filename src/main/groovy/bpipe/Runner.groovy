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
    
    final static String version = System.getProperty("bpipe.version")
    
    final static String builddate = System.getProperty("bpipe.builddate")?:System.currentTimeMillis()
    
    
    static CliBuilder runCli = new CliBuilder(usage: 
   """bpipe [run|test] [-h] [-t] [-d] [-n <threads>] [-v] <pipeline> <in1> <in2>...
history 
log
jobs
diagram
diagrameditor""")
    
    static CliBuilder stopCommandsCli = new CliBuilder(usage: "bpipe stopcommands\n")
    
    static CliBuilder diagramCli = new CliBuilder(usage: "bpipe diagram [-e] <pipeline> <input1> <input2> ...\n")
    
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
			
			if(Config.config.eraseLogsOnExit) {
				String ourPid = System.getProperty("bpipe.pid")
				new File(".bpipe/logs/${ourPid}.erase.log").text=""
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
        
        def groovyArgs = ["--main", "groovy.ui.GroovyMain", "-e"] 
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
        else 
        if(mode == "stopcommands") {
            log.info("Stopping running commands")
            cli = stopCommandsCli
            Config.config["mode"] = "stopcommands"
            int count = new CommandManager().stopAll()
            println "Stopped $count commands"
            System.exit(0)
        } 
        else {
            cli = runCli
	        cli.with {
	             h longOpt:'help', 'usage information'
	             d longOpt:'dir', 'output directory', args:1
	             t longOpt:'test', 'test mode'
	             n longOpt:'threads', 'maximum threads', args:1
	             v longOpt:'verbose', 'print internal logging to standard error'
	        }
        }
        
        String versionInfo = "\nBpipe Version $version   Built on ${new Date(Long.parseLong(builddate))}\n"
        
        def opt = cli.parse(args)
        if(!opt) 
            System.exit(1)
            
        if(!opt.arguments()) {
            println versionInfo
            cli.usage()
            println "\n"
            System.exit(1)
        }
		
//		println("Executing using args: " + groovyArgs)
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
        
        if(opts.n) {
            log.info "Maximum threads specified as $opts.n"
            Config.config.maxThreads = Integer.parseInt(opts.n)
        }
        
		File pipelineFile = new File(opt.arguments()[0])
        if(!pipelineFile.exists()) {
            println "\nCould not understand command $pipelineFile or find it as a file\n"
            cli.usage()
            println "\n"
            System.exit(1)
        }
        
		groovyArgs += "import static Bpipe.*; " + pipelineFile.text
		if(opt.arguments().size() > 1) 
			groovyArgs += opt.arguments()[1..-1]
        
        Config.readUserConfig()
		
		// Add event listeners that come directly from configuration
		EventManager.instance.configure(Config.userConfig)
		
		// If we got this far and are not in test mode, then it's time to 
		// make the logs stick around
		if(!opts.t)
			Config.config.eraseLogsOnExit = false
		
        org.codehaus.groovy.tools.GroovyStarter.main(groovyArgs as String[])
    }
	
//    public static void launch(String [] args) {
//        String conf = System.getProperty("groovy.starter.conf",null);
//        LoaderConfiguration lc = new LoaderConfiguration();
//        
//        // evaluate parameters
//        boolean hadMain=false, hadConf=false, hadCP=false;
//        int argsOffset = 0;
//        while (args.length-argsOffset>0 && !(hadMain && hadConf && hadCP)) {
//            if (args[argsOffset].equals("--classpath")) {
//                if (hadCP) break;
//                if (args.length==argsOffset+1) {
//                    exit("classpath parameter needs argument");
//                }
//                lc.addClassPath(args[argsOffset+1]);
//                argsOffset+=2;
//                hadCP=true;
//            } else if (args[argsOffset].equals("--main")) {
//                if (hadMain) break;
//                if (args.length==argsOffset+1) {
//                    exit("main parameter needs argument");
//                }
//                lc.setMainClass(args[argsOffset+1]);
//                argsOffset+=2;
//                hadMain=true;
//            } else if (args[argsOffset].equals("--conf")) {
//                if (hadConf) break;
//                if (args.length==argsOffset+1) {
//                    exit("conf parameter needs argument");
//                }
//                conf=args[argsOffset+1];
//                argsOffset+=2;
//                hadConf=true;
//            } else {
//                break;
//            }            
//        }
//
//        // this allows to override the commandline conf
//        String confOverride = System.getProperty("groovy.starter.conf.override",null);
//        if (confOverride!=null) conf = confOverride;
//
//        // we need to know the class we want to start
//        if (lc.getMainClass()==null && conf==null) {
//            exit("no configuration file or main class specified");
//        }
//        
//        // copy arguments for main class 
//        String[] newArgs = new String[args.length-argsOffset];
//        for (int i=0; i<newArgs.length; i++) {
//            newArgs[i] = args[i+argsOffset];
//        }        
//        // load configuration file
//        if (conf!=null) {
//            try {
//                lc.configure(new FileInputStream(conf));
//            } catch (Exception e) {
//                System.err.println("exception while configuring main class loader:");
//                exit(e);
//            }
//        }
//        // create loader and execute main class
//        ClassLoader loader = new RootLoader(lc);
//        Method m=null;
//        try {
//            Class c = loader.loadClass(lc.getMainClass());
//            m = c.getMethod("main", new Class[]{String[].class});
//        } catch (ClassNotFoundException e1) {
//            exit(e1);
//        } catch (SecurityException e2) {
//            exit(e2);
//        } catch (NoSuchMethodException e2) {
//            exit(e2);
//        }
//        try {
//            m.invoke(null, new Object[]{newArgs});
//        } catch (IllegalArgumentException e3) {
//            exit(e3);
//        } catch (IllegalAccessException e3) {
//            exit(e3);
//        } catch (InvocationTargetException e3) {
//            exit(e3);
//        } 
//    }
//	
    
    /**
     * Try to determine the process id of this Java process.
     * Because the PID is read from a file that is created after
     * starting of the Java process there is a race condition and thus
     * this call *may* wait some (small) time for the file to appear.
     * 
     * @return    process ID of our process
     */
	private static String resolvePID() {
        // If we weren't given a host pid, assume we are running as a generic
        // command and just put the log files, etc, under this name
		String pid = "command"

		// This property is stored as a file by the hosting bash script
		String ourPid = System.getProperty("bpipe.pid")
		if(ourPid) {
			File pidFile = new File(".bpipe/launch/${ourPid}")
			int count = 0
			while(true) {
				if(pidFile.exists()) {
					pid = pidFile.text.trim()
					pidFile.delete()
					break
				}

				if(count > 100) {
					println "ERROR: Bpipe was unable to read its startup PID file from $pidFile"
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
