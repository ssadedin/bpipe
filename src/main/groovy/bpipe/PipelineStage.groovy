/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe

import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.util.logging.Log

import java.nio.file.DirectoryStream
import java.nio.file.Files;
import java.nio.file.Path
import java.util.regex.Pattern

import bpipe.storage.LocalPipelineFile

class PipelineBodyCategory {
    
    private static final Pattern PREFIX_PATTERN = ~'\\.[^\\.]*?$'
    
	static String getPrefix(String value) {
		return value.replaceAll(PREFIX_PATTERN, '')
	}
    
    static int indexOf(String value, RegionValue other) {
        value.indexOf(other.toString())
    }
    
    /*
    static Object getProperty(Integer n, String name) {
        if(name == "threads") {
            return new ResourceUnit(amount:n as Integer, key: "threads")
        }
        else
        if(name == "GB") {
            return new ResourceUnit(amount: n * 1024 as Integer)
        }
        else
        if(name == "MB") {
            return new ResourceUnit(amount: n as Integer)
        }
        else
            return null
    }
    */
}

/**
 * Encapsulates a Pipeline stage including its body (a closure to run)
 * and all the metadata about it (its name, status, inputs, outputs, etc.).
 * <p>
 * The design goal is that the PipelineStage is independent of the Pipeline that
 * it is running in and thus can be shared or copied to apply it to multiple
 * Pipeline objects, thus enabling parallel / independent execution, or reuse
 * of the same stage at multiple places within a pipeline.  Therefore
 * all the actual running state of a PipelineStage is carried in 
 * a {@link PipelineContext} object that is associated to it.
 * <p>
 * Each stage has an <code>id</code> which identifies the stage uniquely within
 * the topology of the pipeline. Topology, here, means that the same stage used in 
 * different parts of the pipeline will get a different id, but parallel uses of the
 * stage in the same position of the pipeline get the same id.
 * <p>
 * Stages are created in Pipeline.runSegment and their id is assigned in 
 * Pipeline.addStage.
 * 
 * @author ssadedin@mcri.edu.au
 */
@Log
class PipelineStage {
    
    private String id
    
    String getId() {
        return this.id
    }
    
    void setId(String id) {
        this.id = id;
    }
    
    @CompileStatic
    boolean isNonExcludedOutput(Path path) {
        return NewFileFilter.isNonExcludedOutput(path.getFileName().toString(), path)
    }
    
    PipelineStage(PipelineContext context) {
    }
    
    PipelineStage(PipelineContext context, Closure body) {
        this.context = context
        this.body = body
    }
	
	/**
	 * If a pipeline forks to run multiple parallel branches, each 
	 * branch becomes a child of a pipeline stage that acts as a node
	 * at the head of those stages.
	 */
	List<Pipeline> children = []
    
    PipelineContext context
    
    /**
     * The actual closure that will be executed when this pipeline stage runs
     */
    Closure body
    
    boolean running = false
    
    /**
     * True if this is an artificially generated pipeline stage that should not be
     * presented to the user in output reports, etc.
     */
    boolean synthetic = true
    
    /**
     * A short identifier for the stage, taken from the user's pipeline
     * definition
     */
    String stageName = "Unknown"
    
    /**
     * The display name includes more human readable context than the stageName
     * (eg: branch path)
     */
    String displayName = "Unknown Stage"
    
    /**
     * If the name of the stage cannot be determined another way, this name
     * is printed out
     */
    static int stageCount = 1
	
	/**
	 * Set to false if the pipeline stage experiences a failure during execution
	 */
	boolean succeeded = true
    
    def originalInputs
    
    /**
     * Time when body of pipeline stage was launched
     */
    long startDateTimeMs
    
    /**
     * Time when body of pipeline stage exited
     */
    long endDateTimeMs
    
    /**
     * Prepare this stage to execute
     * <p>
     * Sets names of stage, emits required events, outputs to log files
     * 
     * @param pipeline
     */
    void initializeStage(Pipeline pipeline) {
        this.synthetic = false
        
        stageName = 
            PipelineCategory.closureNames.containsKey(body) ? PipelineCategory.closureNames[body] : "${stageCount}"
                        
        displayName = calculateDisplayName(pipeline)
                        
        context.outputLog.flush("\n"+" Stage ${displayName} ".center(Config.config.columns,"="))
        CommandLog.cmdLog << "# Stage $displayName"
        ++stageCount
                    
        // Note: make sure startDateTimeMs is assigned before event is sent
        this.startDateTimeMs = System.currentTimeMillis()
                    
        EventManager.instance.signal(PipelineEvent.STAGE_STARTED, 
                                     "Starting stage $displayName", 
                                     [stage:this])
                    
        context.initialize(pipeline, stageName)
                
        log.info("Stage $displayName : INPUT=${context.@input} OUTPUT=${context.defaultOutput}")
    }
    
    
    /**
     * Executes the pipeline stage body, wrapping it with logic and instrumentation
     * to manage the pipeline. 
     * <p>
     * After the body has been executed, the output files are interrogated to check
     * they are valid and exist and then they are connected to the next 
     * pipeline stage.
     */
    @CompileStatic
    def run() {
        
        // Cache the original inputs for reference when searching back up through 
        // the pipeline for inputs matching a pattern
        this.originalInputs = this.context.rawInput
        
        def pipeline = Pipeline.currentRuntimePipeline.get()
        
        Dependencies.theInstance.checkFiles(context.rawInput, pipeline.aliases)
        
        copyContextBindingsToBody()
        
        succeeded = false
        
        boolean joiner = (body in this.context.pipelineJoiners)
        
		// The name used for displaying this stage
        try {
            if(!joiner) {
                initializeStage(pipeline)
            }   
                
            if(stageName in Config.config.breakAt)
                Config.config.breakTriggered = true
                
            // Execute the actual body of the pipeline stage
            runBody()
                    
            succeeded = true
            if(!joiner) {
                log.info("Stage $displayName returned $context.nextInputs as default inputs for next stage")
            }
               
            setContextNextInputs()
               
            saveOutputMetaData()
            
            DirtyFileManager.theInstance.clear()
        }
        catch(UserTerminateBranchException e) {
            throw e
        }
        catch(PipelineTestAbort e) {
            throw e
        }
        catch(PipelinePausedException e) {
            throw e
        }
        catch(Throwable e) {
                
            if(!joiner) {
               EventManager.theInstance.signal(PipelineEvent.STAGE_COMPLETED, "Finished stage $displayName", (Map<String,Object>)[stage:this])            
               if(!succeeded) {
                   EventManager.theInstance.signal(PipelineEvent.STAGE_FAILED, "Stage $displayName has Failed")
               }
            }
                
            log.severe "Cleaning up outputs due to error: $e"
            log.throwing("PipelineStage", "run", e)
            cleanupOutputs()
            throw e
        }
        finally {
            if(!joiner) {
                if(!context.uncleanFilePath.delete())
                    log.warning("Unable to delete in-progress command file $context.uncleanFilePath")
            }
        }
        
//        log.info "Checking files: " + context.rawOutput.collect { String.valueOf(it) + " ( " + it.storage.class.name + ")" }.join(",")
        try {
             Dependencies.theInstance.checkFiles(context.rawOutput, pipeline.aliases, "output", "in stage ${displayName}")
 
            if(!joiner) {
                 EventManager.theInstance.signal(PipelineEvent.STAGE_COMPLETED, "Finished stage $displayName", (Map<String,Object>)[stage:this])            
            }
        } 
        catch (PipelineError e) {
                
            succeeded = false
                
            if(!joiner) {
                log.info "Sending failed event due to missing output"
                EventManager.theInstance.signal(PipelineEvent.STAGE_COMPLETED, "Finished stage $displayName", (Map<String,Object>)[stage:this])             
                EventManager.theInstance.signal(PipelineEvent.STAGE_FAILED, "Stage $displayName has Failed")
            }
                    
            throw e 
        }
        return context.nextInputs
    }
    
    @CompileStatic
    Map<String,Long> getFileTimestamps(List<Path> paths) {
       (Map<String,Long>)paths.inject((Map<String,Long>)[:]) { Map<String,Long> result, Path f -> 
           result[f.fileName.toString()] = Files.getLastModifiedTime(f).toMillis(); 
           return result 
       } 
    }

    /**
     * Execute the body of the pipeline stage
     * @return
     */
    
    @CompileStatic
	private void runBody() {
		this.running = true
        def stageBranchProp = context.branch.getProperty(stageName)
        if(stageBranchProp != null) {
            log.info "Branch variable $stageName with same name as stage: check for override"
            if(stageBranchProp instanceof Closure) {
                overrideBody((Closure)stageBranchProp)
            }
         }
        
        // Make sure there is a watcher for the output directory
        OutputDirectoryWatcher.getDirectoryWatcher(context.outputDirectory)
        
        PipelineDelegate.setDelegateOn(context,body)
        try {
            Pipeline.currentContext.set(context)
            if(PipelineCategory.wrappers.containsKey(stageName)) {
                log.info("Executing stage $stageName inside wrapper")
                PipelineCategory.wrappers[stageName](body, context.@input)
            }
            else {
                // Closure binding does NOT normally take precedence overy the global script binding
                // to enable local variables to override global ones, ask main binding to override
                Map bindingVariables = (Map)(body.hasProperty("binding") ? ((Binding)body.getProperty('binding')).variables : [:])
                if(body instanceof ParameterizedClosure) {
                    Map resolvedExtras
                    def extras = ((ParameterizedClosure)body).getExtraVariables()
                    if(extras instanceof Closure)
                        resolvedExtras = extras()
                    else
                        resolvedExtras = (Map)extras
                            
                    bindingVariables += resolvedExtras
                }
                        
                List returnedInputs 
                Runner.binding.stageLocalVariables.set(bindingVariables)
                try {
                    returnedInputs = runWrappedBody()
                    this.context.checkAndClearImplicits()
                    runOutstandingChecks()
                }
                finally {
                    Runner.binding.stageLocalVariables.set(null)
                }
                
                if(joiner || (body in Pipeline.currentRuntimePipeline.get().segmentJoiners)) {
                    context.nextInputs = returnedInputs
                }
            }
        }
        catch(PipelineError e) {
            if(e.ctx == null)
                e.ctx = context
            throw e
        }
        finally {
            Pipeline.currentContext.set(null)
            this.running = false
            this.endDateTimeMs = System.currentTimeMillis()
        }
    }
    
    List runWrappedBody() {
        use(PipelineBodyCategory) {
            return Utils.box(body(context.@input)) as List
        }
    }
    
    @CompileStatic
    void overrideBody(Closure stageBranchProp) {
                
        // Note: if the original closure is actually the same, DON'T override
        Closure original = body
        if(body instanceof ParameterizedClosure)  {
            original = ((ParameterizedClosure)body).getBody()
        }
                
        if(original.class.name != stageBranchProp.class.name) {
            body = (Closure) context.branch[stageName]
            log.info "Overriding body for $stageName due to branch variable of type Closure ${context.branch[stageName].hashCode()} containing stage name (new body = " +
                     PipelineCategory.closureNames[body] + "," + PipelineCategory.closureNames[context.branch[stageName]] + ")"
        }
    }
    
    @CompileStatic
    void runOutstandingChecks() {
        for(Checker checker in context.checkers.grep { Checker c -> !c.executed }) {
            log.info "Executing un-executed check $checker.name for stage $stageName"
            checker.otherwise({})
        }
    }
    
    @CompileStatic
    void setContextNextInputs() {
        List<PipelineFile> nextInputs = determineForwardedFiles()
        
        if(!this.context.rawOutput) {
            log.info "No explicit output on stage ${this.hashCode()} context ${this.context.hashCode()}"
            // used to set output to next inputs here but this causes later confusion when 
            // outputs need to be prioritised over inputs
            // (see PipelineCategory#mergeChildStagesToParent)
        }

        context.defaultOutput = null
        log.info "Setting next inputs $nextInputs on stage ${this.hashCode()}, context ${context.hashCode()} in thread ${Thread.currentThread().id}"
        context.nextInputs = nextInputs
    }
    
    /**
     * Identify which files were modified by execution of the stage and 
     * update their saved metadata.
     */
    @CompileStatic
    void saveOutputMetaData() {
        OutputDirectoryWatcher watcher = OutputDirectoryWatcher.getDirectoryWatcher(context.outputDirectory)
        
        // Save the output meta data
        Map<String,Long> modifiedFiles = watcher.modifiedSince(startDateTimeMs)
        
        if(context.executedOutputs.any { !(it.path in modifiedFiles) }) {
            watcher.sync()
            modifiedFiles = watcher.modifiedSince(startDateTimeMs)
        }
        
        if(modifiedFiles.size()<20) {
            log.info "Files modified since $startDateTimeMs in $stageName are $modifiedFiles"
        }
        else {
            log.info "${modifiedFiles.size()} files modified since $startDateTimeMs in $stageName"
        }
        Dependencies.theInstance.saveOutputs(this, modifiedFiles, (List)Utils.box(this.context.@input))
        
    }

    /**
     * Interrogate the PipelineContext and the specified list of 
     * new or modified files to determine an appropriate list of 
     * files that should be forwarded as default inputs to the next 
     * stage in the pipeline.
     * <p>
     * Bpipe operates in two modes here.  If the user has specified
     * outputs for their pipeline stage (eg., using "produce", "transform",
     * or "filter") then those are <i>always</i> preferred.  However if 
     * the user has not specified what the outputs are then the file(s) to be
     * forwarded are chosen from the list of files that are new or modified
     * during the stages execution (presumed to be output files from the stage).
     * 
     * This implemented using the following heuristics:
     * 
     * 1.  if outputs were specified explicitly then use those
     *
     * 2.  if we still don't have anything, default to using the inputs
     *     from the previous stage, assuming that this stage was just
     *     producing "side effects"
     *
     * @return a list of files to send to the next pipeline stage
     *         as default inputs
     */
    @CompileStatic
    private List<PipelineFile> determineForwardedFiles() {
        
        this.context.resolveOutputs()
        
        // Start by initialzing the next inputs from any specifically 
        // set outputs
        if(!context.nextInputs && this.context.rawOutput != null) {
            log.info("Inferring nextInputs from explicit output as ${context.rawOutput}")
            context.nextInputs = this.context.rawOutput
        }
        else {
            log.info "Inputs are NOT being inferred from context.output (context.nextInputs=$context.nextInputs)"
        }

        List<PipelineFile> nextInputs = context.nextInputs
        if(!nextInputs) {
            log.info("Inferring nextInputs from inputs $context.@input")
            nextInputs = this.context.rawInput
        }
        
        return nextInputs.grep { !(it instanceof GlobPipelineFile) }.unique { it.path }
    }
    
    /**
     * Cleanup output files (ie. move them to trash folder).
     * 
     * @param keepFiles    Files that should not be removed
     */
    void cleanupOutputs() {
        
        // Before cleaning up, make sure we resolve the final outputs
        this.context.resolveOutputs()
        
        if(this.context.rawOutput.is(null))
            return
            
        DirtyFileManager.instance.cleanup(context.outputDirectory, context.rawOutput)
    }
    
   
    /**
     * The context provided to a stage can have a set of additional variables 
     * defined in its binding. This method copies them into the binding of the
     * body closure.
     * 
     * @TODO    this possibly should be synchronized?
     * @TODO    remove this?
     */
    void copyContextBindingsToBody() {
		// Note: although it would appear these are being injected at a per-pipeline level,
		// in fact they end up as globally shared variables across all parallel threads
		// (there IS only ONE binding for a closure and only ONE closure instance getting 
		// executed, even by multiple threads). All per-thread state is maintained inside
		// the PipelineContext.
        if(body.properties.containsKey("binding")) {
             this.context.extraBinding.variables.each { k,v ->
                 if(!body.binding.variables.containsKey(k)) {
                     body.binding.variables[k] = v
                 }
             } 
        }
    }
    
    @CompileStatic
    boolean isJoiner() {
//        println "Checking ${context.pipelineJoiners.size()} pipeline joiners"
        this.body in this.context.pipelineJoiners
    }
    
    @CompileStatic
    private String calculateDisplayName(Pipeline pipeline) {
       if(pipeline.branch) {
           return stageName + " ("  + pipeline.branch.getFirstNonTrivialName() + ")"
       }
       else {
           return stageName 
       }
    }
    
    Map toProperties() {
        return [
                id : this.id,
                stageName : this.stageName,
                startMs : this.startDateTimeMs,
                endMs : this.endDateTimeMs,
                branch: this.context.branch.toString(),
                threadId: this.context.threadId,
                succeeded: this.succeeded
            ]
    }
    
    String toString() {
        toProperties().toString()
    }
    
}
