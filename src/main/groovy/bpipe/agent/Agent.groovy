package bpipe.agent

import java.nio.file.Files;

import bpipe.Config
import bpipe.Runner
import bpipe.cmd.BpipeCommand
import bpipe.cmd.LogCommand;
import bpipe.cmd.Stop;
import bpipe.worx.WorxConnection
import groovy.util.logging.Log;;
import groovy.json.JsonOutput

@Log
class Agent extends TimerTask {
    
    public static Map COMMANDS = [
        "stop" : Stop,
        "log" : LogCommand
    ]
    
    WorxConnection worx = new WorxConnection()
    
    Set knownPipelines = new HashSet()
    
    Map<String, PipelineInfo> pipelines = [:]
    
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
            if(!Files.isSymbolicLink(f.toPath()))
                return
                
            if(f.absolutePath in knownPipelines) 
                return
                
            knownPipelines.add(f.absolutePath)
            
            // This converts from symbolic link to real file path
            File realFile = f.canonicalFile
            
            if(!realFile.exists()) {
                log.info "Job path $f.absolutePath no longer exists; deleting and ignoring"
                f.delete() // note: removes symbolic link, not target
                return
            }
            
            // It's a new pipeline that we did not know about
            log.info "Found pipeline $f.name"
            
            PipelineInfo pipelineInfo = new PipelineInfo(realFile)
            
            log.info "Pipeline $f.name in directory $pipelineInfo.path, pguid=$pipelineInfo.pguid"
            if(pipelineInfo.pguid)
                pipelines[pipelineInfo.pguid] = pipelineInfo
            else
                log.info("Pipeline $realFile.path appears to be a legacy pipeline - agent is ignoring this pipeline")
        }
    }
    
    
    void pollForCommands() {
        Map details = [
            pguids: this.pipelines.keySet() as List
        ]
        
        worx.sendJson("/commands", JsonOutput.toJson(details))
        Map result = worx.readResponse()
        println "Response is: " + result
        
        if(result.commands) {
            // Each command is a map, indexed by pguid mapping to a list of Command objects
            // which are serialised as Maps
            result.commands.each { Map command -> 
                PipelineInfo pi =  this.pipelines[command.run.id]
                if(!pi) {
                    log.severe "Unknown pipeline guid $command.run.id returned from poll for $details.pguids"
                    return
                }
                
                // Execute the commands
                executeCommand(command, pi)
            }
        }
    }

    private executeCommand(Map commandAttributes, PipelineInfo pi) {
        
        log.info "Executing command with attributes: " + commandAttributes
        try {
            Class commandClass = COMMANDS[commandAttributes.command]
            if(commandClass == null) {
                throw new Exception("Unknown command $commandAttributes returned by agent poll for $pi.pguid")
            }
    
            BpipeCommand command = commandClass.newInstance()
            command.args = commandAttributes.arguments.split(",")
            
            String result = command.shellExecute(pi)
            worx.sendJson("/commandResult/$commandAttributes.id", [
                    command: commandAttributes.id,
                    status: "ok",
                    output: result
                ]
            )
           Object response = worx.readResponse()
        } 
        catch (Exception e) {
            
            StringWriter stackTrace = new StringWriter()
            PrintWriter stackTraceWriter = new PrintWriter(stackTrace)
            e.printStackTrace(stackTraceWriter)
            
            log.severe "Command $commandAttributes failed: " + stackTrace
            
            worx.sendJson("/commandResult/$commandAttributes.id", [
                command: commandAttributes.id,
                status: "failed",
                error: stackTrace
            ]) 
        }
        
    }
}
