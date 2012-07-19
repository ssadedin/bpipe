package bpipe

import groovy.util.logging.Log;

/**
 * Forwards all calls through to a thread specific context.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class PipelineDelegate {
    
    PipelineDelegate(PipelineContext ctx) {
        if(ctx == null)
	        throw new IllegalArgumentException("Attempt to set null pipeline context")
        this.context.set(ctx)
    }
    
    ThreadLocal<PipelineContext> context = new ThreadLocal<PipelineContext>()
    
    static void setDelegateOn(PipelineContext context, Closure body) {
        synchronized(body) {
            if(body instanceof ParameterizedClosure) {
                ParameterizedClosure pc = body
                context.localVariables = pc.getExtraVariables()
            }
            
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
        
        // The context can be put into a mode where missing variables should echo themselves back
        // ie.  $foo will produce value $foo.  This is used to preserve $'d values in 
        // R scripts while still allowing interpretation of the ones we want ($input, $output, etc)
		log.info "Query for $name on ${context.get()} via delegate ${this}"
        
        PipelineContext ctx = context.get()
        
        if(ctx.hasProperty(name)) {
	        return ctx.getProperty(name)
        }
        else
        if(Pipeline.currentRuntimePipeline.get()?.variables?.containsKey(name)) {
            return Pipeline.currentRuntimePipeline.get().variables.get(name)
        }
        else
        if(ctx.localVariables.containsKey(name)) {
            return ctx.localVariables[name]
        }
        else
        if(name != "input" && ctx.echoWhenNotFound) {
	        try {
		        def result = ctx.getProperty(name)
	            //log.info "Retrieved result for $name = $result"
	            return result
	        }
	        catch(Throwable t) {
	            return '$'+name
	        }
        }
    }
}
