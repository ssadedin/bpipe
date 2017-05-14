/*
 * Copyright (c) 2013 MCRI, authors
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
package bpipe.worx

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import bpipe.Config;
import bpipe.EventManager;
import bpipe.NodeListCategory;
import bpipe.Pipeline;
import bpipe.PipelineContext;
import bpipe.PipelineEvent;
import bpipe.PipelineEventListener;
import bpipe.PipelineStage;
import bpipe.Runner
import bpipe.cmd.Stop;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper
import groovy.util.logging.Log;
import static PipelineEvent.*


/**
 * Event listener that forwards pipeline events to a remote
 * PipeWorx server.
 * 
 * @author Simon Sadedin
 */
@Log
class WorxEventListener implements PipelineEventListener {
    
    /**
     * Background thread that sends the events asynchronously
     */
    ExecutorService service 
    
    /**
     * Low level connection to worx server
     */
    WorxConnection worx
    
    @Log
    class WorxEventJob implements Runnable {
        
        Date date = new Date()
        PipelineEvent event
        Map properties = [:]
        
        void run() {
            sendEvent(this)
        }
        
        String toString() {
            "${event.name()} : " + properties
        }
    }

    void sendEvent(WorxEventJob job) {
                   
        try {
            log.info "Sending event " + job.toString()
            
            Map eventDetails = job.properties.clone()
                                  .collect {(it.value instanceof PipelineStage) ? ["stage",it.value.toProperties()] : it}
                                  .collectEntries()
                    
            eventDetails.time = System.currentTimeMillis() 
            eventDetails.event = job.event.name()
            eventDetails += [
                pid: Config.config.pid,
                script: Config.config.script,
                pguid: Config.config.pguid
            ]
            
            /*
            println "EVENT DETAILS: "
            eventDetails.each { e ->
                println e.key + " : " + e.value
            }
            */
            
            eventDetails.commands = null
            
            try {
                String json = JsonOutput.toJson([events: [eventDetails]])
                worx.sendJson("/events", json)
            }
            catch(Throwable e) {
                e.printStackTrace()
            }
            
            def response = worx.readResponse()
            log.info "Response: $response"
            
            for(Map cmd in response.commands) {
                if(cmd.command == "stop") {
                    new Stop().run(System.out, cmd.arguments)
                }
            }
        }
        catch(Exception e) {
            log.severe "Failed to send event to $socket $this"
            e.printStackTrace()
        }
      
    }
    
    void start() {
        
        this.worx = new WorxConnection()
        
        [
             PipelineEvent.STARTED,
             PipelineEvent.STAGE_STARTED,
             PipelineEvent.STAGE_COMPLETED, 
             PipelineEvent.FINISHED,
             PipelineEvent.SHUTDOWN
        ].each { EventManager.instance.addListener(it,this) } 
        
        this.service = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                Thread newThread(Runnable r) {
                    Thread t=new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
        })
    }
    
    @Override
    public void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
        
        if(eventType == SHUTDOWN) {
            this.shutdown()
            return
        }
        
        PipelineContext ctx = details?.stage?.context
        if(!ctx)
            ctx = details.ctx
       
        File scriptFile = new File(Config.config.script)
        if(eventType == STARTED || eventType == FINISHED) {
            if(details.pipeline && eventType == STARTED) {
                use(NodeListCategory) {
                    details.pipeline = groovy.json.JsonOutput.toJson(Pipeline.getNodeGraph(details.pipeline))
                    log.info "Sending pipeline structure: $details.pipeline"
                } 
            }
            else {
                details.remove("pipeline")
            }
            
            details.title = Pipeline.documentation.title
            if(!details.title)
                details.title = scriptFile.name.replaceAll('\\.[^\\.]*?$', '').capitalize()
            details.dir = Runner.runDirectory
        }
        
        // All events
        details.script = scriptFile.canonicalPath
        details.host = InetAddress.getLocalHost().hostName
            
        WorxEventJob job = new WorxEventJob(event:eventType, properties: [desc: desc, timeMs: System.currentTimeMillis()] + details)
        this.service.submit(job);
        
        /*
        switch(eventType) {
            
            case STAGE_STARTED:
                ctx.doc startedAt: new Date()
            break
            
            case STAGE_COMPLETED:
                ctx.doc finishedAt: new Date(), elapsedMs: (System.currentTimeMillis() - ctx.documentation.startedAt.time)
            break
        }
        */
    }
    
    void shutdown() {
        // Try to wait for any notifications that are still pending to get out
        if(this.service) {
            this.service.shutdown()
            if(!this.service.awaitTermination(10, TimeUnit.SECONDS))
                System.err.println "WARNING: Worx queue timed out before shutting down. Events may have been lost."
        }
    }
}
