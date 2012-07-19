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
import groovy.util.logging.Log

/**
 * Coordinates the distribution of events that occur during the running
 * of a pipeline.  Currently responsible for sending out notifications,
 * though later this may be separated out.
 * 
 * @author ssadedin
 */
@Singleton
@Log
class EventManager {
	
	Map<PipelineEvent,PipelineEventListener> listeners = [:]

	ConfigObject cfg
	
	/**
	 * Configure default event listeners from file / configuration object
	 * 
	 * @param cfg
	 */
	void configure(ConfigObject cfg) {
		this.cfg = cfg
	}
	
	/**
	 * Subscribe to hear notifications about this event
	 * @param evt
	 * @param listener
	 */
	synchronized void addListener(PipelineEvent evt, PipelineEventListener listener) {
		if(!listeners[evt])
			this.listeners[evt] = []
		this.listeners[evt] << listener
	}

    /**
     * Unsubscribe a listener
     *
     * @param evt
     * @param listener
     */
    synchronized void removeListener(PipelineEvent evt, PipelineEventListener listener) {
       if( !listeners[evt] ) {
           return
        }

        this.listeners[evt] -= listener

    }
	
	/**
	 * Send notifications to all the listeners for this event
	 */
	synchronized void notifyListeners(PipelineEvent evt, String desc, Map<String,Object> detail=[:]) {
		if(listeners[evt])
			listeners[evt]*.onEvent(evt,desc,detail)
	}
	
	/**
	 * Notify that an event has occured
	 * 
	 * @param evt	Kind of event
	 * @param desc	brief description (eg: fits in email subject)
	 * @param detail arbitrary key / value data about the event, specific to individual event
	 */
	void signal(PipelineEvent evt, String desc, Map<String,Object> detail=[:]) {
		
		notifyListeners(evt,desc,detail)
		
		if(!cfg.notifications)
			return
            
        // Don't send notifications at all if the user is just testing their pipeline
        if(Runner.opts.t)
           return
		
		cfg.notifications.each { name, channelCfg ->
			PipelineEvent [] eventFilter = [PipelineEvent.FINISHED]
			if(channelCfg.events)  {
				try {
					eventFilter = channelCfg.events.split(",").collect { PipelineEvent.valueOf(it) } 
				}
				catch(IllegalArgumentException e) {
					System.err.println("ERROR: unknown type of Pipeline Event configured for notification type $name: " + channelCfg.events)
					log.severe("ERROR: unknown type of Pipeline Event configured for notification type $name: " + channelCfg.events)
				}
			}
				
			// Check that the filter is active for this event
			if(!(evt in eventFilter))
				return
				
			// Nothing would be worse than having your long-running pipeline abort because some email server
		    // was down!
			NotificationChannel channel
			try {
				channel = createChannel(name,channelCfg)
				channel.notify(evt, desc, detail)
			}
			catch(Throwable t) {
				log.warning("Failed to send notification via channel '$name' ("+ channel + ") with configuration " + channelCfg + ": " + t)
				t.printStackTrace()
			}
		}
	}
	
	/**
	 * Attempt to create a notification channel based on the given name / config
	 * 
	 * @param name
	 * @param channelCfg
	 * @return	{@link NotificationChannel} instance ready to send notifications
	 */
	NotificationChannel createChannel(String name, ConfigObject channelCfg) {
		String clazz = channelCfg.type?:name
        
        // Try and be a little bit more flexible with how things are specified
        if(channelCfg.interval && channelCfg.interval instanceof String)
            channelCfg.interval = Integer.parseInt(channelCfg.interval)
		
		// We try a few different permutations to allow a natural an easy specification
		// of the class name rather than making the user use the fully qualified one.
		// For example, "xmpp" =>  bpipe.XMPPNotificationChannel, etc.
		for(String fullClazz in [clazz, "bpipe."+clazz.toUpperCase()+"NotificationChannel", "bpipe." + clazz]) {
			try {
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
			catch(Exception e) {
				log.severe("Unable to create notification channel using class " + fullClazz + ": " + e)
                e.printStackTrace()
			}
		}
        throw new PipelineError("Configured notification channel type $clazz could not be created")
	}
}
