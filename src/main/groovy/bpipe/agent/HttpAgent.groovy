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
    
    String name = InetAddress.localHost.hostName
    
//    String id = Utils.sha1(String.valueOf(System.currentTimeMillis()) + new Random().nextInt())
    
    String id = Utils.sha1(Runner.BPIPE_HOME + '::' + name + '::' + System.properties['user.name'])
    
    long pollPeriodMs = 15000
    
    WorxConnection  pollConnection
    
    WorxConnection createConnection()  {
        new HttpWorxConnection()
    }
    
    void run() {
        
        pollConnection = createConnection()
        
        while(true) {
            try {
                scanForPipelines()
    
                pollForCommands()
                
                if(stopRequested)
                    break
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
            
        if(result == null) {
            log.warning "Null response from HTTP connection"
            return
        }
        
        if(result.status == "error") {
            log.error "Poll for commands failed: " + result.reason
        }
        
        if(result.commands) {
            // Each command is a map, indexed by pguid mapping to a list of Command objects
            // which are serialised as Maps
            for(Map commandAttributes in result.commands) { 
                processCommand(commandAttributes)
                
                if(singleShot)
                    stopRequested = true
            }
        }
    }
}
