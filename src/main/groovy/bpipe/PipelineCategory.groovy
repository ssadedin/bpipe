/*
 * Copyright (c) 2011 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe

import groovy.lang.Closure;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static Utils.*


/**
 * A category that adds default Bpipe functions to closures
 * to enable basic operators such as the + operator to work.
 */
class PipelineCategory {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineCategory");
    
    static Map closureNames = [:]
    
    /**
     * Map of stage name to body wrapper - a closure that should be 
     * called instead of the body, passing the body as a parameter.
     * This is how predeclared Transform and Filters work.
     */
    static Map wrappers = [:]
    
    /**
     * Joins two closures representing pipeline stages together by
     * creating wrapping closure that executes each one in turn.  This is the 
     * basis of Bpipes's + syntax for joining sequential pipeline stages.
     */
    static Object plus(Closure c, Closure other) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        
		// What we return is actually a closure to be executed later
		// when the pipeline is run.  
        def result  = {  input1 ->
            
            def currentStage = new PipelineStage(pipeline.createContext(), c)
            pipeline.stages << currentStage
            currentStage.context.setInput(input1)
            currentStage.run()
            Utils.checkFiles(currentStage.context.output)
                    
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.context.nextInputs
            log.info "Next inputs from stage = $nextInputs"
            if(nextInputs == null) {
                nextInputs = currentStage.context.@input
            }
                
            log.info "Checking inputs for next stage:  $nextInputs"
            Utils.checkFiles(nextInputs)
                
            currentStage = new PipelineStage(pipeline.createContext(), other)
            currentStage.context.@input = nextInputs
            pipeline.stages << currentStage
            currentStage.run()
            return currentStage.context.nextInputs?:currentStage.context.output
        }
        pipeline.joiners << result
        return result
    }
    
    /**
     * Take the output from the given closure and forward
     * all of them to all the stages in the list.
     * This is a special case of multiply below. 
     */
    static Object plus(Closure other, List segments) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        Closure mul = multiply("*", segments)
        def plusImplementation =  { input1 ->
            
            def currentStage = new PipelineStage(pipeline.createContext(), other)
            pipeline.stages << currentStage
            currentStage.context.setInput(input1)
            currentStage.run()
            Utils.checkFiles(currentStage.context.output)
                    
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.context.nextInputs
            if(nextInputs == null)
                nextInputs = currentStage.context.@input
                
            Utils.checkFiles(nextInputs)
            return mul(nextInputs)
		}
        pipeline.joiners << plusImplementation
        return plusImplementation
	}
    
    /**
     * Implements the syntax that allows an input filter to 
     * break inputs into samples and pass to multiple parallel 
     * stages in the form
     * <p>
     * <code>"sample_%_*.txt" * [stage1 + stage2 + stage3]</code>
     */
	static Object multiply(String pattern, List segments) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
		def multiplyImplementation = { input ->
            
			log.info "multiply on input $input with pattern $pattern"
            
            def currentStage = new PipelineStage(pipeline.createContext(), {})
            pipeline.stages << currentStage
            currentStage.context.setInput(input)
            
			// Match the input
            InputSplitter splitter = new InputSplitter()
            Map samples = splitter.split(pattern, input)
			
            AtomicInteger runningCount = new AtomicInteger()
            
            // Now we have all our samples, make a 
			// separate pipeline for each one, and for each parallel stage
           List<Pipeline> childPipelines = []
           List<Thread> threads = []
           for(Closure s in segments) {
                log.info "Processing segment ${s.hashCode()}"
				samples.each { id, files ->
	                log.info "Creating pipeline to run sample $id with files $files"
	                runningCount.incrementAndGet()
	                Pipeline child = pipeline.fork()
                    Closure segmentClosure = s
	                Thread childThread = new Thread({
	                    // Each sample will decrement the running count as it finishes
	                    child.runSegment(files, segmentClosure, runningCount)
	                })
                    childPipelines << child
                    threads << childThread
				}
            }
           
           // Start all the threads
           threads.each { it.start() }
            
			while(runningCount.get()) {
				log.info("Waiting for " + runningCount.get() + " parallel stages to complete" )
                synchronized(runningCount) {
                    runningCount.wait(5)
                }
                // TODO: really here we should check if any of the pipelines that finished 
                // have failed so that we can abort the other processes if they did
			}
            
            if(childPipelines.any { it.failed }) {
                // TODO: make a much better error message!
                throw new PipelineError("One or more parallel stages failed")
            }
            
            def nextInputs = []
            childPipelines.eachWithIndex { c,i ->
                def out = c.stages[-1].context.nextInputs
                log.info "Outputs from child $i :  $out context=${c.stages[-1].context.hashCode()}"
                if(out)
					nextInputs += out
			}
            currentStage.context.output = nextInputs
            
            Utils.checkFiles(currentStage.context.output)
            
            return nextInputs
		}
        
        pipeline.joiners << multiplyImplementation
        
        return multiplyImplementation
        
	}
    
    static void addStages(Binding binding) {
        binding.variables.each { 
            if(it.value instanceof Closure) {
	            log.info("Found closure variable ${it.key}")
                if(!closureNames.containsKey(it.value))
	                closureNames[it.value] = it.key
            }
        }
    }
    
    /**
     * Add all properties of type Closure belonging to the class of the given 
     * object as known (named) pipeline stages.
     * 
     * @param host
     */
    static void addStages(def host) {
        // Let's introspect the clazz to see what closure attributes it has
        log.info("Adding stages from $host")
        host.metaClass.properties.each { MetaBeanProperty p ->
            try {
                def x = p.getProperty(host)
                if(x instanceof Closure) {
                    log.info("Found pipeline stage ${p.name}")
                    PipelineCategory.closureNames[x] = p.name
                }
            }
            catch(Exception e) {
                // println "Ignoring $p ($e)"
            }
        }
    }
}
