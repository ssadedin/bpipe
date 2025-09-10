/*
 * Copyright (c) 2018 MCRI, authors
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
package bpipe.agent

import java.nio.file.Files;
import java.nio.file.Paths
import java.util.concurrent.Semaphore
import java.util.logging.Level

import bpipe.ChecksCommand
import bpipe.Config
import bpipe.ExecutedProcess
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
import groovy.transform.CompileStatic

/**
 * Listens or monitors for job requests (bpipe commands) and dispatches them for execution,
 * facilitating return of responses to the caller.
 * 
 * @author Simon Sadedin
 */
@Log
abstract class Agent extends TimerTask {
    
    /**
     * Mapping of each command that may be passed in an incoming agent request to a handler
     * for that command type. These are either objects extending BpipeCommand or Closures 
     * that are executed directly.
     */
    public Map COMMANDS = [
        "retry" : { dir, args, writer -> bpipe(bpipeHome, dir, ['retry'], writer)},
        "stop" : Stop,
        "log" : LogCommand,
        "run" : RunPipelineCommand,
        "checks" : ChecksCommand,
        "ping" :  { dir, args, writer -> println("Ping received") }
    ]
    
    Set knownPipelines = new HashSet()
    
    Map<String, PipelineInfo> pipelines = [:]
    
    String name = bpipe.Runner.HOSTNAME
    
    String environment = null
    
//    String id = Utils.sha1(String.valueOf(System.currentTimeMillis()) + new Random().nextInt())
    
    String id = Utils.sha1(Runner.BPIPE_HOME + '::' + name + '::' + System.properties['user.name'])
    
    ConfigObject allowed = null
    
    String outputMode = 'both'
    
    Agent(ConfigObject cfg) {
        if(cfg?.containsKey('allow'))
            allowed = cfg?.allow
        this.outputMode = cfg?.getOrDefault('outputMode', null)?:'both'
    }
    
    abstract WorxConnection createConnection() 
    
    boolean stopRequested = false
    
    boolean singleShot = false
    
    Semaphore concurrency = null
    
    String bpipeHome = bpipe.Runner.BPIPE_HOME 
    
    int executed = 0
    
    int errors = 0
    
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
    
    @CompileStatic
    AgentCommandRunner processCommand(Map<String,Object> commandAttributes, Closure onRun = null) {
        try {
            ++this.executed
            BpipeCommand command = createCommandFromAttributes(commandAttributes)
            command.bpipeHome = this.bpipeHome
            
            command.dir = commandAttributes.directory
            if(!command.dir) {
                command.dir = ((PipelineInfo)pipelines[((Map)(commandAttributes.run))?.id])?.path
            }

            validateCommand(command)
            AgentCommandRunner runner = new AgentCommandRunner(createConnection(), (Long)commandAttributes.id, command, outputMode, onRun)
            runner.concurrency = this.concurrency
            runner.environment = this.environment
            new Thread(runner).start()
            return runner
        }
        catch(Exception e) {
            // report the error upstream if we can
            AgentCommandRunner runner = new AgentCommandRunner(createConnection(), (Long)commandAttributes.id, e, outputMode, onRun)
            runner.concurrency = this.concurrency
            runner.environment = this.environment
            ++this.errors
            new Thread(runner).start() 
            return null
        }
    }
    
    void validateCommand(BpipeCommand command) {
        if(!allowed)
            return
            
        if(!(command instanceof RunPipelineCommand)) {
            log.info "Command $command.id is not a run pipeline command: allowing"
            return
        }
        
        RunPipelineCommand cmd = (RunPipelineCommand)command
            
        // Check pipelines
        String pipeline = cmd.getPipelineFile()
        if(allowed.containsKey('pipelines')) {
            if(!Config.listValue(allowed, 'pipelines').contains(pipeline)) 
                throw new IllegalArgumentException("Pipeline $pipeline is not allowed in configuration of agent")
        }
            
        // Check directories
        if(allowed.containsKey('directories')) {
            def commandDirPath = Paths.get(cmd.dir)
            boolean isAllowed = Config.listValue(allowed, 'directories').any { allowedDir ->
                cmd.dir == allowedDir
                return commandDirPath.startsWith(new File(allowedDir).toPath())
            }
            if(!isAllowed)
                throw new IllegalArgumentException("Directory $cmd.dir is not allowed in configuration of agent")
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
    
    static void bpipe(String home, String dir, List bpipeArgs, Writer out) {
        if(dir == null) 
            throw new IllegalArgumentException("Directory parameter not set. Directory for command to run in must be specified")
        
        File dirFile = new File(dir).absoluteFile
        if(!dirFile.parentFile.exists())
            throw new IllegalArgumentException("Directory supplied $dir is not in an existing path. The directory parent must already exist.")
        
        log.info "Args are: " + bpipeArgs
        List<String> cmd = [ home + "/bin/bpipe" ] 
        cmd.addAll(bpipeArgs)
        log.info "Running command : " + cmd;
        ExecutedProcess result = Utils.executeCommand(cmd, out:out, err: out) {
            directory(dirFile)
        }
    }
}
