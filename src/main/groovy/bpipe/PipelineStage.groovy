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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Logger;

import groovy.lang.Closure;

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
class PipelineStage {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineStage");
    
    /**
     * File where half processed files will be listed on shutdown
     */
    public static File UNCLEAN_FILE_PATH = new File(".bpipe/unclean")
    
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
    
    PipelineContext context
    
    /**
     * The actual closure that will be executed when this pipeline stage runs
     */
    Closure body
    
    boolean running = false
    
    String stageName = "Unknown"
    
    /**
     * If the name of the stage cannot be determined another way, this name
     * is printed out
     */
    static int stageCount = 1
    
    /**
     * Executes the pipeline stage body, wrapping it with logic and instrumentation
     * to manage the pipeline
     */
    def run() {
        Utils.checkFiles(context.@input)
        if(body.properties.containsKey("binding"))
            body.binding.variables += this.context.extraBinding.variables
        
        def oldFiles = new File(".").listFiles() as List
        try {
            oldFiles.removeAll { f -> IGNORE_NEW_FILE_PATTERNS.any { f.name.matches(it) } }
            def modified = oldFiles.inject([:]) { result, f -> result[f] = f.lastModified(); return result }
            
            boolean joiner = (body in this.context.pipelineJoiners)
            if(!joiner) {
	            stageName = PipelineCategory.closureNames.containsKey(body) ?
	            PipelineCategory.closureNames[body] : "${stageCount}"
	            println ""
	            println " Stage ${stageName} ".center(Config.config.columns,"=")
			    new File('commandlog.txt').text += '\n'+"# Stage $stageName"
                ++stageCount
                
	            if(context.output == null) {
                    if(context.@input)
		                context.defaultOutput = Utils.first(context.@input) + "." + stageName
	            }
	            log.info("Stage $stageName : INPUT=${context.@input} OUTPUT=${context.output}")
            }   
            context.stageName = stageName
            
            this.running = true
            
            PipelineDelegate.setDelegateOn(context,body)
            
            if(PipelineCategory.wrappers.containsKey(stageName)) {
                log.info("Executing stage $stageName inside wrapper")
                PipelineCategory.wrappers[stageName](body, context.@input)
            }
            else 
	            context.nextInputs = body(context.@input)
            
            if(!joiner)
	            log.info("Stage $stageName returned $context.nextInputs as default inputs for next stage")
                
            UNCLEAN_FILE_PATH.text = ""
            
            // Try using several heuristics to figure out what the inputs passed to the 
            // next pipeline stage should be
            // 1.  if outputs were specified explicitly then use those
            // 2.  if no outputs were specified but we observe new files were created
            //     by the pipeline stage, then use those as long as they don't look
            //     like files that should never be used as input (*.log, *.bai, etc.)
            // 3.  if we still don't have anything, default to using the inputs
            //     from the previous stage, assuming that this stage was just
            //     producing "side effects"
            
            // If after everything we still don't have 
            // any outputs, we look to see if any files were created
            def newFiles = new File(".").list() as Set
            newFiles.removeAll(oldFiles.collect { it.name })
            newFiles.removeAll { n -> IGNORE_NEW_FILE_PATTERNS.any { n.matches(it) } }
            
            // If there are no new files, we can look at modified files instead
            if(!newFiles) {
                newFiles = oldFiles.grep { it.lastModified() > modified[it] }.collect { it.name }
            }
            
            if(!context.nextInputs && this.context.@output != null) {
                log.info("Inferring nextInputs from explicit output as $context.@output")
                context.nextInputs = this.context.output
            }

            def nextInputs = context.nextInputs
            if(nextInputs == null || Utils.isContainer(nextInputs) && !nextInputs) {
                // TODO: Make configurable
                newFiles.removeAll { it.endsWith(".bai") || it.endsWith(".log") }
                if(newFiles) {
                    // If the default output happens to be one of the created files, 
                    // prefer to use that
                    if(context.defaultOutput in newFiles)
                        nextInputs = context.defaultOutput
                    else {
                        // Use the oldest created file.  This means if the 
                        // body actually executed a series of steps we'll use the
                        // last file it made
	                    // nextInputs = newFiles.iterator().next()
                        nextInputs = newFiles.sort { new File(it).lastModified() }.reverse().iterator().next()
                    }
                    log.info "Using next input inferred from created files $newFiles : ${nextInputs}"
                }
            }
            
            if(!nextInputs) {
                log.info("Inferring nextInputs from inputs $context.@input")
                nextInputs = this.context.@input
            }
                
            if(!this.context.output)
                this.context.output = nextInputs

			context.defaultOutput = null
            context.nextInputs = nextInputs
        }
        catch(PipelineTestAbort e) {
            throw e
        }
        catch(Exception e) {
            // Out of caution we don't remove output files if they existed before this stage ran.
            // Otherwise we might destroy existing data
            if(this.context.output != null) {
	            def newOutputFiles = Utils.box(this.context.output)
                log.info("Retaining pre-existing files $oldFiles from outputs")
                newOutputFiles.removeAll { fn ->
                    def canonical = new File(fn).canonicalPath
                    oldFiles.any { 
                        // println "Checking $it vs $fn :" + (it.canonicalPath ==  canonical)
	                    return it.canonicalPath == canonical
	                }
                }
				log.info("Cleaning up: $newOutputFiles")
	            Utils.cleanup(newOutputFiles)
            }
            throw e
        }
        Utils.checkFiles(context.output,"output")
        
        return context.nextInputs
    }

}