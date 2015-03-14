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

import groovy.util.logging.Log

class PipelineBodyCategory {
	static String getPrefix(String value) {
		return value.replaceAll('\\.[^\\.]*?$', '')
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
 * 
 * @author ssadedin@mcri.edu.au
 */
@Log
class PipelineStage {
    
    /**
     * When new files are autodetected by the pipeline manager certain files
     * are treated as being unlikely to be actual outputs from the pipeline
     * that should be passed to next stages
     */
    static IGNORE_NEW_FILE_PATTERNS = ["commandlog.txt", ".*\\.log"]
    
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
    
    String stageName = "Unknown"
    
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
     * Executes the pipeline stage body, wrapping it with logic and instrumentation
     * to manage the pipeline. 
     * <p>
     * After the body has been executed, the output files are interrogated to check
     * they are valid and exist and then they are connected to the next 
     * pipeline stage.
     */
    def run() {
        
        // Cache the original inputs for reference when searching back up through 
        // the pipeline for inputs matching a pattern
        this.originalInputs = this.context.@input
        
        Dependencies.instance.checkFiles(context.@input)
        
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
        
        succeeded = false
        List<File> oldFiles = new File(context.outputDirectory).listFiles() as List
        oldFiles = oldFiles?:[]
        boolean joiner = (body in this.context.pipelineJoiners)
        
        if(!joiner)
            this.synthetic = false
        
		// The name used for displaying this stage
		String displayName = "Unknown Stage"
        try {
            oldFiles.removeAll { File f -> IGNORE_NEW_FILE_PATTERNS.any { f.name.matches(it) } || f.isDirectory() }
            def modified = oldFiles.inject([:]) { result, f -> result[f] = f.lastModified(); return result }
            
            def pipeline = Pipeline.currentRuntimePipeline.get()
            if(!joiner) {
                stageName = 
                    PipelineCategory.closureNames.containsKey(body) ? PipelineCategory.closureNames[body] : "${stageCount}"
	            context.stageName = stageName
				
				displayName = pipeline.name ? "$stageName [" + pipeline.name.replaceAll('\\.[^.]*$','') + "]" : stageName
                    
                context.outputLog.flush("\n"+" Stage ${displayName} ".center(Config.config.columns,"="))
                CommandLog.cmdLog << "# Stage $displayName"
                ++stageCount
                
                EventManager.instance.signal(PipelineEvent.STAGE_STARTED, "Starting stage $displayName", [stage:this])
                
                if(context.@output == null && context.@defaultOutput == null) {
                    if(context.@input) {
                        // If we are running in a sub-pipeline that has a name, make sure we
                        // reflect that in the output file name.  The name should only be applied to files
                        // produced form the first stage in the sub-pipeline
                        if(pipeline.name && !pipeline.nameApplied) {
                            context.defaultOutput = Utils.first(context.@input) + "." + pipeline.name + "."+stageName
                            // Note we don't set pipeline.nameApplied = true here
                            // if it is really applied then that is flagged in PipelineContext
                            // Setting the applied flag here will stop it from being applied
                            // in the transform / filter constructs 
                        }
                        else {
                                context.defaultOutput = Utils.first(context.@input) + "." + stageName
                        }
                    }
                    else
                        context.defaultOutput = stageName
                }
                log.info("Stage $displayName : INPUT=${context.@input} OUTPUT=${context.defaultOutput}")
            }   
            context.stageName = stageName
            
            if(stageName in Config.config.breakAt)
                Config.config.breakTriggered = true
            
            // Execute the actual body of the pipeline stage
            runBody()
                
            succeeded = true
            if(!joiner) {
                log.info("Stage $displayName returned $context.nextInputs as default inputs for next stage")
            }
                
            context.uncleanFilePath.text = ""
            
            def newFiles = findNewFiles(oldFiles, modified)
            def nextInputs = determineForwardedFiles(newFiles)
                
            if(!this.context.@output) {
                // log.info "No explicit output on stage ${this.hashCode()} context ${this.context.hashCode()} so output is nextInputs $nextInputs"
                this.context.rawOutput = nextInputs 
            }

            context.defaultOutput = null
            log.info "Setting next inputs $nextInputs on stage ${this.hashCode()}, context ${context.hashCode()} in thread ${Thread.currentThread().id}"
            context.nextInputs = nextInputs
            
            Dependencies.instance.saveOutputs(context, oldFiles, modified, Utils.box(this.context.@input))
        }
        catch(UserTerminateBranchException e) {
            throw e
        }
        catch(PipelineTestAbort e) {
            throw e
        }
        catch(Throwable e) {
            
            if(!succeeded && !joiner) 
                EventManager.instance.signal(PipelineEvent.STAGE_FAILED, "Stage $displayName has Failed")
            
            log.info("Retaining pre-existing files $oldFiles from outputs")
            cleanupOutputs(oldFiles)
            throw e
        }
		finally {
            if(!joiner) {
                if(!context.uncleanFilePath.delete())
                    log.warning("Unable to delete in-progress command file $context.uncleanFilePath")
                    
	            EventManager.instance.signal(PipelineEvent.STAGE_COMPLETED, "Finished stage $displayName", [stage:this])
            }
		}
        
        log.info "Checking files: " + context.@output
        Dependencies.instance.checkFiles(context.@output,"output")
        
        // Save the database of files created
//        if(Config.config.enableCommandTracking)
        
        return context.nextInputs
    }

    /**
     * Execute the body of the pipeline stage
     * @return
     */
	private runBody() {
		this.running = true
        
        Closure actualBody = body
        
        // Check if the closure is overridden in this branch
        if(context.branch.getProperty(stageName) && context.branch[stageName] instanceof Closure) {
            log.info "Overriding body for $stageName due to branch variable of type Closure containing stage name"
            body = context.branch[stageName]
        }
        
		PipelineDelegate.setDelegateOn(context,body)
		this.startDateTimeMs = System.currentTimeMillis()
		try {
            Pipeline.currentContext.set(context)
			if(PipelineCategory.wrappers.containsKey(stageName)) {
				log.info("Executing stage $stageName inside wrapper")
				PipelineCategory.wrappers[stageName](body, context.@input)
			}
			else {
				use(PipelineBodyCategory) {
					def returnedInputs = body(context.@input)
					if(joiner) {
						context.nextInputs = returnedInputs
					}
				}
			}
		}
        catch(PipelineError e) {
            e.ctx = context
            throw e
        }
		finally {
            Pipeline.currentContext.set(null)
			this.running = false
			this.endDateTimeMs = System.currentTimeMillis()
		}
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
     * 2.  if no outputs were specified but we observe new files were created
     *     by the pipeline stage, then use those as long as they don't look
     *     like files that should never be used as input (*.log, *.bai, etc.)
     * 3.  if we still don't have anything, default to using the inputs
     *     from the previous stage, assuming that this stage was just
     *     producing "side effects"
     *
     * If after everything we still don't have any outputs, we look to see if 
     * any files were created
     * 
     * @param newFiles    Files that were created or modified in the execution
     *                     of this pipeline stage
     * @return String|List<String>    a list of files to send to the next pipeline stage
     *                                 as default inputs
     */
    private determineForwardedFiles(List newFiles) {
        
        this.resolveOutputs()
        
        // Start by initialzing the next inputs from any specifically 
        // set outputs
        if(!context.nextInputs && this.context.@output != null) {
            log.info("Inferring nextInputs from explicit output as ${context.@output}")
            context.nextInputs = Utils.box(this.context.@output).collect { it.toString() }
        }

        // No output OR forward inputs were specified by the pipeline stage?!
        // well, we have one last, very special case resort: if a file was created
        // that exactly matches the unique file name we WOULD have generated IF the 
        // user had referenced $output - then we accept this
        // @TODO: review possibly remove this if it does not break any obvious
        //        useful functionality.
        def nextInputs = context.nextInputs
        if(nextInputs == null || Utils.isContainer(nextInputs) && !nextInputs) {
            log.info "Removing inferred outputs matching $context.outputMask"
            newFiles.removeAll {  fn -> context.outputMask.any { fn ==~ '^.*' + it } }

            if(newFiles) {
                // If the default output happens to be one of the created files,
                // prefer to use that
                log.info "Comparing default output $context.defaultOutput to new files $newFiles"
                if(context.defaultOutput in newFiles) {
                    nextInputs = context.defaultOutput
                    log.info("Found default output $context.defaultOutput among detected new files:  using it")
                }
            }
        }

        if(!nextInputs) {
            log.info("Inferring nextInputs from inputs $context.@input")
            nextInputs = this.context.@input
        }
        
        if(nextInputs instanceof PipelineOutput) {
            nextInputs = nextInputs.toString()
            if(nextInputs == "null")
                nextInputs = null
        }
            
        return nextInputs
    }
    
    /**
     * After a pipeline stage executes outputs can be directly in outputs or they
     * can have been inferred through implicit references to file extensions on the
     * output variable.
     */
    private resolveOutputs() {
        if(!context.@output && context.allInferredOutputs) {
            log.info "Using inferred outputs $context.allInferredOutputs as outputs because no explicit outputs set"
            context.@output = context.allInferredOutputs
        }
    }

    /**
     * Finds either:
     * <ul>
     *   <li>all the files in the output directory not in the
     *      specified <code>oldFiles</code> List
     *   <li><b>or</b> all the files in the <code>oldFiles</code>
     *       list that have been modified since the timestamps
     *       recorded in the <code>timestamps</code> hash
     *     
     * Certain predetermined files are ignored and never returned
     * (see IGNORE_NEW_FILE_PATTERNS).
     * 
     * @param oldFiles        List of {@link File} objects
     * @param timestamps    List of previous timestamps (long values) of the oldFiles files
     */
    protected List<String> findNewFiles(List oldFiles, HashMap<File,Long> timestamps) {
        def newFiles = (new File(context.outputDirectory).listFiles().grep {!it.isDirectory() }*.name) as Set
		
        newFiles.removeAll(oldFiles.collect { it.name })
        newFiles.removeAll { n -> IGNORE_NEW_FILE_PATTERNS.any { n.matches(it) } }

        // If there are no new files, we can look at modified files instead
        if(!newFiles) {
            newFiles = oldFiles.grep { it.lastModified() > timestamps[it] }.collect { it.name }
        }

        // Since we operated on local file names only so far, we have to restore the
        // output directory to the name
        return newFiles.collect { context.outputDirectory + "/" + it }
    }
    
    /**
     * Cleanup output files (ie. move them to trash folder).
     * 
     * @param keepFiles    Files that should not be removed
     */
    void cleanupOutputs(List<File> keepFiles) {
        
        // Before cleaning up, make sure we resolve the final outputs
        this.resolveOutputs()
        
        // Out of caution we don't remove output files if they existed before this stage ran.
        // Otherwise we might destroy existing data
        if(this.context.@output != null) {
            def newOutputFiles = Utils.box(this.context.@output).collect { it.toString() }.unique()
            newOutputFiles.removeAll { fn ->
                def canonical = new File(fn).canonicalPath
                keepFiles.any {
                    // println "Checking $it vs $fn :" + (it.canonicalPath ==  canonical)
                    return it.canonicalPath == canonical
                }
            }
            log.info("Cleaning up: $newOutputFiles")
            List<String> failed = Utils.cleanup(newOutputFiles)
            if(failed) {
                markDirty(failed)
            }
        }
    }
    
    /**
     * Record the given file as dirty - ie: should be cleaned up
     * @param file
     * @return
     */
    synchronized static markDirty(List<String> files) {
        files.collect { new File(it).canonicalFile }.each { File file ->
            File dirtyFile = new File(".bpipe/dirty.txt")
            List<String> dirtyFiles = []
            if(dirtyFile.exists()) {
                dirtyFiles = dirtyFile.readLines() 
            }
            dirtyFiles.add(file.absolutePath)
            dirtyFile.text = dirtyFiles.join("\n")
        }
    }
    
    boolean isJoiner() {
        this.body in this.context.pipelineJoiners
    }
    
    Map toProperties() {
        return [
                stageName : this.stageName,
                startMs : this.startDateTimeMs,
                endMs : this.endDateTimeMs,
                branch: this.context.branch.toString(),
                threadId: this.context.threadId
            ]
    }
}
