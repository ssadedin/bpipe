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

import java.io.OutputStream;
import java.util.List;
import java.util.Timer;

@Log
class ForwardHost {
    
    transient List<Forwarder> forwarders = []
    
    public void forward(File file, def stream) {
        this.forward(file.path, stream)
    }
    
    public void forward(String fileName, def stream) {
    
   
        Forwarder f = new Forwarder(new File(fileName), stream)
        log.info "Forwarding file $fileName using forwarder $f"
        
        Poller.instance.timer.schedule(f, 0, 2000)
    
        this.forwarders << f
    }
    
    /**
     * Ensure any remaining output is copied
     */
    void stopForwarding() {
        this.forwarders*.cancel()
        
        log.info "Flushing outputs for forwarders: $forwarders"
        
        // Now run them all one last time to flush any last contents
        this.forwarders*.flush()
    }
}
