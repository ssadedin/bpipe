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

import java.util.logging.ConsoleHandler
import java.util.logging.FileHandler
import java.util.logging.Handler;
import java.util.logging.Level
import java.util.logging.Logger;

import org.apache.commons.cli.Option
import org.codehaus.groovy.runtime.StackTraceUtils;

import bpipe.agent.AgentRunner
import bpipe.cmd.ArchiveCommand
import bpipe.cmd.PreallocateCommand
import bpipe.cmd.Stop;
import bpipe.worx.WorxEventListener;

import groovy.transform.CompileStatic;
import groovy.util.logging.Log

/**
 * The main entry point for Bpipe.
 * <p>
 * Handles parsing and validation of command line parameters and flags,
 * reads the user script and creates a Groovy shell to run the script
 * with variables initialized correctly.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Runner {
    
    private static Logger log = Logger.getLogger("bpipe.Runner")
    
    final static String version = System.getProperty("bpipe.version")
    
    final static long startTimeMs = System.currentTimeMillis()
    
    final static String builddate = System.getProperty("bpipe.builddate")?:startTimeMs
    
    final static String runDirectory = new File(".").absoluteFile.parentFile.absolutePath
    
    final static String canonicalRunDirectory = new File(runDirectory).canonicalPath
    
    final static BpipeParamsBinding binding = new BpipeParamsBinding()
    
    static List<Command> runningCommands = null
	
    final static String DEFAULT_HELP = """
        bpipe [run|test|debug|touch|execute] [options] <pipeline> <in1> <in2>...
                   retry [job id] [test]
                   remake <file1> <file2>...
                   resume
                   stop [preallocated]
                   history 
                   log [-n <lines>] [job id]
                   jobs
                   checks [options]
                   override 
                   status
                   cleanup
                   query <file>
                   preallocate
                   archive [--delete] <zip file path>
                   autoarchive
                   preserve
                   register <pipeline> <in1> <in2>...
                   diagram <pipeline> <in1> <in2>...
                   diagrameditor <pipeline> <in1> <in2>...

      Options:
    """.stripIndent().trim() + '\n\n'
    
    static Map<String,BpipePlugin> plugins = [:]
    
    static CliBuilder runCli = new CliBuilder(usage: DEFAULT_HELP, posix: true)
          
    static CliBuilder stopCommandsCli = new CliBuilder(usage: "bpipe stopcommands\n", posix: true)
    
    static CliBuilder diagramCli = new CliBuilder(usage: "bpipe diagram [-e] [-f <format>] <pipeline> <input1> <input2> ...\n", posix: true)
    
    static CliBuilder registerCli = new CliBuilder(usage: "bpipe register [-e] [-f <format>] <pipeline> <input1> <input2> ...\n", posix: true)
    
    /**
     * Introduced to (attempt to) deal with obscure out-of-memory situations. On rare
     * occasions Bpipe shuts down with no message in the log files. The best theory that I
     * have about it is that it's a severe out-of-memory situation and that even the attempts
     * to write / flush the logs fail. To try and at least trace the problem, we track all
     * the "normal" exit paths by setting this flag to true, and that way we know the exit
     * is not "normal" in the shutdown handler and write directly to stderr.
     */
    static boolean normalShutdown = true
    
    final static String HOSTNAME = InetAddress.localHost.hostName
    
    public static final File PAUSE_FLAG_FILE = new File(".bpipe/pause")

    public static OptionAccessor opts = runCli.parse([])
    
    public static boolean touchMode = false
    
    public static boolean testMode = false
    
    public static boolean cleanupRequired = false
    
    @CompileStatic
    public static void main(String [] arguments) {
        
        List<String> args = arguments as List<String>
        
        if(!BPIPE_HOME || BPIPE_HOME.isEmpty()) {
            System.err.println "ERROR: The system property bpipe.home was not set. Please check that Bpipe was correctly started from its launch script."
            System.exit(1)
        }
        
        def db = new File(".bpipe")
        if(!db.exists())
            if(!db.mkdir())
                throw new RuntimeException("Bpipe was not able to make its database directory, .bpipe in the local folder.  Is this folder read-only?")
        
        String pid = resolvePID()
        
        Config.config.pid = pid
        
        Config.config.outputLogPath = ".bpipe/logs/${pid}.log"
        
        // println "Starting Runner at ${new Date()}..."
            
        // PID of shell that launched Bpipe
        String parentPid = System.getProperty("bpipe.pid")
        Config.config.parentPid = parentPid
        
        // Before we do anything else, add a shutdown hook so that termination of the process causes the job to 
        // to be removed from the user's folder
//        log.info "Adding shutdown hook"
        System.addShutdownHook(Runner.&shutdown)
        
        String mode = System.getProperty("bpipe.mode")
        
        
        if(mode == "agent")  {
           runAgent(args)
           return 
        }
        
        if(mode == "touch") {
           touchMode = true
           testMode = true
        }

        def parentLog = initializeLogging(pid)
        
        log.info "Initializing plugins ..."
        Config.initializePlugins()
        
        def cli 
        if(mode == "diagram")  {
            log.info("Mode is diagram")
            cli = configureDiagramCli()
            Config.config["mode"] = "diagram"
        }
        else
        if(mode == "preallocate")  {
            log.info("Mode is preallocate")
            Config.config["mode"] = mode
            new PreallocateCommand(args as List).run(System.out)
            exit(0) 
        }
        else
        if(mode == "archive")  {
            log.info("Mode is archive")
            Config.config["mode"] = mode
            new ArchiveCommand(args as List).run(System.out)
            exit(0) 
        }
        else
        if(mode == "autoarchive")  {
            log.info("Mode is autoarchive")
            Config.config["mode"] = mode
            new ArchiveCommand(['--delete'] + args.grep { String arg -> arg.startsWith('-') } + ['.bpipe.zip']).run(System.out)
            exit(0) 
        }
        else
        if(mode == "register")  {
            log.info("Mode is register")
            cli = registerCli
            Config.config["mode"] = "register"
        }
        else
        if(mode == "documentation")  {
            log.info("Mode is documentation")
            cli = diagramCli
            Config.config["mode"] = "documentation"
        }
        else 
        if(mode == "diagrameditor") {
            log.info("Mode is diagram editor")
            cli = diagramCli
            Config.config["mode"] = "diagrameditor"
        }
        else
        if(mode == "jobs") {
            log.info("Mode is jobs")
            Config.config["mode"] = mode
            new bpipe.cmd.JobsCommand(args as List).run(System.out)
            exit(0)
        } 
        else 
        if(mode == "cleanup") {
            runCleanup(args)
        }         
        else 
        if(mode == "query") {
            log.info("Showing dependency graph for " + args)
            new bpipe.cmd.QueryCommand(args as List).run(System.out)
            exit(0)
        }         
        else 
        if(mode == "install") {
            log.info("Installing tools [args=" + args + "]")
            new bpipe.cmd.InstallToolsCommand(args as List).run(System.out)
            exit(0)
        }        
        else
        if(mode == "preserve") {
            log.info("Preserving " + args)
            this.runPreserve(args)
            exit(0)
        } 
        else
        if(mode == "status") {
            log.info("Displaying status")
            this.runStatus(args)
            exit(0)
        } 
        else
        if(mode == "stats") {
            log.info("Displaying stats")
            Config.config["mode"] = mode
            new bpipe.cmd.StatsCommand(args as List).run(System.out)
            exit(0)
        } 
        else
        if(mode == "stopcommands") {
            log.info("Stopping running commands")
            cli = stopCommandsCli
            new Stop(args as List).run(System.out)
            exit(0)
        } 
        else {
            
            if(mode == "retry" || mode == "resume" || mode == "remake") {
                
               
                if(mode == "resume")
                    runningCommands = new CommandManager().getCurrentCommands()
                
                // remake is like retry, but we preserve the args to invalidate
                // their timestamps
                if(mode == "remake") {
                    log.info "Remaking $args"
                    Dependencies.theInstance.remakeFiles(args as List)
                    args = [] as List<String>
                    mode = "retry"
                }

                // Substitute arguments from prior command 
                // to re-run it
                def retryInfo = parseRetryArgs(args)
                args = retryInfo[1] as List<String>
                mode = retryInfo[0]
                
                Config.config["mode"] = mode
            }
            
            cli = runCli
            configureRunCli(cli)
            
            Config.plugins.each { name, plugin -> plugin.configureCli(cli) }
        }
        
        String versionInfo = "\nBpipe Version $version   Built on ${new Date(Long.parseLong(builddate))}\n"
        
        def opt = cli.parse(args)
        if(!opt) 
            exit(1)
        
        if(!opt.arguments()) {
            println versionInfo
            cli.usage()
            println "\n"
            exit(1)
        }
        
        // Note: configuration reading depends on the script, so this
        // needs to come first
        Config.config.script = opt.arguments()[0]
        
        Config.config.pguid = Utils.sha1(canonicalRunDirectory +'$'+Config.config.pid+'$'+Config.config.script) 

        log.info "=================== GUID=${Config.config.pguid} PID=$pid (${Config.config.pid}) =============="
        
        // add all user specified parameters to the binding
        if(opt['params']) {  // <-- note: ending with the 's' character the 'param' option, will force to return it as list of string
            log.info "Adding CLI parameters: ${opt['params']}"
            binding.addParams( (List)opt['params'] )
        }
        else {
            log.info "No CLI parameters specified"
        }
        
        if(opt['e']) {
            Config.config.environment = opt['e']
        }
        
        // read the configuration file, if available
        readUserConfig()
        
        opts = opt
        if(opts['v']) {
            Utils.configureVerboseLogging()
        }
        
        log.info "Loading user configuration using environment: " + Config.config.environment 
	 
        
        if(opts['d']) {
            Config.config.defaultOutputDirectory = opts['d']
        }
        
        if(opts['n']) {
            log.info "Maximum threads specified as ${opts['n']}"
            Config.config.maxThreads = Integer.parseInt((String)opts['n'])
            Config.config.customThreads = true
        }
        else 
        if(Config.userConfig.containsKey('concurrency')) { // If not specified by command line, look for concurrency in bpipe.config
            Config.config.maxThreads = Integer.parseInt(String.valueOf(Config.userConfig.concurrency))
            Config.config.customThreads = true
        }

        if(opts['m']) {
            log.info "Maximum memory specified as ${opts['m']}"
            try {
                Config.config.maxMemoryMB = parseMemoryOption((String)opts['m'])
            } 
            catch (Exception e) {
                System.err.println "\n$e.message\n"
                cli.usage()
                exit(1)
            }
        }
        
        if(opts['l']) {
            log.info "Resource limit specified as ${opts['l']}"
            def limit = ((String)opts['l']).split("=")
            if(limit.size()!=2) {
                System.err.println "\nBad format for limit ${opts['l']} - expect format <name>=<value>\n"
                cli.usage()
                exit(1)
            }
            Concurrency.theInstance.setLimit((String)limit[0],limit[1] as Integer)
        }

        if(opts['r']) {
            Config.config.report = true
			def reportStats = new ReportStatisticsListener("index",(String)(opts['f']?:"index.html"))
        }
        else
        if(opts['R']) {
            log.info "Creating report ${opts['R']}"
            def reportStats = new ReportStatisticsListener((String)opts['R'], (String)(opts['f']?:(String)opts['R']+".html"))
        }

        if(opts['b']) {
            Config.config.branchFilter = ((String)opts['b']).split(",").collect { it.trim() }
            log.info "Set branch filter = ${Config.config.branchFilter}"
        }
        
        if(((ConfigObject)Config.userConfig.worx)?.enable) {
            new WorxEventListener().start()
        }
        
        String loadArgs = opt['source'] ? '\n'+((String)opt['source']).split(',').collect { "load '$it'\n"}.join("") : ""
        
        def pipelineArgs = null
        String pipelineSrc
        if(mode == "execute") {
            pipelineSrc = Pipeline.PIPELINE_IMPORTS + loadArgs + ' Bpipe.run { ' + opt.arguments()[0] + '}'
        }
        else {
            pipelineSrc = loadPipelineSrc(cli, opt.arguments()[0], loadArgs)
        }
        
        if(opt.arguments().size() > 1)
            pipelineArgs = opt.arguments()[1..-1]
                
        log.info "Loading tool database ... "
        def initThreads = [
                           { ToolDatabase.theInstance.init(Config.userConfig) }, 
                           { /* Add event listeners that come directly from configuration */ EventManager.theInstance.configure(Config.userConfig) },
                           { Concurrency.theInstance.initFromConfig() },
                           { if(!opts['t']) { NotificationManager.theInstance.configure(Config.userConfig); configureReportsFromUserConfig() } },
                           { Dependencies.theInstance.preloadOutputGraph() }
                           ].collect{new Thread(it)}
        initThreads*.start()

        // If we got this far and are not in test mode, then it's time to 
        // make the logs stick around
        if(!opts['t']) {
            Config.config.eraseLogsOnExit = false
            appendCommandToHistoryFile(mode, args, pid)
            
            // If we were paused, remove that
            PAUSE_FLAG_FILE.delete()
        }
        
        if(opts['u']) {
            Config.config.breakAt = ((String)opts['u']).split(",")
        }

        def gcl = new GroovyClassLoader()
        
        if('parameters' in Config.userConfig) {
            Config.userConfig.parameters.collect { Map.Entry<String,Object> e ->
                if(!binding.parameters.contains(e.key))
                    binding.setParam(e.key,e.value)
            }
        }
       
        if(opts['L']) { 
            Config.region = new RegionValue((String)opts['L'])
        }
        
        if(opts['t'])
            testMode = true

        initThreads*.join(20000)
        
      
        // create the pipeline script instance and set the binding obj
        log.info "Parsing script ... "
        
        loadExternalLibs()
        
        Script script = gcl.parseClass(pipelineSrc).newInstance()
        script.setBinding(binding)
        // set the pipeline arguments
        script.setProperty("args", pipelineArgs);

        // RUN it
        try {
            Exception fatalError = NotificationManager.theInstance.fatalError
            if(fatalError) {
                throw new bpipe.PipelineError('One or more of your required notification channels could not be configured. Please check your configuration to resolve the error below', fatalError)
            }
 
            checkDirtyFiles()
            
            DirtyFileManager.theInstance.initCleanupState()
  

            log.info "Run ... "
            normalShutdown = false
            script.run()
            normalShutdown=true
            ExecutorPool.shutdownAll()
            Poller.theInstance.executor.shutdown()
            EventManager.theInstance.signal(PipelineEvent.SHUTDOWN, "Shutting down process $pid")
            NotificationManager.theInstance.shutdown()
        }
        catch(MissingPropertyException e)  {
            if(e.type?.name?.startsWith("script")) {
                // Handle this as a user error in defining their script
                // print a nicer error message than what comes out of groovy by default
                handleMissingPropertyFromPipelineScript(e)
                EventManager.theInstance.signal(PipelineEvent.SHUTDOWN, "Shutting down process $pid")
		        exit(1)
            }
            else {
                reportExceptionToUser(e)
                EventManager.theInstance.signal(PipelineEvent.SHUTDOWN, "Shutting down process $pid")
		        exit(1)
            }
        }
        catch(Throwable e) {
            reportExceptionToUser(e)
            EventManager.theInstance.signal(PipelineEvent.SHUTDOWN, "Shutting down process $pid")
	        exit(1)
        }
        
        if(opts['a']) {
            new ArchiveCommand(["-d", (String)opts['a']]).run(System.out)
        }
   }
   
    static void configureRunCli(CliBuilder cli) {
        cli.width = 100
        cli.with {
            h longOpt:'help', 'usage information'
            d longOpt:'dir', 'output directory', args:1
            a longOpt: 'autoarchive', 'clean up all internal files after run into given archive', args:1
            t longOpt:'test', 'test mode'
            f longOpt: 'filename', 'output file name of report', args:1
            r longOpt:'report', 'generate an HTML report / documentation for pipeline'
            'R' longOpt:'report', 'generate report using named template', args: 1
            n longOpt:'threads', 'maximum threads', args:1
            m longOpt:'memory', 'maximum memory in MB, or specified as <n>GB or <n>MB', args:1
            l longOpt:'resource', 'place limit on named resource', args:1, argName: 'resource=value'
            v longOpt:'verbose', 'print internal logging to standard error'
            y longOpt:'yes', 'answer yes to any prompts or questions'
            u longOpt:'until', 'run until stage given',args:1
            p longOpt: 'param', 'defines a pipeline parameter, or file of parameters via @<file>', args: 1, argName: 'param=value', valueSeparator: ',' as char
            b longOpt: 'branch', 'Comma separated list of branches to limit execution to', args:1
            s longOpt: 'source', 'Load the given pipeline file(s) before running / executing', args: 1
            e longOpt: 'env', 'Environment to select from alternate configurations in bpipe.config', args: 1
            'L' longOpt: 'interval', 'the default genomic interval to execute pipeline for (samtools format)',args: 1
        }
    }
    
    static void exit(int code) {
        normalShutdown = true
        System.exit(code)
    }

   synchronized static reportExceptionToUser(Throwable e) {
        log.severe "Reporting exception to user: "
        log.log(Level.SEVERE, "Reporting exception to user", e)
        
        String msg = e.message?:"Unknown Error (Null message)"

        System.err.println(" Bpipe Error ".center(Config.config.columns,"="))
        
        System.err.println("\nAn error occurred executing your pipeline:\n\n${msg.center(Config.config.columns,' ')}\n")
        
        if(!(e instanceof ValueMissingError)) {
            System.err.println("\nPlease see the details below for more information.\n")
            System.err.println(" Error Details ".center(Config.config.columns, "="))
            System.err.println()
            Throwable sanitized = Utils.sanitizeForDisplay(e)
            StringWriter sw = new StringWriter()
            sanitized.printStackTrace(new PrintWriter(sw))
            String stackTrace = sw.toString()
            Pipeline.scriptNames.each { fileName, internalName -> stackTrace = stackTrace.replaceAll(internalName+'(.groovy)?', fileName) }
            System.err.println(stackTrace)
            System.err.println()
            System.err.println "=" * Config.config.columns
            System.err.println("\nMore details about why this error occurred may be available in the full log file .bpipe/bpipe.log\n")
        }
    }

    private static handleMissingPropertyFromPipelineScript(MissingPropertyException e) {
        
        // Log the full stack trace
        log.log(Level.SEVERE, "Missing variable failure", e)
        def s = new StringWriter()
        e.printStackTrace(new PrintWriter(s))
        log.severe(s.toString())
        
        // A bit of a hack: the parsed script ends up with a class name like script123243242...
        // so search for that in the stack trace to find the line number
        int lineNumber = e.stackTrace.find { it.className ==~ /script[0-9]{1,}/ }?.lineNumber ?: -1

        println """
                    Pipeline Failed!

                    A variable referred to in your script on line ${lineNumber}, '$e.property' was not defined.  
                    
                    Please check that all pipeline stages or other variables you have referenced by this name are defined.
                    """.stripIndent()
    }

    /**
     * Set up logging for the Bpipe diagnostic log
     */
    public static Logger initializeLogging(String pid, String logFileName="bpipe") {
        
        def parentLog = log.getParent()
        parentLog.getHandlers().each { parentLog.removeHandler(it) }
        
        File logFile = new File(".bpipe/${logFileName}.log")
        if(logFile.exists()) {
            logFile.delete()
        }
        
        // The current log file
        FileHandler fh = new FileHandler(logFile.path)
        fh.setFormatter(new BpipeLogFormatter())
        parentLog.addHandler(fh)

        try {
            // Another log file for history
            new File(".bpipe/logs").mkdirs()
            Handler pidLog = pid == "tests" ? new ConsoleHandler() : new FileHandler(".bpipe/logs/${pid}.${logFileName}.log")
            pidLog.setFormatter(new BpipeLogFormatter())
            parentLog.addHandler(pidLog)
        }
        catch(Exception e) {
            log.info "Error initializing logging: " + e
        }
    
		Properties p = System.properties
        log.info("Starting")
        log.info("OS: " + p['os.name'] + " (" + p['os.version'] + ") Java: " + p['java.version'] + " Vendor: " + p["java.vendor"] )
		
        return parentLog
    }
    
   
    /**
     * Try to determine the process id of this Java process.
     * Because the PID is read from a file that is created after
     * starting of the Java process there is a race condition and thus
     * this call *may* wait some (small) time for the file to appear.
     * <p>
     * TODO: it would probably be possible to remove this now and pass the PID
     *       as a system property
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
                    println "ERROR: Bpipe was unable to read its startup PID file from $pidFile.absolutePath"
                    println "ERROR: This may indicate you are in a read-only directory or one to which you do not have full permissions"
                    exit(1)
                }

                // Spin a short time waiting
                Thread.sleep(20)
                ++count
            }
        }
        return pid
    }
    
    static void loadExternalLibs() {
       if(!Config.userConfig.containsKey("libs")) 
           return
           
        def jars = Config.userConfig.libs
        if(Config.userConfig.libs instanceof String) {
            jars = jars.tokenize(":") 
        }
            
        List resolvedLibs = []
        for(String jar in jars) {
            resolvedLibs << loadExternalLib(jar)
        }
        Config.userConfig.libs = resolvedLibs
    }
    
    /**
     * @param libPath   libPath to search for in predefined locations and load
     * @return          actual resolved library
     */
    static String loadExternalLib(String libPath) {
        try {
            File f = new File(libPath)
            if(!f.exists()) {
                // Attempt to resolve the file relative to the main script location if
                // it cannot be resolved directly
                File relativeToScript = new File(new File(Config.config.script).canonicalFile.parentFile, libPath)
                
                log.info "Library $f does not exist: checking under path $relativeToScript"
                if(!relativeToScript.exists()) {
                    log.info "Path relative to script does not exist: library $libPath may be ignored"
                }
                f = relativeToScript
            }
            log.info "Attempting to add to classpath: $f.absolutePath"
            Runner.class.classLoader.rootLoader.addURL(f.toURL())
            return f.path
        }
        catch(Exception e) {
            log.severe("Failed to add jar $libPath to classpath: " + e)
            System.err.println("WARN: Error adding jar $libPath to classpath: " + e.getMessage())
        }                    
    }
                    
    /**
     * Loads a pipeline file from the source path, checks it in some simple ways and 
     * augments it with some precursor declarations and imports to bring things into
     * the default scope.
     * 
     * @param cli
     * @param srcFilePath
     * @return
     */
    static String loadPipelineSrc(def cli, def srcFilePath, String preamble) {
        File pipelineFile = new File(srcFilePath)
        if(!pipelineFile.exists()) {
            println "\nCould not understand command $pipelineFile or find it as a file\n"
            cli.usage()
            println "\n"
            exit(1)
        }
        
        String scriptText = pipelineFile.text
        if(scriptText.startsWith('#!')) {
           scriptText = scriptText.substring(scriptText.indexOf('\n')+1) 
        }
        
        // Note that it is important to keep this on a single line because 
        // we want any errors in parsing the script to report the correct line number
        // matching what the user sees in their script
        String pipelineSrc = Pipeline.PIPELINE_IMPORTS + 
                    "bpipe.Pipeline.scriptNames['$pipelineFile.name']=this.class.name;" +
                    preamble + 
                    scriptText
                     
        if(scriptText.indexOf("return null") >= 0) {
            println """
                       ================================================================================================================
                       | WARNING: since 0.9.4 using 'return null' in pipeline stages is incorrect. Please use 'forward input' instead.|
                       ================================================================================================================
            """.stripIndent()
        }
        return pipelineSrc
    }
    
    /**
     * Centralised directory where in-progress jobs are linked
     */
    static final File CENTRAL_JOB_DIR = new File(System.getProperty("user.home") + "/.bpipedb/jobs/")
    
    /**
     * Centralised directory where completed job links are linked
     */
    static final File COMPLETED_DIR = new File(System.getProperty("user.home") + "/.bpipedb/completed")
    
    /**
     * The local directory where job information is stored
     */
    static final File LOCAL_JOB_DIR = new File(".bpipe/jobs")
    
    /**
     * Bpipe home, set as system property by Bpipe runner script prior to launching
     */
    static String BPIPE_HOME = System.getProperty("bpipe.home")?.asType(File)?.absolutePath?:new File(".").absolutePath
    
    /**
     * Perform essential cleanup when Bpipe process ends.
     * <p>
     * Note: This function is added as a shutdown hook. Therefore it executes in the very
     *       limited context and constraints applied to Java shutdown hooks.
     */
    @CompileStatic
    static void shutdown() {
        
        String pid = Config.config.pid
        String parentPid = Config.config.parentPid
        
        ExecutorPool.shutdownAll()
        
        Poller.theInstance.executor.shutdown()
            
        // The normalShutdown flag is set to false by default, and only set to true
        // when Bpipe executes through one of the normal / expected paths. In this 
        // way we have a chance to detect when Bpipe dies through an abnormal 
        // mechanism.
        if(!normalShutdown) {
            if(new File(".bpipe/stopped/$pid").exists())
                System.err.println "MSG: Bpipe stopped by stop command: " + new Date()
            else
                System.err.println "ERROR: Abnormal termination - check bpipe and operating system has enough memory!"
            System.err.flush()
        }
            
        def home = System.getProperty("user.home")
        File jobFile = new File(CENTRAL_JOB_DIR, "$pid")
        if(jobFile.exists()) {
            
            if(!COMPLETED_DIR.exists())
                COMPLETED_DIR.mkdirs()
                
            File completedFile = new File(COMPLETED_DIR, pid)
            if(completedFile.exists())
                completedFile.delete()
            
            log.info("Deleting job file $jobFile")
            if(!jobFile.renameTo(completedFile)) {
                log.warning("Unable to move job file for job $pid")
                println("WARN: Unable to move job file for job $pid")
            }
        }
            
        if(Config.config.eraseLogsOnExit && parentPid != null) {
            new File(".bpipe/logs/${parentPid}.erase.log").text=""
        }
            
        if(cleanupRequired) {
            DirtyFileManager.theInstance.cleanupDirtyFiles()
        }
    }
    
    static void checkDirtyFiles() {
        def dirtyFiles = DirtyFileManager
                            .getTheInstance()
                            .getUncleanFilePaths()
                            .grep { new File(it).exists() }
        if(dirtyFiles) {
            println "=" * Config.config.columns
            println ""
            println "WARNING: dirty files may be present from a previous run. Please check these paths:"
            println ""
            println dirtyFiles.collect { '\t' + it + '\n' }.join('')
            
            cleanupRequired = false
            throw new PipelineError("Dirty files were found. Please inspect and remove contents of " + CommandManager.UNCLEAN_FILE_DIR + " after being sure the files are OK")
        }
    }
   
    /**
     * Execute the 'cleanup' command
     * @param args
     */
    static void runCleanup(def args) {
        def cli = new CliBuilder(usage: "bpipe cleanup [-y]\n", posix: true)
        cli.with {
            y longOpt: 'yes', 'answer yes to any prompts or questions'
        }
        def opt = cli.parse(args)
        
        readUserConfig()
       
        if(opt.y) {
            Config.userConfig.prompts.handler = { msg -> return "y"}
        }
            
        Dependencies.instance.cleanup(opt.arguments())
        exit(0)
    }
    
    /**
     * Execute the 'preserve' command
     * @param args
     */
    static void runPreserve(def args) {
        def cli = new CliBuilder(usage: "bpipe preserve <file1> [<file2>] ...", posix:true)
        def opt = cli.parse(args)
        if(!opt.arguments()) {
            println ""
            cli.usage()
            exit(1)
        }
        Dependencies.instance.preserve(opt.arguments())
        exit(0)
    }
    
    static void runStatus(def args) {
        new StatusCommand().execute(args)
    }
    
    static void runAgent(def args) {
        bpipe.agent.AgentRunner.main(args)
    }
    
    /**
     * Parse the arguments from the retry command to see if the user has 
     * specified a job or test mode, then find the relevant command
     * from history and return it
     * 
     * @param args  arguments passed to retry 
     * @return  a list with 2 elements, [ <command>, <arguments> ]
     */
    static List parseRetryArgs(args) {
        
        // They come in as an array, but there are some things that don't work
        // on arrays in groovy ... (native java list operations)
        args = args as List
        
        String notFoundMsg = """
            
            No previous Bpipe command seems to have been run in this directory.

            """.stripIndent()
            
            
        String usageMsg = """
           Usage: bpipe retry [jobid] [test]
        """.stripIndent()
        
        def historyFile = new File(".bpipe/history")
        if(!historyFile.exists()) {
            System.err.println(notFoundMsg);
            exit(1)
        }
        
        def historyLines = historyFile.readLines()
        if(!historyLines) {
            System.err.println(notFoundMsg);
            exit(1)
        }
        
        String commandLine = null
        boolean testMode = false
        if(!args) {
            commandLine = historyLines[-1]
        }
        else {
            if(args[0] == "test") {
                testMode = true
                args.remove(0)
            }
            
            if(args) {
                if(args[0].isInteger()) {
                  commandLine = historyLines.reverse().find { it.startsWith(args[0] + "\t") }
                }
                else {
                    System.err.println "\nJob ID could not be parsed as integer\n" + usageMsg
                    exit(1)
                }
                
                if(commandLine == null) {
                    System.err.println "\nCould not find a previous Bpipe run with id ${args[0]}. Please use 'bpipe history' to check for valid run ids.\n" + usageMsg
                    exit(1)
                }
            }
            else {
                commandLine = historyLines[-1]
            }
        }
        
        // Trim off the job id
        if(commandLine.matches("^[0-9]{1,6}.*\$"))
            commandLine = commandLine.substring(commandLine.indexOf("\t")+1)
        
        // Remove leading "bpipe" and "run" arguments
        def parsed = (commandLine =~ /bpipe ([a-z]*) (.*)$/)
        if(!parsed)
            throw new PipelineError("Internal error: failed to understand format of command from history:\n\n$commandLine\n")
            
        args = Utils.splitShellArgs(parsed[0][2]) 
        def command = parsed[0][1]
        
        return [ command,  testMode ? ["-t"] + args : args]
    }
    
    /**
     * Add a line to the current history file with information about
     * this run. The current command is stored in .bpipe/lastcmd
     */
    static void appendCommandToHistoryFile(String mode, args, String pid) {
        
        File history = new File(".bpipe/history")
        if(!history.exists())
            history.text = ""
            
        if(mode == null)
            mode = "run"
            
        history.withWriterAppend { it << [pid, "bpipe $mode " + args.collect { it.contains(" ") ? "'$it'" : it }.join(" ")].join("\t") + "\n" }
    }
    
    static void configureReportsFromUserConfig() {
        
        if(!Config.userConfig.containsKey("reports"))
            return
        
        Config.userConfig.reports.each { reportName, reportConfig -> 
           log.info "Found report $reportName configured with config $reportConfig" 
           new ReportStatisticsListener(reportName, reportConfig.fileName?:reportName + ".html", reportConfig.dir?:"doc", reportConfig.notification?:false)
        }
    }
    
    /**
     * Parse the memory option and return the resulting memory amount in MB.
     * <p>
     * The value can be specified either as a plain integer (interpreted as MB) or as an integer followed by
     * either MB or GB. For example:
     * <li>4GB
     * <li>4gb
     * <li>4  (means 4MB)
     * <li>4MB 
     */
    static int parseMemoryOption(String memoryValue) {
        
        if(memoryValue.isInteger())
            return memoryValue.toInteger()
            
        // Separate the memory unit from the number
        def memory = (memoryValue =~ /([0-9]*)([a-zA-Z]{2})/)
        if(!memory)
            throw new IllegalArgumentException("Memory value '$memoryValue' couldn't be parsed. Please specify in the form <n>MB or <n>GB")
        
        String amount = memory[0][1]
        String unit = memory[0][2].toUpperCase()
        
        if(unit == "MB")
            return amount.toInteger()
        
        if(unit == "GB")
            return amount.toInteger() * 1000
    }
    
    static void readUserConfig() {
        log.info "Reading user config ... "
        Utils.time ("Read user config") {
            try {
                Config.readUserConfig()
            }
            catch( Exception e ) {
                def cause = e.getCause() ?: e
                println("\nError parsing 'bpipe.config' file. Cause: ${cause.getMessage() ?: cause}\n")
                reportExceptionToUser(e)
                exit(1)
            }
        }
    }

    static boolean isPaused() {
        if(PAUSE_FLAG_FILE.exists()) {
            throw new PipelinePausedException()
        }
    }
    
    static CliBuilder configureDiagramCli() {
        CliBuilder cli = diagramCli
        cli.with {
            f "Set output format to 'png' or 'svg'", args:1
        }
    }
}
