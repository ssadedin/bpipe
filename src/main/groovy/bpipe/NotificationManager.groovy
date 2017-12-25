/*
 * Copyright (c) 2012 MCRI, authors
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

import java.lang.reflect.Constructor;
import java.util.logging.Level;

import groovy.text.SimpleTemplateEngine
import groovy.util.ConfigObject;
import groovy.util.logging.Log;

/**
 * Responsible for sending out notifications over
 * notification channels, such as XMPP or SMTP
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@Singleton
class NotificationManager {
    
    ConfigObject cfg
	
	/**
	 * Time at which a message was last sent for each category of message
	 * ("category" here being a combination of attributes; see computeMessageCategory)
	 */
	Map<String,Long> sendTimestamps = [:]
    
    /**
     * Configured notification channels
     */
    Map<String,NotificationChannel> channels = [:]
    
    /**
     * Configure this notification manager with the given configuration object
     * <p>
     * Causes the manager to subscribe to necessary events to send configured
     * notifications.
     * 
     * @param obj    configuration
     */
    void configure(ConfigObject obj) {
        
        log.info "Configuring notifications"
        
        this.cfg = obj
        
        // Add a default FileNotificationChannel if one does not already exist
        if(!cfg.notifications.containsKey('file')) {
            cfg.notifications.file = new ConfigObject()
            cfg.notifications.file.events="SEND"
        }
            
        cfg.notifications.each { String name, ConfigObject channelCfg -> 
            channelCfg.type = channelCfg.type?:name
			
            NotificationChannel channel = createChannel(channelCfg) 
            
            PipelineEvent [] eventFilter = [PipelineEvent.FINISHED]
            if(channelCfg.containsKey('events'))  {
                try {
                    eventFilter = channelCfg.events.tokenize(",").collect { evt ->
                        log.info("Subscribing channel $channelCfg.name to event " + evt)
                        PipelineEvent.valueOf(evt) 
                    }
                }
                catch(IllegalArgumentException e) {
                    System.err.println("ERROR: unknown type of Pipeline Event configured for notification type $name: " + channelCfg.events)
                    log.severe("ERROR: unknown type of Pipeline Event configured for notification type $name: " + channelCfg.events)
                }
            }
            
            // this.channels[name] = channel
            channelCfg.channel = channel
            
            // Wire up required events
            eventFilter.each {
                EventManager.instance.addListener(it, { evt, desc, detail -> 
					sendNotification(channelCfg, evt, desc, detail)

	            } as PipelineEventListener)
            }    
        }
    }
	
	void sendNotification(String channelName, PipelineEvent evt, String desc, Map detail) {
        
        // When run in test mode, notifications are not configured
        // However sending can now be invoked explicitly by the user, so
        // need to be aware of that
        if(this.cfg == null)
            return
        
        if(!this.cfg.notifications.containsKey(channelName)) {
            String msg = "An unknown communication recipient / channel was specified: $channelName for message: $desc"
            log.warning(msg)
            println "WARNING: $msg\nWARNING: To fix this, please edit bpipe.config and add a '$channelName' entry."
            return
        }
        
        // Find the correct configuration
        sendNotification(this.cfg.notifications[channelName], evt, desc, detail)
    }
    
	/**
	 * Send the given notification, subject to constraints on sends that are configured for
	 * the channel
	 */
	void sendNotification(ConfigObject cfg, PipelineEvent evt, String desc, Map detail) {
        
        log.info "Sending to channel $cfg"
        
        Utils.waitWithTimeout(30000) { 
            cfg.containsKey('channel')
        }.ok {
            log.info("Channel for $cfg.name is active")
        }.timeout {
            throw new PipelineError("Notification channel $cfg.name is not configured. Please check the log files to see why this channel did not set up correctly")
        }
        
        
        NotificationChannel channel = cfg.channel
		
		long intervalMs = cfg.interval?:0
		
		String category = cfg.type + "." + evt.name()
		long lastNotificationTimeMs = sendTimestamps[category]?:0
		
		// Check timestamp of last send and whether we are within the interval limit for sends
		// to this channel
		long nowMs = System.currentTimeMillis()
		if(intervalMs > 0 && lastNotificationTimeMs > 0) {
			if(nowMs - lastNotificationTimeMs < intervalMs) {
				log.info("Ignoring notification $desc for event $evt because it occurred too soon after the last notification")
				return
			}
		}
        
        // Figure out the right template name from the channel configuration
        // Note that in most situations, detail[send.contentType] below will be null
        // - only when the pipeline itself is sending content and suggests a content type
        // will it be non-null
        String templateName = channel.getDefaultTemplate(detail["send.contentType"])
        if(cfg.containsKey("template")) {
            templateName = cfg.template
        }
        
        // Is it customized for this event?
        if(cfg.containsKey("templates")) {
            if(cfg.templates.containsKey(evt.name())) {
                templateName = cfg.templates[evt.name()]
            }
        }
        
        File templateFile = ReportGenerator.resolveTemplateFile(templateName)
        if(!templateFile.exists()) {
            def msg = "WARNING: unable to send notification: template $templateName mapped to file $templateFile.absolutePath, but this file does not exist!"
            log.error msg
            println msg
            return
        }
        
        if(detail.checks) {
            StringWriter w = new StringWriter()
            ChecksCommand.printChecks(detail.checks, out:w, columns:60)
            detail.checkReport = w.toString()
        }
        
        String contentType = 'text/plain'
        
        // Default content type to HTML when extension is HTML
        if(templateName.endsWith('.html'))
            contentType = 'text/html'
            
        // In case the content type is explicitly specified for the message
        if(detail.containsKey('send.contentType'))
            contentType = detail['send.contentType']
        else
        // Or let config override for ultimate control
        if(cfg.containsKey('contentType'))
            contentType = cfg.contentType
            
        //Pipeline pipeline = Pipeline.currentRuntimePipeline.get()?.rootPipeline
        
        def engine = new SimpleTemplateEngine()
        detail += [
            date      : (new Date()),
            full_path : (new File(".").absolutePath),
            event : evt,
            description: desc,
            'send.contentType' : contentType,
            pipeline : Pipeline.rootPipeline
        ]
        
        log.info "Generating template from file $templateFile.absolutePath"
        def template = engine.createTemplate(templateFile.getText())
	
		sendTimestamps[category] = System.currentTimeMillis()
		
		try {
			channel.notify(evt, desc, template, detail)
		}
		catch(Throwable t) {
			log.warning("Failed to send notification via channel "+ channel + " with using template file $templateFile.absolutePath,configuration " + cfg + ": " + t)
            log.log(Level.SEVERE, "Failed to send notification to channel $channel using template $templateFile, coniguration $cfg", t)
			System.err.println "MSG: unable to send notification to channel $channel due to $t"
		}
	}
	
    /**
     * Attempt to create a notification channel based on the given name / config.
     * <p>
     * Channels are instantiated as classes based on the "type", which can be 
     * explicitly configured or if it is not, the name will be used as the type.
     * <p>
     * The type is used in various ways to synthesise class names to try, such as
     * a full absolute class name, but also uppercased and as a prefix to 
     * "NotificationChannel", so, for example, "xmpp" will cause 
     * "XMPPNotificationChannel" to be tried.
     * 
     * @param name
     * @param channelCfg
     * @return    {@link NotificationChannel} instance ready to send notifications
     */
   NotificationChannel createChannel(ConfigObject channelCfg) {
       String clazz = channelCfg.type
       
       // Try and be a little bit more flexible with how things are specified
       if(channelCfg.interval && channelCfg.interval instanceof String)
           channelCfg.interval = Integer.parseInt(channelCfg.interval)
       
       // We try a few different permutations to allow a natural an easy specification
       // of the class name rather than making the user use the fully qualified one.
       // For example, "xmpp" =>  bpipe.XMPPNotificationChannel, etc.
       String upperFirst = clazz[0].toUpperCase() + clazz.substring(1)
       for(String fullClazz in [clazz, "bpipe."+clazz.toUpperCase()+"NotificationChannel", "bpipe." + clazz, "bpipe."+upperFirst+"NotificationChannel"]) {
           try {
               log.info "Trying class name $fullClazz for notification channel $clazz"
               Class [] args = [ ConfigObject.class ] as Class[]
               Constructor c = Class.forName(fullClazz).getConstructor(args)
               NotificationChannel nc = c.newInstance( [ channelCfg ] as Object[] )
               log.info "Successfully created notification channel using class $fullClazz"
               return nc
           }
           catch(ClassNotFoundException e) {
               // This is expected
               log.info("Unable to create notification channel using class " + fullClazz + ": " + e)
           }
           catch(NoClassDefFoundError e) {
               // This is expected
               log.info("Unable to create notification channel using class " + fullClazz + ": " + e)
           }
           catch(Exception e) {
               log.severe("Unable to create notification channel using class " + fullClazz + ": " + e)
               e.printStackTrace()
           }
       }
       throw new PipelineError("Configured notification channel type $clazz could not be created")
   }
   
   void shutdown() {
       if(!this.cfg)
           return 
       this.cfg.notifications.each { id, channelCfg ->
           if(channelCfg.channel.respondsTo('close'))
               channelCfg.channel.close()
           else
               log.info("Channel $id does not support close")
       }
   }
}
