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
package bpipe

import groovy.util.logging.Log;

import java.util.concurrent.Semaphore;

/**
 * Bpipe uses a thread based architecture for running parallel sections of pipelines.
 * However when running under high concurrency (say, hundreds of input files),
 * this can cause high concurrency for these internal operations, which in turn
 * can result in failures due to OS resources being exhausted.
 * <p>
 * To avoid this, various parts of Bpipe that can afford to execute in a single threaded
 * manner with other parts funnel their actions through this class to throttle 
 * the concurrency of actions down.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Singleton
@Log
class OSResourceThrottle {
    
    /**
     * The concurrency allowed for calling the custom command script.
     * By default, the concurrency is 1, meaning that we do not assume
     * that the custom script supports concurrency at all.  This also
     * prevents hitting resource constraints (such as max file handles)
     * on systems where small limits have been placed on the 
     * head node that is submitting jobs.
     */
    Semaphore concurrencyCounter;
    
    public OSResourceThrottle() {
    }
   
    synchronized acquireLock(Map cfg) {
        if(concurrencyCounter == null) {
           // TODO: the concurrency is not implemented, should be removed
           concurrencyCounter = new Semaphore(cfg?.concurrency?:1)
        }
        concurrencyCounter.acquire()
    }
    
   	/**
	 * Execute the action within the lock that ensures the
	 * maximum concurrency for the custom script is not exceeded.
	 */
	def withLock(Closure action) {
		withLock(null, action)
	}
	
	/**
	 * Execute the action within the lock that ensures the
	 * maximum concurrency for the custom script is not exceeded.
	 */
	def withLock(Map cfg, Closure action) {
		acquireLock(cfg) 
		try {
			return action()
		}
		finally {
			concurrencyCounter.release()
		}
	} 
}
