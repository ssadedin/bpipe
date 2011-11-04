package bpipe

import java.util.logging.Logger;

/**
 * Forwards all calls through to a thread specific context.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class PipelineDelegate {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineDelegate");
    
    PipelineDelegate(PipelineContext ctx) {
        if(ctx == null)
	        throw new IllegalArgumentException("Attempt to set null pipeline context")
        this.context.set(ctx)
    }
    
    ThreadLocal<PipelineContext> context = new ThreadLocal<PipelineContext>()
    
    static void setDelegateOn(PipelineContext context, Closure body) {
        synchronized(body) {
            if(body.getDelegate() == null || !(body.getDelegate() instanceof PipelineDelegate)) {
                def d = new PipelineDelegate(context)
                body.setDelegate(d)
            }
            else {
                body.getDelegate().context.set(context)
            }
        }
    }
    
    def methodMissing(String name, args) {
//        println "Query for method $name on ${context.get()} via delegate ${this}"
        return context.get().invokeMethod(name, args)
    }
    
    def propertyMissing(String name) {
//        println "Query for $name on ${context.get()} via delegate ${this}"
        return context.get().getProperty(name)
    }
}
