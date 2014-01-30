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

import groovy.lang.Closure;
import groovy.xml.MarkupBuilder

/**
 * Sends information to a recipient via a communication channel
 * <p>
 * This class enables the DSL syntax for sending explicit 
 * notifications in pipeline stages, eg: 'send "hello" to gtalk'
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Sender {
    
    PipelineContext ctx
    
    String contentType
    
    String content
    
    String defaultSubject

    public Sender(PipelineContext ctx) {
        this.ctx = ctx
        this.content = content
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
        this.defaultSubject = Utils.collectText(new XmlParser().parseText(this.content)) { it }[0]
        return this
    }
    
    /**
     * Advanced form of send, eg:
     * 
     * <code>send text "hello" to channel: gmail, subject: "hey there", file: output.txt</code>
     * 
     * @param details
     */
    void to(Map details) {
        
        // Find the config object
        String cfgName = details.channel
        
        println "Sending $content to $cfgName!"
        
        if(cfgName.startsWith('$'))
            cfgName = cfgName.substring(1)
        
        if(details == null) {
            details = [:]
        }
        
        if(!details.containsKey("subject")) {
            details.subject = defaultSubject
        }
        
        NotificationManager.instance.sendNotification(cfgName, PipelineEvent.SEND, details.subject, [
            "send.content": content,
            "send.subject": details.subject,
            "send.contentType" : this.contentType,
            "send.file" : details.file
        ]) 
    }
    
    /**
     * Simplified form : 'send "hello" to gtalk'
     * 
     * @param recipient name of communication channel to use
     */
    void to(String recipient) {
        to([channel:recipient])
    }
}
