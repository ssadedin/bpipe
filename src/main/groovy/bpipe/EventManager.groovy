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

import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Coordinates the distribution of events that occur during the running
 * of a pipeline.  Currently responsible for sending out notifications,
 * though later this should be separated out to be just another 
 * "listener" for events.
 * 
 * @author ssadedin
 */
@Singleton
@Log
class EventManager {
	
	Map<PipelineEvent,PipelineEventListener> listeners = [:]

	Map cfg
    
    @CompileStatic
    static EventManager getTheInstance() {
        return EventManager.instance
    }
	
	/**
	 * Configure default event listeners from file / configuration object
	 * 
	 * @param cfg
	 */
	void configure(Map cfg) {
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
        if(!listeners[evt]) {
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
     * <p>
     * Currently this is a fully synchronous / blocking call, and thus a hang from one 
     * of the server connections can cause delay to the caller.
	 * 
	 * @param evt	Kind of event
	 * @param desc	brief description (eg: fits in email subject)
	 * @param detail arbitrary key / value data about the event, specific to individual event
	 */
	void signal(PipelineEvent evt, String desc, Map<String,Object> detail=[:]) {
		
		notifyListeners(evt,desc,detail)
		
		if(!cfg.notifications)
			return
	}
}
