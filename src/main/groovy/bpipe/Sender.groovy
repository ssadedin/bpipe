/*
 * Copyright (c) 2014 Simon Sadedin, authors
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
package bpipe

import java.util.logging.Level

import bpipe.storage.LocalPipelineFile
import bpipe.storage.UnknownStoragePipelineFile
import groovy.json.JsonOutput
import groovy.util.logging.Log;
import groovy.xml.MarkupBuilder

/**
 * Sends information to a recipient via a communication channel
 * <p>
 * This class enables the DSL syntax for sending explicit 
 * notifications in pipeline stages, eg: 'send "hello" to gtalk'
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class Sender {
    
    PipelineContext ctx
    
    String contentType
    
    /**
     * Content may be a string or it may be a closure that returns a string, or it may
     * be a raw File
     */
    def content
    
    String defaultSubject
    
    Map details = [:]
    
    Closure onSend = null

    public Sender(PipelineContext ctx) {
        this.ctx = ctx
        this.content = content
        this.defaultSubject = "Message from Pipeline Stage $ctx.stageName"
    }
    
    /**
     * Support for sending simple text through a communication channel - 
     * the message is provided as a hard coded string.
     * 
     * @param c Closure that should return a string value
     * @return
     */
    Sender text(Closure c) {
        this.content = c()
        this.contentType = "text/plain"
        this.defaultSubject = Utils.truncnl(content,80)
        return this
    }
    
    Sender json(Closure c) {
        Object result = c()
        if(result instanceof PipelineInput) {
            result = Pipeline.file(result)
        }
        
        // If it is a file object, send the content of the file, not the literal file object
        if(result instanceof File) {
            log.info "Sent JSON resolves to file: sending content of file $result"
            this.content = result.text
        }
        else
            this.content = JsonOutput.toJson(result)
            
        this.contentType = "application/json"
        this.defaultSubject = "JSON content from stage ${ctx.stageName}"
        return this
    }
    
    Sender issue(Map details) {
        this.content = details
        this.contentType = "application/json"
        this.defaultSubject = details.title
        return this
    }
 	
    
    /**
     * Support for sending HTML through a communication channel - 
     * the message is provided by building HTML with a MarkupBuilder.
     * 
     * @param c closure that builds the markup
     * @return  Sender object
     */
    Sender html(Closure c) {
        def result = new StringWriter()
        ctx.currentBuilder = new MarkupBuilder(result)
        ctx.currentBuilder.html(c)
        ctx.currentBuilder = null
        this.content = result.toString()
        this.contentType = "text/html"
        this.extractSubjectFromHTML()
        return this
    }
    
    Sender report(String reportName) {
        
        String threadId = Thread.currentThread().id
        
        File reportDir = new File(".bpipe/reports/"+Config.config.pid+"/")
        reportDir.mkdirs()
        File outFile = new File(reportDir, Math.round(Math.random()*100000) + "." + threadId + '.' + new File(reportName).name)
        
        this.content = {
          Map binding = details.withDefault { ctx.myDelegate.propertyMissing(it) }
          new ReportGenerator(reportBinding:binding).generateFromTemplate(
              Pipeline.currentRuntimePipeline.get(), reportName, reportDir.absolutePath, outFile.name
          ) 
          contentType = outFile.name.endsWith("html") ? "text/html" : "text/plain"
          extractSubjectFromHTML()
          if(reportName.endsWith(".groovy"))
              return outFile
          else
              return outFile.text
        }
        
        return this    
    }
    
    void extractSubjectFromHTML() {
        // We make an attempt to extract the first line of text as a subject
        // when the type is HTML
        if(contentType == "text/html") {
            try {
              this.defaultSubject = Utils.collectText(new XmlParser().parseText(this.content)) { it }[0]
            }
            catch(Throwable t) {
               // .. Ignore .. 
            }
        } 
    }
    
    /**
     * Advanced form of send, eg:
     * 
     * <code>send text "hello" to channel: gmail, subject: "hey there", file: output.txt</code>
     * 
     * @param details
     */
    void to(Map extraDetails) {
       
        if(this.ctx.inferredOutputs) {
            Command cmd = new Command(
                id:CommandId.newId(), 
                command:"send in stage $ctx.stageName" 
            )
            
            if(extraDetails.config != null)
                cmd.configName = extraDetails.config
            
            Map cfg = cmd.getConfig(this.ctx.rawInput)
            
            cmd.outputs = this.ctx.inferredOutputs.collect { new PipelineFile(it, ctx.resolveStorageForConfig(cfg)) }
            this.ctx.trackedOutputs[cmd.id] = cmd
            this.ctx.inferredOutputs = []
        }
       
        // Don't send anything if the pipeline stage was just being probed
        if(ctx.probeMode)
            return
            
        this.details = extraDetails.collectEntries { it }
        
        // Find the config object
        String cfgName;
        ConfigObject channelCfg

        if(details.channel instanceof String) {
            cfgName = details.channel
        }
        else
        if(details.channel instanceof NotificationChannelReference) {
            cfgName = details.channel.channelName
        }

        if(cfgName == null && details.containsKey("file")) {
            cfgName = "file"
            channelCfg = new ConfigObject()
            channelCfg.with {
                type = "file"
                name = "file.send"
                channel = new FileNotificationChannel(new ConfigObject())
            }
        }
        
        if(cfgName?.startsWith('$'))
            cfgName = cfgName.substring(1)
        
        if(!details.containsKey("subject")) {
            details.subject = defaultSubject
        }
        
        // Dereference the content, if necessary
        if(content instanceof Closure) {
            content = content() // Note: may change / set contentType
        }
        
        log.info "Sending $content to $cfgName!"
        
       final File sentFile = determineSentFile(cfgName)
       if(sentFile.exists() && !Dependencies.instance.getOutOfDate([new LocalPipelineFile(sentFile.absolutePath)], ctx.@input)) {
           
           if(extraDetails.file && !Utils.fileExists(extraDetails.file)) {
               log.info "Sent file $sentFile.absolutePath already exists but destination is missing file: will send again"
           }
           else {
               log.info "Sent file $sentFile.absolutePath already exists - skipping send of this message"
               if(onSend != null) {
                 onSend(details)
               } 
               return
           }
       }
       
       if(Runner.testMode) {
           throwTestAbort(cfgName, extraDetails)
       }
       else
           log.info "Sending $content to $cfgName"
           
       Map props = [
            "stage" : ctx.currentStage,
            "send.content": content,
            "send.subject": this.details.subject,
            "send.contentType" : this.contentType,
            "send.file" : this.details.file
        ]
        
        if('url' in details) {
            props["send.url"] = details.url
        }
        
       FileNotificationChannel.modelContentToFile(props, sentFile)

        if('properties' in details) {
           details.properties.each { k,v ->
               props[k] = v
           }
       }
        
       // The file can actually be a PipelineOutput or similar which leads to 
       // bizarre errors when serializing to JSON because the whole PipelineContext
       // gets captured in there
       if(props['send.file'] != null)
           props['send.file'] = String.valueOf(props['send.file'])
       
       if(details.containsKey('url') && !details.containsKey('channel')) {
           new HTTPNotificationChannel(details, this.contentType).sendToURL(this.content)
       }
       else
       if(channelCfg != null) {
           NotificationManager.instance.sendNotification(channelCfg, PipelineEvent.SEND, this.details.subject, props) 
       }
       else {
           NotificationManager.instance.sendNotification(cfgName, PipelineEvent.SEND, this.details.subject, props) 
       }
       
       EventManager.instance.signal(PipelineEvent.SEND, this.details.subject, props)
       
       
       if(ctx.currentCheck && ctx.currentCheck.message != details.subject) {
           ctx.currentCheck.message = details.subject
           ctx.currentCheck.save()
       }
       
       if(onSend != null) {
           onSend(details)
       }
    }
    
    /**
     * Resolve a unique name for the output file that will represent this send.
     * 
     * Each send is given a unique file name by hashing its content, the branch within the pipeline
     * that generates it, the subject (if any) and the file it is to be sent to (if any).
     * 
     * @param cfgName
     * @return  Internal Bpipe file to save the state of this send operation 
     */
    private File determineSentFile(final String cfgName) {
       Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
       File sentFolder = new File(".bpipe/sent/")
       sentFolder.mkdirs()
       
       File legacySentFile = new File(sentFolder, cfgName + "." + ctx.stageName + "." + Utils.sha1(this.details.subject + content))
       File sentFile
       if(legacySentFile.exists()) {
           log.info "Found legacy sent file for $ctx.stageName/$details.subject: not re-sending" 
           sentFile = legacySentFile
       }
       else {
           log.info "Using branch path ${pipeline.branchPath.join('/')} to compute sent file" 
           String sha1Subject = pipeline.branchPath.join('/') + this.details.subject + content
           if(details.file) {
               sha1Subject = sha1Subject + ':'+details.file.toString()
           }
           sentFile = new File(sentFolder, cfgName + "." + ctx.stageName + "." + Utils.sha1(sha1Subject))
           log.info "Using new style sent file $sentFile"
       }
       return sentFile
    }

    private throwTestAbort(String cfgName, Map extraDetails) {
        log.info "Would send $content to $cfgName, but we are in test mode"
        if(onSend != null) {
            onSend(details)
        }

        StringWriter sw = new StringWriter()

        Utils.table(['Property','Value'], [extraDetails*.key, extraDetails*.value].transpose(), out: sw)
        
        String msg = "Would send message to channel '$cfgName' using details:\n\n" + sw.toString()
        if(content instanceof String) {
            if(this.contentType == "application/json") {
                content = JsonOutput.prettyPrint(content)
            }
            msg = msg + "\n\nContent:\n"+ content.readLines().take(30).join('\n')
        }

        throw new PipelineTestAbort(msg)
    }
    
    /**
     * Simplified form : 'send "hello" to gtalk'
     * 
     * @param recipient name of communication channel to use
     */
    void to(String recipient) {
        this.ctx.checkAndClearImplicits("$recipient")
        to([channel:recipient])
    }

    /**
     * Simplified form : 'send "hello" to gtalk'
     * 
     * @param recipient name of communication channel to use
     */
    void to(NotificationChannelReference recipient) {
        to([channel:recipient.channelName])
    }
}
