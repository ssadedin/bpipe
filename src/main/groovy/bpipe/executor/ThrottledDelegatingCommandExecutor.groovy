package bpipe.executor

import groovy.util.logging.Log;
import bpipe.Concurrency;

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
    int concurrency 
    
    @Delegate CommandExecutor commandExecutor
    
    ThrottledDelegatingCommandExecutor(CommandExecutor delegateTo, int concurrency) {
        this.concurrency = concurrency
        this.commandExecutor = delegateTo
    }
    
    /**
     * Acquire the number of configured concurrency permits and then call
     * the delegate {@link CommandExecutor#start(java.util.Map, String, String, String, java.io.File)}
     */
    @Override
    void start(Map cfg, String id, String name, String cmd, File outputDirectory) {
        Concurrency.instance.acquire(concurrency)
        commandExecutor.start(cfg, id, name, cmd, outputDirectory)
    }
    
    /**
     * Wait for the delegate pipeline to stop and then release concurrency permits
     * that were allocated to it
     */
    @Override
    int waitFor() {
        try {
            int result = commandExecutor.waitFor()
            return result
        }
        finally {
            try {
                Concurrency.instance.release(concurrency)
            }
            catch(Throwable t) {
                log.warning("Error reported while releasing $concurrency concurrency permits : " + t.toString())
            }
        }
    }
    
    @Override
    void stop() {
        try {
            Concurrency.instance.release(concurrency)
        }
        catch(Throwable t) {
            // Stop can be called from outside the Bpipe instance that is actually running the
            // pipeline, so that will probably generate this error
            log.warning("Error reported while releasing $concurrency concurrency permits : " + t.toString())
        }
        this.commandExecutor.stop()
    }
}
