/*
 * Copyright (c) 2011 MCRI, authors
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

import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler

import static java.nio.file.StandardOpenOption.*

import java.nio.ByteBuffer

import static java.nio.channels.AsynchronousFileChannel.*

import groovy.transform.CompileStatic
import groovy.util.logging.Log;

/**
 * A rather simplistic mechanism to allocate a unique job id for
 * each new 'job' that Bpipe runs.  The primary requirement
 * here is that each job gets an id that is unique even between
 * Bpipe runs. In this implementation the id is stored in a file
 * in the .bpipe folder and is updated each time a new job is allocated.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@CompileStatic
class CommandId {
    
    private static int lastCommandId = -1 
    
    /**
     * File where the job id is stored
     */
    private final static File commandIdFile = new File(".bpipe/commandid") 
    
    private static AsynchronousFileChannel fileChannel = openChannel()
    
    
    static AsynchronousFileChannel openChannel() {
        AsynchronousFileChannel.open(commandIdFile.toPath(), WRITE, CREATE);
    }
    
    static CompletionHandler completionHandler = new CompletionHandler() {
        @Override
        public void failed(Throwable exc, Object attachment) {
            log.warning "Failed to save command id ($exc)"
            if(fileChannel.isOpen()) {
                try {
                    fileChannel.close() 
                } catch (Exception e) {
                    System.err.println("WARNING: Failed to re-open command id channel: $e")
                }
            }
            fileChannel = openChannel()
        }

        @Override
        public void completed(Object result, Object attachment) {
//            println "saved command id $attachment"
        }
    }
    
    /**
     * Returns a newly allocated job id that will not be reused in this instance of Bpipe
     * @return
     */
    static synchronized String newId() {
        
        if(lastCommandId < 0) {
            if(!commandIdFile.exists()) {
                lastCommandId = 0
            }
            else
			try {
	            lastCommandId = Integer.parseInt(commandIdFile.text)
			}
			catch(Exception e) {
				log.warning("Failed to parse command id text: [" + commandIdFile.text + "]")
				
				// Could do better than this: scan directory for old commands? This should
				// be a very rare condition however.
                commandIdFile << "0"
			}
        }
        
        ++lastCommandId
        
        
//        println "save command $lastCommandId"
//        commandIdFile.text = String.valueOf(lastCommandId)
        
        final String id = String.valueOf(lastCommandId)
        
        writeId(id)
        
        return id
    }

    private static writeId(String id) {
        final ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.put(id.getBytes());
        buffer.flip();
        fileChannel.write(buffer,0, buffer, completionHandler)
    }
}
