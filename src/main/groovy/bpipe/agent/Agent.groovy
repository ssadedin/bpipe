package bpipe.agent

import java.nio.file.Files;
import java.util.concurrent.Semaphore
import java.util.logging.Level

import bpipe.ChecksCommand
import bpipe.Config
import bpipe.Runner
import bpipe.Utils
import bpipe.cmd.BpipeCommand
import bpipe.cmd.ClosurePipelineCommand
import bpipe.cmd.LogCommand;
import bpipe.cmd.RunPipelineCommand
import bpipe.cmd.Stop;
import bpipe.worx.HttpWorxConnection
import bpipe.worx.WorxConnection
import groovy.util.logging.Log;
import groovy.json.JsonOutput

@Log
abstract class Agent extends TimerTask {
    
    public static Map COMMANDS = [
        "retry" : { dir, args, writer -> bpipe(dir, ['retry'], writer)},
        "stop" : Stop,
        "log" : LogCommand,
        "run" : RunPipelineCommand,
        "checks" : ChecksCommand
    ]
    
    Set knownPipelines = new HashSet()
    
    Map<String, PipelineInfo> pipelines = [:]
    
    String name = bpipe.Runner.HOSTNAME
    
//    String id = Utils.sha1(String.valueOf(System.currentTimeMillis()) + new Random().nextInt())
    
    String id = Utils.sha1(Runner.BPIPE_HOME + '::' + name + '::' + System.properties['user.name'])
    
    abstract WorxConnection createConnection() 
    
    boolean stopRequested = false
    
    boolean singleShot = false
    
    Semaphore concurrency = null
    
    abstract void run()
    
    /**
     * Read the user's home directory to find running or completed pipelines
     */
    void scanForPipelines() {
        
        [Runner.CENTRAL_JOB_DIR, Runner.COMPLETED_DIR]*.eachFileMatch(~/[0-9]{1,10}/) { File f ->
            this.registerPotentialJob(f)
        }
    }
    
    void registerPotentialJob(File potentialJobFile) {
        
        if(!Files.isSymbolicLink(potentialJobFile.toPath()))
            return
                
        if(potentialJobFile.absolutePath in knownPipelines) 
            return
                
        registerNewJob(potentialJobFile)
    }
    
    
    void registerNewJob(File jobLink) {
        
        knownPipelines.add(jobLink.absolutePath)
            
        // This converts from symbolic link to real file path
        File realFile = jobLink.canonicalFile
            
        if(!realFile.exists()) {
            log.info "Job path $jobLink.absolutePath no longer exists; deleting and ignoring"
            jobLink.delete() // note: removes symbolic link, not target
            return
        }
            
        // It's a new pipeline that we did not know about
        log.info "Found pipeline $jobLink.name"
            
        PipelineInfo pipelineInfo = new PipelineInfo(realFile)
            
        log.info "Pipeline $jobLink.name in directory $pipelineInfo.path, pguid=$pipelineInfo.pguid"
        if(pipelineInfo.pguid)
            pipelines[pipelineInfo.pguid] = pipelineInfo
        else
            log.info("Pipeline $realFile.path appears to be a legacy pipeline - agent is ignoring this pipeline")
        
    }
    
    void processCommand(Map commandAttributes) {
        try {
            BpipeCommand command = createCommandFromAttributes(commandAttributes)
            command.dir = commandAttributes.directory ?: pipelines[commandAttributes.run.id].path
            AgentCommandRunner runner = new AgentCommandRunner(createConnection(), commandAttributes.id, command)
            runner.concurrency = this.concurrency
            new Thread(runner).start()
        }
        catch(Exception e) {
            // report the error upstream if we can
            AgentCommandRunner runner = new AgentCommandRunner(createConnection(), commandAttributes.id, e)
            runner.concurrency = this.concurrency
            new Thread(runner).start() 
        }
    }
    
    /**
     * Create a command by introspecting its class and arguments from
     * the given attributes.
     * 
     * @param commandAttributes
     * @return
     */
    private BpipeCommand createCommandFromAttributes(Map commandAttributes) {
        
        def args = commandAttributes.arguments 
        if((args != null) && (args instanceof String) && (args!="")) {
            log.info("Parse args as JSON: " + args)
            args = new groovy.json.JsonSlurper().parseText(args)
        }
        else 
        if(args == "")
            args = []
        
        BpipeCommand command
        
        Object commandObj = COMMANDS[commandAttributes.command]
        if(commandObj instanceof Closure) {
            return new ClosurePipelineCommand(commandObj, args)
        }
        else {
            Class commandClass = commandObj
            if(commandClass == null) {
                throw new Exception("Unknown command $commandAttributes returned by agent poll")
            }
            command = commandClass.newInstance() // todo: should really be passing args to constructor
        }
        
        log.info "Command args are: " + args
        if(args != null)
            command.args = args
            
        return command
    }
    
    static void bpipe(String dir, List bpipeArgs, Writer out) {
        if(dir == null) 
            throw new IllegalArgumentException("Directory parameter not set. Directory for command to run in must be specified")
        
        File dirFile = new File(dir).absoluteFile
        if(!dirFile.parentFile.exists())
            throw new IllegalArgumentException("Directory supplied $dir is not in an existing path. The directory parent must already exist.")
        
        log.info "Args are: " + bpipeArgs
        List<String> cmd = [ bpipe.Runner.BPIPE_HOME + "/bin/bpipe" ] 
        cmd.addAll(bpipeArgs)
        log.info "Running command : " + cmd;
        Map result = Utils.executeCommand(cmd, out:out, err: out) {
            directory(dirFile)
        }
    }
}
