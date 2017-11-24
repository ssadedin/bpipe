package bpipe.agent

import java.nio.file.Files;
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
import bpipe.worx.JMSWorxConnection
import bpipe.worx.WorxConnection
import groovy.util.logging.Log;;
import groovy.json.JsonOutput

@Log
class HttpAgent extends Agent implements Runnable {
    
    Set knownPipelines = new HashSet()
    
    Map<String, PipelineInfo> pipelines = [:]
    
    String name = InetAddress.localHost.hostName
    
//    String id = Utils.sha1(String.valueOf(System.currentTimeMillis()) + new Random().nextInt())
    
    String id = Utils.sha1(Runner.BPIPE_HOME + '::' + name + '::' + System.properties['user.name'])
    
    long pollPeriodMs = 15000
    
    JMSWorxConnection  pollConnection
    
    WorxConnection createConnection()  {
        new HttpWorxConnection()
    }
    
    void run() {
        
        pollConnection = createConnection()
        
        while(true) {
            try {
                scanForPipelines()
    
                pollForCommands()
            } 
            catch (Throwable e) {
                log.severe "Failed to scan for pipelines or poll remote listener: "
                def s = new StringWriter()
                e.printStackTrace(new PrintWriter(s))
                log.severe s.toString()
                
                if(pollConnection) {
                    pollConnection.close()
                    pollConnection = createConnection()
                }
            }
            Thread.sleep(pollPeriodMs)
        }
    }
    
    void pollForCommands() {
        
        Map details = [
            agent: [id: this.id, name: this.name],
            pguids: this.pipelines.keySet() as List
        ]
        
        pollConnection.sendJson("/commands", JsonOutput.toJson(details))
        Map result = pollConnection.readResponse()
        
        if(log.isLoggable(Level.FINE))
            log.fine "Result = " + result
            
        if(result == null)
            return
        
        if(result.commands) {
            // Each command is a map, indexed by pguid mapping to a list of Command objects
            // which are serialised as Maps
            result.commands.each { Map commandAttributes -> 
                processCommand(commandAttributes)
            }
        }
    }
}
