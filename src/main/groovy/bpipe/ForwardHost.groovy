package bpipe

import java.io.OutputStream;
import java.util.List;
import java.util.Timer;

@Category(ForwardHost)
class ForwardHost {
    
    static Timer forwardingTimer
    
    transient List<Forwarder> forwarders = []
    
    private void forward(String fileName, OutputStream s) {
    
        // Start the forwarding timer task if it is not already running
        synchronized(ForwardHost.class) {
            if(forwardingTimer == null) {
                forwardingTimer = new  Timer(true)
            }
        }
    
        Forwarder f = new Forwarder(new File(fileName), s)
        forwardingTimer.schedule(f, 0, 2000)
    
        this.forwarders << f
    }
}
