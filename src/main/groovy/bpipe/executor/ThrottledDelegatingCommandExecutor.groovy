package bpipe.executor

import groovy.transform.CompileStatic
import groovy.util.logging.Log;
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern
import bpipe.Command;
import bpipe.CommandProcessor
import bpipe.Concurrency;
import bpipe.Config
import bpipe.Pipeline
import bpipe.PipelineContext;
import bpipe.ResourceUnit;
import bpipe.processors.DockerContainerWrapper
import bpipe.processors.MemoryLimitReplacer
import bpipe.processors.SingularityContainerWrapper
import bpipe.processors.StorageResolver
import bpipe.processors.ThreadAllocationReplacer
import bpipe.storage.StorageLayer
import bpipe.RescheduleResult
import bpipe.PipelineError
import bpipe.RegionValue

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
    
    /**
     * Resolve final variables and invoke the the wrapped / delegated start 
     */
    @CompileStatic
    void doStart(Map cfg, Command cmd) {
        
        prepareCommand(cmd)

        Pipeline pipeline = bpipe.Pipeline.currentRuntimePipeline.get()
        pipeline.isIdle = false

        if(bpipe.Runner.testMode || bpipe.Config.config.breakTriggered) {
            triggerBreak(cmd, cfg)
        }
        
        synchronized(jobLaunchLock) {
            if(cfg.containsKey('jobLaunchSeparationMs')) {
                Thread.sleep((int)cfg.jobLaunchSeparationMs)
            }
            commandExecutor.start(cfg, this.command, outputLog, errorLog)
        }
        
        this.command.save()
    }

    /**
     * Throw an exception indicating the pipeline is aborting due to user initiated break
     */
    private void triggerBreak(Command cmd, Map cfg) {
        String msg = command.branch.name ? "Stage $command.name in branch $command.branch.name would execute:\n\n        $cmd.command" : "Would execute $cmd.command"
        this.releaseAll()
        if(commandExecutor instanceof LocalCommandExecutor)
            throw new bpipe.PipelineTestAbort(msg)
        else {
            throw new bpipe.PipelineTestAbort("$msg\n\n                using $commandExecutor with config $cfg")
        }
    }

    /**
     * Substitute the lazy variables in the command and initialise the 
     * execution statistics.
     */
    @CompileStatic
    private void prepareCommand(Command cmd) {
        
        runProcessors(cmd)

        if(command.@cfg.beforeRun != null) {
            ((Closure)command.@cfg.beforeRun)(cmd.@cfg)
        }

        command.allocated = true
        command.createTimeMs = System.currentTimeMillis()
    }

    private runProcessors(Command cmd) {
        
        
        List<CommandProcessor> processors = [
            new MemoryLimitReplacer(), 
            new ThreadAllocationReplacer(),
            new StorageResolver(commandExecutor),
        ]
        
        String containerType = ((Map)cmd.processedConfig.container)?.type
        log.info "Container type for command $cmd.id is $containerType"
        if(containerType) {
            if(containerType == "docker") {
                log.info "Configuring command with singularity shell wrapper for command $cmd.id"
                processors << new DockerContainerWrapper()
            }
            else
            if(containerType == "singularity") {
                log.info "Configuring command with singularity shell wrapper for command $cmd.id"
                processors << new SingularityContainerWrapper()
            }
            else 
                throw new IllegalArgumentException("Unknown container type $containerType configured")
        }
        
        for(CommandProcessor processor in processors) {
            processor.transform(cmd, resources)
        }
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
