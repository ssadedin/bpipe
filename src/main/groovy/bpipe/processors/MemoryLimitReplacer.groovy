package bpipe.processors

import bpipe.*
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
@CompileStatic
class MemoryLimitReplacer implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
       addMemoryResources(resources, command)
    }

    private void addMemoryResources(List<ResourceUnit> resources, Command cmd) {
        
        final Map cfg = cmd.processedConfig      

        if(!cfg.containsKey("memory")) {
            checkIfMemoryInCommand(cmd)
            return
        }
            
        ResourceUnit memoryResource = ResourceUnit.memory(cfg.memory)
        int memoryAmountMB = memoryResource.amount
            
        if(cfg.containsKey("memoryMargin")) {
            ResourceUnit memoryMargin = ResourceUnit.memory(cfg.memoryMargin)
            memoryAmountMB = Math.max((int)(memoryAmountMB/2),(int)(memoryAmountMB-memoryMargin.amount))
            log.info "After applying memory margin of ${memoryMargin}MB, actual memory available is ${memoryAmountMB}MB"
        }
            
        // Actual memory is passed to pipelines in GB
        int memoryGigs = (int)Math.round((double)(memoryAmountMB / 1024))
        String memoryValue = String.valueOf(memoryGigs)
            
        log.info "Reserving ${memoryValue} of memory for command due to presence of memory config"
        cmd.command = cmd.command.replaceAll(PipelineContext.MEMORY_LAZY_VALUE, memoryValue)
        resources << memoryResource
    }
     
    /**
     * Throws exception with informative error message if $memory was referenced in a command
     * but was not defined anywhere in configuration.
     * <p>
     * <i>Note</i>: this was made a separate method because of IllegalAccessError with @CompileStatic
     * in addMemoryResources
     */
    private void checkIfMemoryInCommand(Command command) {
        if(command.command.contains(PipelineContext.MEMORY_LAZY_VALUE)) 
            throw new PipelineError("Command in stage $command.name:\n\n" + command.command.replaceAll(PipelineContext.MEMORY_LAZY_VALUE,'\\${memory}') + "\n\ncontains reference to \$memory variable, but no memory is specified for this command in the configuration.\n\n" + 
                                    "Please add an entry for memory to the configuration of this command in your bpipe.config file")
    }
}
