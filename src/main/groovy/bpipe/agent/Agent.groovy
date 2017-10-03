package bpipe.agent

import java.nio.file.Files;
import java.util.logging.Level

import bpipe.ChecksCommand
import bpipe.Config
import bpipe.Runner
import bpipe.Utils
import bpipe.cmd.BpipeCommand
import bpipe.cmd.LogCommand;
import bpipe.cmd.RunPipelineCommand
import bpipe.cmd.Stop;
import bpipe.worx.WorxConnection
import groovy.util.logging.Log;;
import groovy.json.JsonOutput

@Log
class Agent extends TimerTask {
    
    public static Map COMMANDS = [
        "stop" : Stop,
        "log" : LogCommand,
        "run" : RunPipelineCommand,
        "checks" : ChecksCommand
    ]
    
    WorxConnection worx = new WorxConnection()
    
    Set knownPipelines = new HashSet()
    
    Map<String, PipelineInfo> pipelines = [:]
    
    String name = InetAddress.localHost.hostName
    
//    String id = Utils.sha1(String.valueOf(System.currentTimeMillis()) + new Random().nextInt())
    
    String id = Utils.sha1(Runner.BPIPE_HOME + '::' + name + '::' + System.properties['user.name'])
    
    void run() {
        try {
            scanForPipelines()

            pollForCommands()
        } 
        catch (Throwable e) {
            log.severe "Failed to scan for pipelines or poll remote listener: "
            def s = new StringWriter()
            e.printStackTrace(new PrintWriter(s))
            log.severe s.toString()
            
            if(worx) {
                worx.close()
                worx = new WorxConnection()
            }
        }
    }
    
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
    
    
    void pollForCommands() {
        Map details = [
            agent: [id: this.id, name: this.name],
            pguids: this.pipelines.keySet() as List
        ]
        
        worx.sendJson("/commands", JsonOutput.toJson(details))
        Map result = worx.readResponse()
        
        if(log.isLoggable(Level.FINE))
            log.fine "Result = " + result
            
        if(result == null)
            return
        
        if(result.commands) {
            // Each command is a map, indexed by pguid mapping to a list of Command objects
            // which are serialised as Maps
            result.commands.each { Map commandAttributes -> 
                try {
                    BpipeCommand command = createCommandFromAttributes(commandAttributes)
                    command.dir = commandAttributes.directory ?: pipelines[commandAttributes.run.id].path
                    AgentCommandRunner runner = new AgentCommandRunner(new WorxConnection(), commandAttributes.id, command)
                    new Thread(runner).start()
                }
                catch(Exception e) {
                    AgentCommandRunner runner = new AgentCommandRunner(new WorxConnection(), commandAttributes.id, e)
                    new Thread(runner).start()
                }
            }
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
        Class commandClass = COMMANDS[commandAttributes.command]
        if(commandClass == null) {
            throw new Exception("Unknown command $commandAttributes returned by agent poll")
        }

        BpipeCommand command = commandClass.newInstance()
        def args = commandAttributes.arguments 
        if((args != null) && args instanceof String) 
            args = new groovy.json.JsonSlurper().parseText(args)
            
        command.args = args
        return command
    }
}
