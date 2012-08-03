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
    
    void configure(ConfigObject obj) {
        this.cfg = obj
        
        // Gather up all the events that are specified for notifications
        List<PipelineEvent> events = scanConfiguredEvents()
        events.each {
            EventManager.instance.addListener(it) { evt, desc, detail -> sendNotifications(event, desc, detail) }
        }
    }
    
    /**
     * Send notifications for the given event.
     * 
     * @param evt
     * @param desc
     */
	void sendNotifications(PipelineEvent evt, String desc, Map<String,Object> detail=[:]) {
    }

    /**
     * Scan through the configuration and find the full set of events
     * that are referred to
     * @return
     */
	List<PipelineEvent> scanConfiguredEvents() {
		List<PipelineEvent> events = cfg.notifications.collect { name, channelCfg ->
			channelCfg.events.split(",").collect { PipelineEvent.valueOf(it) }
		} + [PipelineEvent.FINISHED] // FINISHED is implicit, so add it in  manually
		events = events.flatten().unique()
	}
}
