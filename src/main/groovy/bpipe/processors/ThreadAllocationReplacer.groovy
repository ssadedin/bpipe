package bpipe.processors

import bpipe.*
import groovy.transform.CompileStatic

@CompileStatic
class ThreadAllocationReplacer implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        String threadAmount = resolveAndConfigureThreadResources(command, resources)
        command.command = command.command.replaceAll(PipelineContext.THREAD_LAZY_VALUE, threadAmount)
    }
    
    String resolveAndConfigureThreadResources(Command command, List<ResourceUnit> resources) {
        
        Map cfg = command.processedConfig        
        ResourceUnit threadResource = resources.find { ResourceUnit ru -> ru.key == "threads" }

        if(isUnlimited(command, cfg,threadResource))
            threadResource.amount = ResourceUnit.UNLIMITED

        resources.each { Concurrency.theInstance.acquire(it) }

        int threadCount = threadResource?threadResource.amount:1
        String threadAmount = String.valueOf(threadCount)

        // Problem: some executors use non-integer values here, if we overwrite with an integer value then
        // we break them (Sge)
        if((cfg.procs == null) || cfg.procs.toString().isInteger() || (cfg.procs instanceof IntRange))
            cfg.procs = threadCount
        return threadAmount
    }

    /**
     * Resolve if the given command can take variable number of thread resources and therefore
     * needs to submit to thread auctioning
     * 
     * @param command
     * @param cfg
     * @param threadResource
     * @return
     */
    boolean isUnlimited(Command command, Map cfg, ResourceUnit threadResource) {
        
        if(threadResource == null)
            return false
        
        if(!command.command.contains(PipelineContext.THREAD_LAZY_VALUE))
            return false
            
        if((threadResource.amount==0) && (threadResource.maxAmount==0)) 
            return true

        if(!cfg.containsKey("procs") && (threadResource.amount == 1) && (threadResource.maxAmount == 0))
            return true
        else            
            return false    
    }
}
