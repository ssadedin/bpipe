package bpipe.executor

import groovy.util.logging.Log;
import bpipe.Command;
import bpipe.Concurrency;
import bpipe.PipelineContext;
import bpipe.ResourceUnit;

/**
 * Wraps another CommandExecutor and adds concurrency control to it
 * so that the command will not execute if it would violate the concurrency 
 * restrictions configured by the user.
 * 
 * @author ssadedin
 */
@Log
class ThrottledDelegatingCommandExecutor {
    
    /**
     * The level of concurrency that will be reserved for executing the command
     */
    List<ResourceUnit> resources
    
    /**
     * If true, command is only actually executed when "waitFor()" is called
     * This is necessary to avoid deadlocks when multiple commands are run
     * simultaneously inside a single resource usage block 
     * (see {@link PipelineContext#multiExec(java.util.List)}
     */
    boolean deferred = false
    
    @Delegate CommandExecutor commandExecutor
    
    // Stored parameters that are cached from the original "start" command
    // and used when "waitFor" is called
    Map cfg
    Command command
    File outputDirectory
    
    ThrottledDelegatingCommandExecutor(CommandExecutor delegateTo, Map resources) {
        
        // Note that the sort here is vital to avoid deadlocks - it ensures 
        // that resources are always allocated in the same order
        this.resources = resources.values().sort { it.key }
        this.commandExecutor = delegateTo
    }
    
    /**
     * Acquire the number of configured concurrency permits and then call
     * the delegate {@link CommandExecutor#start(java.util.Map, String, String, String, java.io.File)}
     */
    @Override
    void start(Map cfg, Command cmd, File outputDirectory) {
        this.command = cmd
        if(deferred) {
          this.cfg = cfg
          this.outputDirectory = outputDirectory
        }
        else {
          resources.each { Concurrency.instance.acquire(it) }
          commandExecutor.start(cfg, this.command, outputDirectory)
        }
    }
    
    /**
     * Wait for the delegate pipeline to stop and then release concurrency permits
     * that were allocated to it
     */
    @Override
    int waitFor() {
        if(deferred) {
          resources.each { Concurrency.instance.acquire(it) }
          commandExecutor.start(cfg, this.command, outputDirectory)
        }
        
        try {
            log.info "Waiting for command to complete before releasing ${resources.size()} resources"
            int result = commandExecutor.waitFor()
            return result
        }
        finally {
            log.info "Releasing ${resources.size()} resources"
            releaseAll()
            log.info "Released ${resources.size()} resources"
        }
    }
    
    void releaseAll() {
        resources.reverse().each { resource ->
            try {
                Concurrency.instance.release(resource)
            }
            catch(Throwable t) {
                log.warning("Error reported while releasing $resource.amount $resource.key : " + t.toString())
            }
        }
    }
    
    @Override
    void stop() {
        try {
            releaseAll()
        }
        catch(Throwable t) {
            // Stop can be called from outside the Bpipe instance that is actually running the
            // pipeline, so that will probably generate this error
            log.warning("Error reported while releasing resources $resources : " + t.toString())
        }
        this.commandExecutor.stop()
    }
    
    String statusMessage() {
        this.commandExecutor.statusMessage()
    }
    
}
