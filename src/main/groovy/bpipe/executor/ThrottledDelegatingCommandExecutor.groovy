package bpipe.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Log;
import bpipe.Command;
import bpipe.Concurrency;
import bpipe.PipelineContext;
import bpipe.ResourceUnit;
import bpipe.RescheduleResult
import bpipe.PipelineError

/**
 * Wraps another CommandExecutor and adds concurrency control to it
 * so that the command will not execute if it would violate the concurrency 
 * restrictions configured by the user.
 * 
 * @author ssadedin
 */
@Log
class ThrottledDelegatingCommandExecutor implements CommandExecutor {
    
    public static final long serialVersionUID = 4057750835163512598L
    
    /**
     * The level of concurrency that will be reserved for executing the command
     */
    List<ResourceUnit> resources
    
    transient Appendable outputLog = null
    
    /**
     * The output log to which stderr will be written
     */
    transient Appendable errorLog = null
     
    /**
     * If true, command is only actually executed when "waitFor()" is called
     * This is necessary to avoid deadlocks when multiple commands are run
     * simultaneously inside a single resource usage block 
     * (see {@link PipelineContext#multiExec(java.util.List)}
     */
    boolean deferred = false
    
    @Delegate CommandExecutor commandExecutor
    
    /**
     * Set if this job has been rescheduled
     */
    transient CommandExecutor rescheduledExecutor
    
    /**
     * Set by the waitFor() method to indicate if reschedule was successful
     */
    transient bpipe.RescheduleResult rescheduleResult
    
    /**
     * This lock controls concurrency so that we do not try to launch too many concurrent jobs.
     * On some scheduling systems this can cause failures in an of itself.
     */
    private static Object jobLaunchLock = new Object()
    
    // Stored parameters that are cached from the original "start" command
    // and used when "waitFor" is called
    Map cfg
    Command command
    
    ThrottledDelegatingCommandExecutor(CommandExecutor delegateTo, Map resources) {
        
        // Note that the sort here is vital to avoid deadlocks - it ensures 
        // that resources are always allocated in the same order
        this.resources = resources.values()*.clone().sort { it.key }
        this.commandExecutor = delegateTo
    }
    
    /**
     * Acquire the number of configured concurrency permits and then call
     * the delegate {@link CommandExecutor#start(java.util.Map, String, String, String, java.io.File)}
     */
    @Override
    void start(Map cfg, Command cmd, Appendable outputLog, Appendable errorLog) {
        this.command = cmd
        this.outputLog = outputLog
        this.errorLog = errorLog
        this.cfg = cfg
        if(!deferred) {
            doStart(cfg,cmd)
        }
    }
    
    void doStart(Map cfg, Command cmd) {
        
        ResourceUnit threadResource = resources.find { it.key == "threads" }

        if(isUnlimited(cfg,threadResource))
            threadResource.amount = ResourceUnit.UNLIMITED

        addMemoryResources(cfg, cmd)
            
        resources.each { Concurrency.instance.acquire(it) }

        int threadCount = threadResource?threadResource.amount:1
        String threadAmount = String.valueOf(threadCount)

        // Problem: some executors use non-integer values here, if we overwrite with an integer value then
        // we break them (Sge)
        if(cfg.procs == null || cfg.procs.toString().isInteger() || (cfg.procs instanceof IntRange))
            cfg.procs = threadCount

        command.command = command.command.replaceAll(PipelineContext.THREAD_LAZY_VALUE, threadAmount)
        
        command.allocated = true
        command.createTimeMs = System.currentTimeMillis()

        bpipe.Pipeline.currentRuntimePipeline.get().isIdle = false

        if(bpipe.Runner.opts.t || bpipe.Config.config.breakTriggered) {
            String msg = command.branch.name ? "Branch $command.branch.name would execute: $cmd.command" : "Would execute $cmd.command"
            this.releaseAll()
            if(commandExecutor instanceof LocalCommandExecutor)
                throw new bpipe.PipelineTestAbort(msg)
            else {
                throw new bpipe.PipelineTestAbort("$msg\n\n                using $commandExecutor with config $cfg")
            }
        }
        
        synchronized(jobLaunchLock) {
            if(cfg.containsKey('jobLaunchSeparationMs')) {
                Thread.sleep(cfg.jobLaunchSeparationMs)
            }
            commandExecutor.start(cfg, this.command, outputLog, errorLog)
        }
        
        this.command.save()
    }
    
    @CompileStatic
    void addMemoryResources(Map cfg, Command cmd) {
        
        if(!cfg.containsKey("memory")) {
            checkIfMemoryInCommand()
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
        command.command = command.command.replaceAll(PipelineContext.MEMORY_LAZY_VALUE, memoryValue)
        resources << memoryResource
    }
    
    /**
     * Throws exception with informative error message if $memory was referenced in a command
     * but was not defined anywhere in configuration.
     * <p>
     * <i>Note</i>: this was made a separate method because of IllegalAccessError with @CompileStatic
     * in addMemoryResources
     */
    private void checkIfMemoryInCommand() {
        if(command.command.contains(PipelineContext.MEMORY_LAZY_VALUE)) 
            throw new PipelineError("Command in stage $command.name:\n\n" + command.command.replaceAll(PipelineContext.MEMORY_LAZY_VALUE,'\\${memory}') + "\n\ncontains reference to \$memory variable, but no memory is specified for this command in the configuration.\n\n" + 
                                    "Please add an entry for memory to the configuration of this command in your bpipe.config file")
    }
    
    @CompileStatic
    boolean isUnlimited(Map cfg, ResourceUnit threadResource) {
        
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
    
    /**
     * Wait for the delegate pipeline to stop and then release concurrency permits
     * that were allocated to it
     */
    @Override
    int waitFor() {
        if(deferred) {
            doStart(cfg,this.command)
        }
        
        try {
            return waitWithReschedule()
        }
        finally {
            log.info "Releasing ${resources.size()} resources"
            releaseAll()
            log.info "Released ${resources.size()} resources"
        }
    }
    
    int waitWithReschedule() {
        
        log.info "Waiting for command to complete before releasing ${resources.size()} resources"
        while(true) {
            
            int result = commandExecutor.waitFor()
            
            // rescheduledExecutor being set is a flag to indicate the underlying job 
            // has been rescheduled
            if((result != 0) && rescheduledExecutor) { 
                
                reschedule()
                    
                // In the case of rescheduling, re-execute this loop with the new executor
                continue
            }
            return result
        }
    }
    
    void reschedule() {
        
        log.info "Job $command.id has been rescheduled"
        
        this.commandExecutor = rescheduledExecutor
        this.command.executor = rescheduledExecutor
        commandExecutor.start(this.cfg, this.command, outputLog, errorLog)
        this.command.save()
        this.rescheduleResult = RescheduleResult.SUCCEEDED
        this.rescheduledExecutor = null
    }
    
    final static long RESCHEDULE_JOB_TIMEOUT = 30000
    
    public RescheduleResult reschedule(CommandExecutor newExecutor) {
        this.rescheduledExecutor = newExecutor
        this.stop()
        bpipe.Utils.waitWithTimeout(RESCHEDULE_JOB_TIMEOUT) {
            this.rescheduleResult != null
        }.ok {
            this.rescheduleResult
        }.timeout {
           log.warning "Attempt to reschedule job timed out after "
           RescheduleResult.FAILED 
        }
    }
    
    void releaseAll() {
        try {
            bpipe.Pipeline.currentRuntimePipeline.get().isIdle = true
        }
        catch(Exception e) {
            log.severe "Unable to set pipeline to idle after releasing resources"
        }
        
        resources.reverse().each { resource ->
            try {
                Concurrency.instance.release(resource)
            }
            catch(Throwable t) {
                log.warning("Error reported while releasing $resource.amount $resource.key : " + t.toString())
            }
        }
    }
    
    String status() {
        String result = commandExecutor.status()
        try {
            command.status = bpipe.CommandStatus.valueOf(result)
            command.save()
        }
        catch(Exception e) {
            log.warning("Failed to update command status " + command)
        }
        return result   
    }
    
    void setJobName(String name) {
        if(this.commandExecutor.respondsTo("setJobName")) {
            log.info "Setting job name"
            this.commandExecutor.setJobName(cmd.name)
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
    
    @Override
    void cleanup() {
        this.commandExecutor.cleanup()
    }
}
