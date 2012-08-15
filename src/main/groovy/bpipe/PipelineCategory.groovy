/*
 * Copyright (c) 2012 MCRI, authors
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import groovy.util.logging.Log;
import java.util.regex.Pattern;

import org.codehaus.groovy.runtime.StackTraceUtils;

import static Utils.*

/**
 * A category that adds default Bpipe functions to closures
 * to enable basic operators such as the + operator to work.
 */
@Log
class PipelineCategory {
    
    static Map closureNames = [:]
    
    /**
     * Map of stage name to body wrapper - a closure that should be 
     * called instead of the body, passing the body as a parameter.
     * This is how predeclared Transform and Filters work.
     */
    static Map wrappers = [:]
    
    static Closure getAt(Closure c, String... params) {
        return c
    }
   
    static Closure cfg(Closure c, Map params) {
        def pc = new ParameterizedClosure(params, c)
        if(closureNames.containsKey(c))
            closureNames[pc] = closureNames[c]
        return pc
    }
    
   static Closure using(Closure c, Map params) {
        cfg(c,params)
    }
    
    static Closure with(Closure c, Map params) {
        cfg(c,params)
    }
    
    static Closure bitwiseNegate(Closure c, Map params) {
        cfg(c,params)
    }
    
    static Closure cfg(Closure c, Object... args) {
        c.binding.variables["args"] = args
        return c
    }
    
    static Closure using(Closure c, Object... args) {
        cfg(c,args)
    }
    
    static Closure with(Closure c, Object... args) {
        cfg(c,args)
    }
    
    static Closure bitwiseNegate(Closure c, Object... args) {
        cfg(c,args)
    }
	
     /**
     * Joins two closures representing pipeline stages together by
     * creating wrapping closure that executes each one in turn.  This is the 
     * basis of Bpipes's + syntax for joining sequential pipeline stages.
     */
    static Object plus(Closure c, Closure other) {
        
        // What we return is actually a closure to be executed later
        // when the pipeline is run.  
        def result  = {  input1 ->
            
            Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
             
            def currentStage = new PipelineStage(pipeline.createContext(), c)
            pipeline.addStage(currentStage)
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
            pipeline.addStage(currentStage)
            currentStage.run()
            return currentStage.context.nextInputs?:currentStage.context.output
        }
        Pipeline.currentUnderConstructionPipeline.joiners << result
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
            pipeline.addStage(currentStage)
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
    
    static Object multiply(Set objs, List segments) {
        if(!objs) 
            throw new PipelineError("Multiply syntax requires a non-empty list of files or chromosomes, but no entries were in the supplied set")
        
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        
        def multiplyImplementation = { input ->
            
            log.info "multiply on input $input on set " + objs
            
            def currentStage = new PipelineStage(pipeline.createContext(), {})
            pipeline.addStage(currentStage)
            currentStage.context.setInput(input)
            
            AtomicInteger runningCount = new AtomicInteger()
            
            List chrs = []
            chrs.addAll(objs)
            chrs.sort()
            
            // Now we have all our samples, make a 
            // separate pipeline for each one, and for each parallel stage
            List<Pipeline> childPipelines = []
            List<Runnable> threads = []
            for(Closure s in segments) {
                log.info "Processing segment ${s.hashCode()}"
                chrs.each { chr ->
                    log.info "Creating pipeline to run on chromosome $chr"
                    runningCount.incrementAndGet()
                    Pipeline child = pipeline.fork()
                    currentStage.children << child
                    Closure segmentClosure = s
                    threads << {
                            try {
                                // First we make a "dummy" stage that contains the inputs
                                // to the next stage as outputs.  This allows later logic
                                // to find these "inputs" correctly when it expects to see
                                // all "inputs" reflected as some output of an earlier stage
                                PipelineContext dummyPriorContext = pipeline.createContext()
                                PipelineStage dummyPriorStage = new PipelineStage(dummyPriorContext,{})
                                dummyPriorContext.output = input
                                
                                log.info "Adding dummy prior stage for thread ${Thread.currentThread().id} with outputs : $dummyPriorContext.output"
                                pipeline.addStage(dummyPriorStage)
                                child.variables += [chr: chr.name]
                                child.name = chr.name
                                child.runSegment(input, segmentClosure, runningCount)
                            }
                            catch(Exception e) {
                                log.log(Level.SEVERE,"Pipeline segment in thread " + Thread.currentThread().name + " failed with internal error: " + e.message, e)
                                StackTraceUtils.sanitize(e).printStackTrace()
                                child.failed = true
                            }
                    } as Runnable
                    childPipelines << child
                }
            }
            return runAndWaitFor(currentStage, childPipelines, threads, runningCount)
        }
        
        log.info "Joiners for pipeline " + pipeline.hashCode() + " = " + pipeline.joiners
        pipeline.joiners << multiplyImplementation
        
        return multiplyImplementation
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
        segments = segments.collect { 
            if(it instanceof List) {
                return multiply("*",it)
            }
            else 
                return it
        }
        
        def multiplyImplementation = { input ->
            
            log.info "multiply on input $input with pattern $pattern"
            
            def currentStage = new PipelineStage(pipeline.createContext(), {})
            pipeline.addStage(currentStage)
            currentStage.context.setInput(input)
            
            // Match the input
            InputSplitter splitter = new InputSplitter()
            Map samples = splitter.split(pattern, input)
            
            if(samples.isEmpty()) 
                if(input)
                    throw new PipelineError("The pattern provided '$pattern' did not match any of the files provided as input $input")
                else
                    throw new PatternInputMissingError("An input pattern was specified '$pattern' but no inputs were given when Bpipe was run.")
                
            AtomicInteger runningCount = new AtomicInteger()
            
            // Now we have all our samples, make a 
            // separate pipeline for each one, and for each parallel stage
            List<Pipeline> childPipelines = []
            List<Runnable> threads = []
            for(Closure s in segments) {
                log.info "Processing segment ${s.hashCode()}"
                samples.each { id, files ->
                    log.info "Creating pipeline to run parallel segment $id with files $files"
                    runningCount.incrementAndGet()
                    
                    Pipeline child = pipeline.fork()
                    currentStage.children << child
                    Closure segmentClosure = s
                    threads << {
                            try {
                                // First we make a "dummy" stage that contains the inputs
                                // to the next stage as outputs.  This allows later logic
                                // to find these "inputs" correctly when it expects to see
                                // all "inputs" reflected as some output of an earlier stage
                                PipelineContext dummyPriorContext = pipeline.createContext()
                                PipelineStage dummyPriorStage = new PipelineStage(dummyPriorContext,{})
                                dummyPriorContext.output = files
                                dummyPriorContext.@input = files
                                
                                log.info "Adding dummy prior stage for thread ${Thread.currentThread().id} with outputs : $dummyPriorContext.output"
                                child.addStage(dummyPriorStage)
                                child.runSegment(files, segmentClosure, runningCount)
                            }
                            catch(Exception e) {
                                log.log(Level.SEVERE,"Pipeline segment in thread " + Thread.currentThread().name + " failed with internal error: " + e.message, e)
                                StackTraceUtils.sanitize(e).printStackTrace()
                                child.failed = true
                            }
                        } as Runnable
                    childPipelines << child
                }
            }
            return runAndWaitFor(currentStage, childPipelines, threads, runningCount)
        }
        
        log.info "Joiners for pipeline " + pipeline.hashCode() + " = " + pipeline.joiners
        pipeline.joiners << multiplyImplementation
        
        return multiplyImplementation
    }
    
    static runAndWaitFor(PipelineStage currentStage, List<Pipeline> pipelines, List<Runnable> threads, AtomicInteger runningCount) {
            // Start all the threads
            log.info "Creating thread pool with " + Config.config.maxThreads + " threads to execute parallel pipelines"
            ThreadPoolExecutor pool = Executors.newFixedThreadPool(Config.config.maxThreads, { Runnable r ->
              def t = new Thread(r)  
              t.setDaemon(true)
              return t
            } as ThreadFactory)
            
            try {
                threads.each { pool.execute(it) }
                
                long lastLogTimeMillis = 0
                while(runningCount.get()) {
                    
                    if(lastLogTimeMillis < System.currentTimeMillis() - 5000) {
                        log.info("Waiting for " + runningCount.get() + " parallel stages to complete (pool.active=${pool.activeCount} pool.tasks=${pool.taskCount})" )
                        lastLogTimeMillis = System.currentTimeMillis()
                    }
                        
                    synchronized(runningCount) {
                        runningCount.wait(50)
                    }
                    
                    if(runningCount.get())
                        Thread.sleep(300)
                    // TODO: really here we should check if any of the pipelines that finished 
                    // have failed so that we can abort the other processes if they did
                }
            }
            finally {
				log.info "Shutting down thread pool (pool.active=${pool.activeCount} pool.tasks=${pool.taskCount})" 
                pool.shutdownNow()
				log.info "Thread pool shut down"
            }
            
            if(pipelines.any { it.failed }) {
                // TODO: make a much better error message!
                throw new PipelineError("One or more parallel stages aborted")
            }
            
            def nextInputs = []
            pipelines.eachWithIndex { Pipeline c,i ->
                def out = c.stages[-1].context.nextInputs
                log.info "Outputs from child $i :  $out context=${c.stages[-1].context.hashCode()}"
                if(out)
                    nextInputs += out
            }
            currentStage.context.output = nextInputs
            
            Utils.checkFiles(currentStage.context.output)
            
            return nextInputs
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
    
    static String getPrefix(String value) {
        return value.replaceAll('\\.[^\\.]*?$', '')    
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
