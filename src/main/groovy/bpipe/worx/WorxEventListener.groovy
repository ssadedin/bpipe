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

import bpipe.Config;
import bpipe.EventManager;
import bpipe.PipelineContext;
import bpipe.PipelineEvent;
import bpipe.PipelineEventListener;

import groovy.util.logging.Log;

import static PipelineEvent.*


class WorxEventJob implements Runnable {
    
    Date date = new Date()
    PipelineEvent event
    Map properties = [:]
    
    void run() {
        HttpURLConnection connection = new URL(Config.userConfig["worx.url"]?:"http://127.0.0.1/").openConnection()
        connection.setRequestMethod("POST")
        connection.doOutput = true
        connection.addRequestProperty("Content-Type","application/json")
        connection.outputStream.withWriter { it << JsonOutput.toJson(params) }
        def response = connection.content.text
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
    
    ExecutorService service 
    
    void start() {
        EventManager.instance.addListener(STAGE_STARTED, this)
        EventManager.instance.addListener(STAGE_COMPLETED, this)
        
        this.service = Executors.newSingleThreadExecutor()
    }
    
    @Override
    public void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
        
        PipelineContext ctx = details?.stage?.context
        if(!ctx)
            ctx = details.ctx
        
        if(!ctx)    {
            log.warning("Pipeline stage or context missing from details provided to statistics event")
            return
        }
        
        this.service.submit();
        
        switch(eventType) {
            
            case STAGE_STARTED:
                log.info "Annotating stage start time $ctx.stageName"
                ctx.doc startedAt: new Date()
            break
            
            case STAGE_COMPLETED:
                log.info "Annotating stage end time $ctx.stageName"
                ctx.doc finishedAt: new Date(), elapsedMs: (System.currentTimeMillis() - ctx.documentation.startedAt.time)
            break
    }

}
