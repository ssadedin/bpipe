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
    
    ThreadLocal<PipelineContext> context = new ThreadLocal<PipelineContext>() {
        void set(PipelineContext c) {
            super.set(c)
        }
    }
    
    static void setDelegateOn(PipelineContext context, Closure body) {
        synchronized(body) {
            if(body instanceof ParameterizedClosure) {
                ParameterizedClosure pc = body
                context.localVariables = pc.getExtraVariables()
            }
            
        
            if(body.getDelegate() == null || !(body.getDelegate() instanceof PipelineDelegate)) {
                
                log.fine "Existing delegate has type ${body.delegate.class.name} in thread ${Thread.currentThread().id}"
        
                def d = new PipelineDelegate(context)
                body.setDelegate(d)
            }
            else {
                body.getDelegate().context.set(context)
            }
        }
    }
    
    def methodMissing(String name, args) {
        log.fine "Query for method $name on ${context.get()} via delegate ${this} in thread ${Thread.currentThread().id}"
        
        if(name == "from") {
            if(args.size()<2) 
                throw new IllegalArgumentException("from requires 2 arguments")
            def actualArgs = args[0..-2] as List
            if(actualArgs[0] instanceof List && actualArgs.size()==1)
                actualArgs = actualArgs[0]
            context.get().invokeMethod("fromImpl", [actualArgs, args[-1]] as Object[])
        }
        else
        if(name == "multi") {
            context.get().invokeMethod("multiExec", [args as List] as Object[])
        }
        else
          return context.get().invokeMethod(name, args)
    }
    
    def propertyMissing(String name) {
        
        // The context can be put into a mode where missing variables should echo themselves back
        // ie.  $foo will produce value $foo.  This is used to preserve $'d values in 
        // R scripts while still allowing interpretation of the ones we want ($input, $output, etc)
        
        log.fine "Query for $name on ${context.get()} via delegate ${this} in thread ${Thread.currentThread().id}"
        
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
        if(name == "region") {
            def genome = Pipeline.genomes.values()[0]
            if(!genome) 
                throw new PipelineError("You referenced a region without defining a genome first. Please specify a genome, eg, using:\n\n\tgenome \"hg19\"\n")
                
            // Construct the regions in Samtools format
            // We filter out "unusual" chromosomes by default
            String regionValue = genome.sequences.values().grep { !it.name.contains('_') }
                                                           .collect { it.name + ':' +it.range.from + '-' +it.range.to }
                                                           .join(" ")
            ctx.localVariables["region"] = 
                new RegionValue(value:regionValue)
            
            return ctx.localVariables.region
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