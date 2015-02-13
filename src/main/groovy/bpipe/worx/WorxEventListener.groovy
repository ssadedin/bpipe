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
import bpipe.Runner;
import groovy.json.JsonOutput;
import groovy.util.logging.Log;
import static PipelineEvent.*

/**
 * Convenience wrapper that gives a few useful additional methods for
 * sending HTTP protocol constructs.
 * 
 * @author Simon
 */
class HttpWriter {
    @Delegate
    Writer wrapped
    
    /**
     * Sends the output termintaed by an appropriate newline
     */
    HttpWriter headerLine(String line) {
      wrapped.print(line + "\r\n")
      return this
    }
    
    /**
     * Necessary because default print method comes from Object
     * and prints to stdout.
     * 
     * @param obj
     */
    void print(Object obj) {
        this.wrapped.print(obj)
    }
}

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
     * Underlying socket for connection to Worx server.
     * This is wrapped by #socketReader and #socketWriter.
     */
    Socket socket 
    
    /**
     * Reader for reading the socket
     */
    Reader socketReader
    
    /**
     * Writer for writing to socket
     */
    HttpWriter socketWriter
    
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
   
    void sendEventJson(eventJson) {
        socketWriter.headerLine("POST /events HTTP/1.1")
                    .headerLine("Content-Type: application/json")
                    .headerLine("Content-Length: " + eventJson.size())
                    .headerLine("")
                    .flush()
        
        socketWriter.print(eventJson+"\r\n")
        socketWriter.headerLine("")
                    .flush()
    }
    
    /**
     * Read the HTTP response from the given reader.
     * First reads headers and observes content length header to 
     * then load the body. Requires content length to be set!
     * 
     * @param reader
     * @return
     */
    String readResponse(reader) {
        
        // Read headers
        String line
        int blankCount=0
        Map headers = [:]
        while(true) {
          line = reader.readLine()
          if(!line && (++blankCount>0)) {
              break
          }
          if(line)
            blankCount = 0
          def header = line.trim().split(':')
          if(header.size()>1)
            headers[header[0]] = header[1]
        }

        log.info "Content Length = " + headers['Content-Length'].toInteger()
        char [] buffer = new char[headers['Content-Length'].toInteger()+1]
        reader.read(buffer)

//        println "REPONSE: \n" + buffer
        
        return new String(buffer)
    }

    void sendEvent(WorxEventJob job) {
                   
        try {
            if(socket == null || !socket.isClosed())
                resetSocket()
            
            log.info "Sending event " + job.toString()
            
            Map eventDetails = job.properties.clone()
                                  .collect {(it.value instanceof PipelineStage) ? it.value.toProperties() : it}
                                  .collectEntries()
                    
            eventDetails.time = System.currentTimeMillis()
            eventDetails.event = job.event.name()
            eventDetails += [
                pid: Config.config.pid,
                script: Config.config.script
            ]
            sendEventJson(JsonOutput.toJson([events: [eventDetails]]))
            
            def response = readResponse(socketReader)
            log.info "Response: $response"
        }
        catch(Exception e) {
            log.severe "Failed to send event to $socket $this"
            e.printStackTrace()
        }
      
    }
    
    void resetSocket() {
        
        log.info "Resetting Worx connection ..."
        try {
            socket.close()
        }
        catch(Exception e) {
            // Ignore
        }
        
        log.info "Config is ${Config.userConfig}"
        
        String configUrl = Config.userConfig["worx"]?.url?:"http://127.0.0.1:8888/"
        log.info "Connecting to $configUrl"
        
        URL url = new URL(configUrl)
        socket = new Socket(url.host, url.port) 
        
        socketReader = new BufferedReader(new InputStreamReader(socket.inputStream))
        socketWriter = new HttpWriter(wrapped: new PrintWriter(socket.outputStream))
    }
    
    void start() {
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
                    details.pipeline = groovy.json.JsonOutput.toJson(details.pipeline.toMap())
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
            
        WorxEventJob job = new WorxEventJob(event:eventType, properties: [desc: desc] + details)
        
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
