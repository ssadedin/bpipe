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
import groovy.transform.CompileStatic

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import groovy.util.logging.Log;

import java.util.regex.Pattern;

import org.codehaus.groovy.runtime.StackTraceUtils;

import bpipe.storage.LocalPipelineFile
import bpipe.storage.StorageLayer
import bpipe.storage.UnknownStoragePipelineFile

/**
 * A category that adds default Bpipe functions to closures
 * to enable basic operators such as the + operator to work.
 */
class PipelineCategory {
    
    static Logger log = Logger.getLogger('PipelineCategory')
    
    static Map closureNames = [:]
    
    /**
     * Map of stage name to body wrapper - a closure that should be 
     * called instead of the body, passing the body as a parameter.
     * This is how predeclared Transform and Filters work.
     */
    static Map<String,Closure> wrappers = [:]
    
    static Closure getAt(Closure c, String... params) {
        return c
    }
   
    static Closure cfg(Closure c, def params) {
        def pc = new ParameterizedClosure(params, c)
        if(closureNames.containsKey(c))
            closureNames[pc] = closureNames[c]
        return pc
    }
    
   static Closure using(Closure c, Closure params) {
        cfg(c,params)
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
    
    static String quote(String value) {
        Utils.quote(value)
    }
    
    static Closure power(List list, Closure c) {
        sequentially(list,c)
    }
    
    static Closure sequentially(List list, Closure c) {
        List nameSetters = []
        Closure result = list.collect { n -> 
            def nameSetter = { it -> branch.name = n; return null; } 
            
            nameSetters << nameSetter
            
            return nameSetter + c(n)
            
        }.sum()
        
        Pipeline.currentUnderConstructionPipeline.joiners.addAll(nameSetters)
        
        return result
    }
    
    static Object rightShiftUnsigned(Closure l, Closure b) {
        return plus(l, b, true) 
    }
  
    static Object rightShiftUnsigned(Closure l, List b) {
        return plus(l, b, true) 
    }
     
    static Object plus(List l, Closure c) {
        def j = {
            return it
        }
        Pipeline.currentUnderConstructionPipeline.joiners << j
        return plus(j, l) + c
    }
    
    
    static Object plus(List l, List other) {
        if(!l.empty && !other.empty && l[0] instanceof Closure && other[0] instanceof Closure) {
            def j = {
                return it
            }
            Pipeline.currentUnderConstructionPipeline.joiners << j
            return plus(j, l) + other
        }
        else
            return org.codehaus.groovy.runtime.DefaultGroovyMethods.plus(l,other)
    }
	
    static Object plus(Closure c, Closure other) {
        return plus(c,other,false)
    }
        
     /**
     * Joins two closures representing pipeline stages together by
     * creating wrapping closure that executes each one in turn.  This is the 
     * basis of Bpipes's + syntax for joining sequential pipeline stages.
     */
    @CompileStatic
    static Object plus(Closure c, Closure other, boolean mergePoint) {
        
        // log.info "Closure["+PipelineCategory.closureNames[c] + "] + Closure[" + PipelineCategory.closureNames[other] + "]"
        
        // What we return is actually a closure to be executed later
        // when the pipeline is run.  
        def result  = {  input1 ->
            
            Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
            
            PipelineStage currentStage = new PipelineStage(pipeline.createContext(), c)
            pipeline.addStage(currentStage)
            currentStage.context.setInput(input1)
            currentStage.run()
            
            log.info "Checking that outputs ${currentStage.context.@output} exist"
            Dependencies.theInstance.checkFiles(currentStage.context.@output, pipeline.aliases)
                    
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.context.nextInputs
            log.info "Next inputs from stage = $nextInputs"
            if(nextInputs == null) {
                nextInputs = currentStage.context.@input
            }
                
            log.info "Checking inputs for next stage:  $nextInputs"
            Dependencies.theInstance.checkFiles(nextInputs, pipeline.aliases)
            
            currentStage = new PipelineStage(pipeline.createContext(), other)
            currentStage.context.@input = nextInputs
            
            if(mergePoint) 
                pipeline.setMergePoint(currentStage)
  
            pipeline.addStage(currentStage)
            currentStage.run()
            return currentStage.context.nextInputs?:currentStage.context.@output
        }
        Pipeline.currentUnderConstructionPipeline.joiners << result
        return result
    }
    
    /**
     * Take the output from the given closure and forward
     * all of them to all the stages in the list.
     * This is a special case of multiply below. 
     */
    @CompileStatic
    static Closure plus(Closure other, List segments, boolean mergePoint=false) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        Closure mul = splitOnFiles("*", segments, false, false,mergePoint)
        Closure plusImplementation =  { input1 ->
            
            Pipeline runtimePipeline = Pipeline.currentRuntimePipeline.get()
            def currentStage = new PipelineStage(runtimePipeline.createContext(), other)
            runtimePipeline.addStage(currentStage)
            currentStage.context.setInput(input1)
            currentStage.run()
            Dependencies.theInstance.checkFiles(currentStage.context.@output, runtimePipeline.aliases)
                    
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.context.nextInputs
            if(nextInputs == null)
                nextInputs = currentStage.context.@input
                
            Dependencies.theInstance.checkFiles(nextInputs, runtimePipeline.aliases)
            
            return mul(nextInputs)
        }
        pipeline.joiners << plusImplementation
        return plusImplementation
    }
    
    @CompileStatic
    static Object rightShift(Closure c, String channel) {
        
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        
        Closure rightShiftImplementation = {
            
            Pipeline runtimePipeline = Pipeline.currentRuntimePipeline.get()
            String myChannel = runtimePipeline.channel ?: runtimePipeline.branch.name
            String branchName = runtimePipeline.branch.name

            Pipeline targetPipeline 
            Pipeline targetParent = runtimePipeline.parent

            while(!targetPipeline && !targetParent.is(null)) {
                targetPipeline = targetParent.children.find { 
                    it.channel == channel && it.branch.name == branchName
                }
                
                if(targetPipeline)
                    break
                    
                branchName = targetParent.branch.name
                targetParent = targetParent.parent
            }

            if(!targetPipeline) 
                throw new PipelineError('Unknown channel ' + channel + ' specified to forward results from ' + runtimePipeline.branch.name)
  
            log.info "Register channel $myChannel with $targetPipeline"
            synchronized(targetPipeline.channelResults) {
                targetPipeline.channelResults[myChannel] = (List<PipelineFile>)null // signifying pending
            }
               
            def results = []
            try {
                results = (List<PipelineFile>)Utils.box(c())
            }
            finally {
                log.info "Forwarding results: $results to channel $channel in target pipeline $targetPipeline"
                synchronized(targetPipeline.channelResults) {
                    targetPipeline.channelResults[myChannel] = results.collect { it }
                    targetPipeline.channelResults.notifyAll()
                }
            }

            return results
        }
        
        pipeline.joiners << rightShiftImplementation
        
        return rightShiftImplementation
    }
    
    static Object multiply(List objs, Map segments) {
        multiply(objs.collect { String.valueOf(it) } as Set, segments*.value, segments*.key)
    }

    static Object multiply(List objs, List segments) {
        multiply(objs.collect { String.valueOf(it) } as Set, segments)
    }
    
    static Object multiply(String pattern, Closure c) {
        throw new PipelineError("Multiply syntax requires a list of stages")
    }
    
    static Object multiply(Set objs, List segments) {
        multiply(objs, segments, null)
    }

    static Object multiply(Set objs, List segments, List channels) {
        
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        
        def multiplyImplementation = { input ->
            
            log.info "multiply on input $input on set " + objs
            
            def currentStage = new PipelineStage(Pipeline.currentRuntimePipeline.get().createContext(), {})
            Pipeline.currentRuntimePipeline.get().addStage(currentStage)
            currentStage.context.setInput(input)
            
            // Now we have all our inputs, make a 
            // separate pipeline for each one, and execute each parallel segment
            List<Pipeline> childPipelines = []
            List<Runnable> threads = []
            Pipeline parent = Pipeline.currentRuntimePipeline.get()
            
            if(!objs) {
                if(parent.branch?.name && parent.branch.name != "all") {
                    println "MSG: Parallel segment inside branch $branch.name will not execute because the list to parallelise over is empty"
                }
                else {
                    println "MSG: Parallel segment will not execute because the list to parallelise over is empty"
                }
            }
            
            List chrs = []
            chrs.addAll(objs)
            chrs.sort()
            
            Node branchPoint = parent.addBranchPoint("split")
            int segmentIndex = 0
            for(Closure s in segments) {
                log.info "Processing segment ${s.hashCode()}"
                
                // ForkId is used to track distinct topological paths in the pipeline graph from 
                // physical ones. That is, both of these run the same hello stage twice in parallel, but
                // they are topologically different:
                //
                //     foo + [hello,hello]
                //
                //     ["a","b"] * [hello] 
                //
                // in the first case, they will receive different forkIds
                // in the second case they'll have the same forkId
                //
                String forkId = null
                chrs.each { chr ->
                    
                    if(!Config.config.branchFilter.isEmpty() && !Config.config.branchFilter.contains(chr)) {
                        System.out.println "Skipping branch $chr because not in branch filter ${Config.config.branchFilter}"
                        return
                    }
                    
                    log.info "Creating pipeline to run on branch $chr"
                    Pipeline child = Pipeline.currentRuntimePipeline.get().fork(branchPoint, chr.toString(), forkId)
                    if(channels) {
                        child.channel = channels[segmentIndex]
                        log.info "Assigned channel $child.channel to $child"
                    }
                    forkId = child.id
                    currentStage.children << child
                    Closure segmentClosure = s
                    threads << {
            
                        try {
                            
                            // If the filterInputs option is set, match input files on the region name
                            def childInputs = input
                            
                            child.branch = new Branch()
                            if(chr instanceof Chr) {
                                childInputs = initChildFromChr(child,chr,input)
                                if(childInputs == null)
                                    return
                            }
                            else
                            if(chr instanceof RegionSet) {
                                initChildFromRegionSet(child, chr)
                            }
                            else {
                                child.branch.@name = String.valueOf(chr)
                            }
                            
                            PipelineStage dummyPriorStage = createFilteredInputStage(chr, Utils.box(childInputs), pipeline)
                            child.addStage(dummyPriorStage)
                            child.runSegment(childInputs, segmentClosure)
                        }
                        catch(Exception e) {
                            log.log(Level.SEVERE,"Pipeline segment in thread " + Thread.currentThread().name + " failed with internal error: " + e.message, e)
                            Runner.reportExceptionToUser(e)
                            Concurrency.instance.unregisterResourceRequestor(child)
                            child.failed = true
                        }
                    } as Runnable
                    childPipelines << child
                }
                ++segmentIndex
            }
            return runAndWaitFor(currentStage, childPipelines, threads)
        }
        
//        log.info "Joiners for pipeline " + pipeline.hashCode() + " = " + pipeline.joiners
        pipeline.joiners << multiplyImplementation
        
        return multiplyImplementation
    }
    
    /**
     * Create a fake pipeline stage that exists for the purpose of setting 
     * a particular set of inputs to a child branch
     * 
     * @param branchObject  object that identifies the branch
     * @param childInputs   input set that the child branch should see
     * @param pipeline      pipeline that will be the parent of the child branch
     * 
     * @return  a PipelineStage object that will filter inputs to the list 
     *          given
     */
    @CompileStatic
    static PipelineStage createFilteredInputStage(def branchObject, List childInputs, Pipeline pipeline) {
        // First we make a "dummy" stage that contains the inputs
        // to the next stage as outputs.  This allows later logic
        // to find these "inputs" correctly when it expects to see
        // all "inputs" reflected as some output of an earlier stage
        PipelineContext dummyPriorContext = pipeline.createContext()
        PipelineStage dummyPriorStage = new PipelineStage(dummyPriorContext,{})
        dummyPriorContext.stageName = dummyPriorStage.stageName = "_${branchObject}_inputs"
                                
        // Note: must be raw output because otherwise the original inputs (from other folders)
        // can get redirected to the output folder
        dummyPriorContext.setRawOutput(childInputs)
        
        log.info "Created dummy prior stage for thread ${Thread.currentThread().id} with outputs : $dummyPriorContext.output"
        return dummyPriorStage
    }
    
    static void initChildFromRegionSet(Pipeline child, RegionSet regions) {
        RegionValue regionValue = 
            new RegionValue(regions.sequences.values())
                                        
        child.variables.region = regionValue
        child.branch.region = regionValue
        child.branch.chr = regions.sequences.keySet().iterator().next()
        child.branch.name = regionValue.id
        child.branch.@name = regionValue.id
        
        log.info "Set region value for $child to $regionValue"
    }
    
    /**
     * Configure a child pipeline to execute for a specific chromosome
     * 
     * @param child
     * @param chr
     * @return a list of inputs to use for the child, or null if the child should not execute
     */
    static def initChildFromChr(Pipeline child, Chr chr, List<PipelineFile> input) {
        
        List<PipelineFile> childInputs = input 
        if(childInputs == null)
            childInputs = []
        
        String chrName = chr.name
        
        def filterInputs = chr instanceof Chr ? chr.config?.filterInputs : "auto"
                            
        if(filterInputs == "auto") { 
            if(Config.userConfig.autoFilter!="false") {
                // log.info "Checking for auto filter - inputs matching chr pattern are: " + Utils.box(input).grep { it.matches(/.*\.chr[1-9A-Z_]*\..*$/) }
                filterInputs = input.any { it.path.matches(/.*\.chr[1-9A-Z_]*\..*$/) }
            }
            else
                filterInputs = false
        }
                            
        if(filterInputs && (chr instanceof Chr)) {
            log.info "Filtering child pipeline inputs on name $chr.name"
                                
            childInputs  = input.grep { i -> (i.path.indexOf('.' + chr.name + '.')>0) }
                                    
            // Since the name of the region is already in the file path, it does not need
            // to be applied again to output files
            child.nameApplied = true
                                    
            if(!childInputs) {
                println "MSG: Skipping region ${chr.name} because no matching inputs were found"
                Concurrency.instance.unregisterResourceRequestor(child)
                return null
            }
        }
        
        child.branch = new Branch()
        child.variables.chr = chrName
        child.branch.name = chrName
        child.branch.@name = chrName
        child.branch.chr = chrName
        if(Pipeline.defaultGenome) {
            Map<String,Integer> chromSizes = Pipeline.genomeChromosomeSizes[Pipeline.defaultGenome]
            log.info "Checking if chromosome sizes available for " + Pipeline.defaultGenome + " (chromSizes[$chrName]=${chromSizes[chrName]})"
            if(chromSizes && chromSizes[chrName]) {
                child.branch.region = chrName + ':0-' + chromSizes[chrName]
                child.variables.region = chrName + ':0-' + chromSizes[chrName]
                log.info "Set child branch region for chr $chrName to $child.branch.region based on " + chromSizes[chrName]
            }
        }
        
        return childInputs
    }
    
    static Object multiply(java.util.regex.Pattern pattern, List segments) {
        splitOnFiles(pattern,segments,true)
    }
    
    /**
     * Implements the syntax that allows an input filter to 
     * break inputs into samples and pass to multiple parallel 
     * stages in the form
     * <p>
     * <code>"sample_%_*.txt" * [stage1 + stage2 + stage3]</code>
     */
    static Object multiply(String pattern, List segments) {
        splitOnFiles(pattern,segments,true)
    }
    
    static Object multiply(Map branches, List segments) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
        
        // Ensure the keys and values are normalised to expected structure downstream
        Map<String,List> cleanBranches = branches.collectEntries { e ->
            [String.valueOf(e.key),  Utils.box(e.value)]
        }
        
        def multiplyImplementation = { input ->
            
            def inputs = Utils.box(input)
            
            // Filter so that only inputs actually received are left in the
            // provided map
            // Map filteredBranches = branches.keySet().collectEntries { key -> 
            //   [key, Utils.box(branches[key]).removeAll { !(it in inputs) }]
            //}
            splitOnMap(input, cleanBranches, segments)
        }
        
//        log.info "Joiners for pipeline " + pipeline.hashCode() + " = " + pipeline.joiners
        pipeline.joiners << multiplyImplementation
        return multiplyImplementation
    }
    
    /**
     * Implements the syntax that allows an input filter to 
     * break inputs into samples and pass to multiple parallel 
     * stages in the form
     * <p>
     * <code>"sample_%_*.txt" * [stage1 + stage2 + stage3]</code>
     * 
     * @param pattern   either a String or a Pattern object
     * @param segments  a list of closures to run in paraellel
     * @param requireMatch  if true, the pipeline will fail if there are 
     *                      no matches to the pattern
     */
    static Closure splitOnFiles(def pattern, List segments, boolean requireMatch, boolean sortResults=true, boolean mergePoint=false) {
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get() ?: Pipeline.currentUnderConstructionPipeline
        
        def multiplyImplementation = { input ->
            
//            assert input.every { it instanceof PipelineFile }
            
            log.fine "multiply on input $input with pattern $pattern"
            
            // Match the input
            InputSplitter splitter = new InputSplitter(sortResults:sortResults)
            Map branches = splitter.split(pattern, input)
            
            if(branches.isEmpty() && !requireMatch && pattern == "*")        
                branches["*"] = input
                
            if(branches.isEmpty()) {
                branches = locateMostRecentMatch(pattern, splitter)
            }
            
            if(branches.isEmpty()) 
                if(input)
                    println("Note: a pattern '$pattern' was provided, but did not match any of the files provided as input $input.")
                else
                    throw new PatternInputMissingError("An input pattern was specified '$pattern' but no inputs were given when Bpipe was run.")
                    
            return splitOnMap(input, branches, segments, pattern instanceof String && pattern.contains("/"), mergePoint)
        }
					
        
//        log.info "Joiners for pipeline " + pipeline.hashCode() + " = " + pipeline.joiners
        pipeline.joiners << multiplyImplementation
        
        return multiplyImplementation
    }

    /**
     * Search in reverse through the current pipeline input stack to find the most recent
     * stage that had an input matching the given split pattern. For that stage, return
     * all the inputs matching the pattern.
     */
    private static Map locateMostRecentMatch(final Object pattern, final InputSplitter splitter) {
        List allInputs = Pipeline.currentRuntimePipeline.get().stages*.context.collect { it.@input}
        for(def inps in allInputs.reverse()) {
            log.info "Checking input split match on $inps"
            final Map branches = splitter.split(pattern, inps)
            if(!branches.isEmpty()) {
                log.info "Found input split match for pattern $pattern on $inps"
                return branches
            }
        }
        return [:]
    }
    
    @CompileStatic
    static Object splitOnMap(def input, Map<String, List> branchMap, List segmentObjs /* Closure or List of Closures */, boolean applyName=false, boolean mergePoint=false) {
        
        assert branchMap*.value.every { it instanceof List }
        
        Map<String,List<PipelineFile>> resolvedBranchMap =  (Map<String,List<PipelineFile>>)branchMap.collectEntries { String id, List files ->
            [id, StorageLayer.resolve(files) ]
        }
        
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get() ?: Pipeline.currentUnderConstructionPipeline
        
        // Convert any segments that have been passed as lists to compiled closure form
        List<Closure> segments = convertSegmentLists(segmentObjs)
        
        final PipelineStage currentStage = new PipelineStage(pipeline.createContext(), {})
        final Pipeline parent = Pipeline.currentRuntimePipeline.get()
        parent.addStage(currentStage)
        currentStage.context.setInput(input)
        
        parent.childCount = 0
            
        log.info "Created pipeline stage ${currentStage.hashCode()} for parallel block"
        
        // Now we have all our branches, make a 
        // separate pipeline for each one, and for each parallel stage
        List<Pipeline> childPipelines = []
        List<Runnable> threads = []
		Node branchPoint = parent.addBranchPoint("split")
        for(Closure segmentClosure in segments) {
            log.info "Processing segment ${segmentClosure.hashCode()}"
            
            // See note in multiply(Set objs, List segments) for details on the purpose of forkId
            String forkId = null
            resolvedBranchMap.each { id, files ->
                    
                log.info "Creating pipeline to run parallel segment $id with files $files. Branch filter = ${Config.config.branchFilter}"
                   
				if(isBranchFiltered(id))
                    return
 
                String childName = computeSubBranchName(id, segments, segmentClosure)
                Pipeline child = parent.fork(branchPoint, childName, forkId)
                forkId = child.id
                currentStage.children << child
                if(mergePoint)
                    child.setMergePoint(currentStage)
                threads << new BranchRunner(parent, child, files, childName, segmentClosure, applyName)
                childPipelines << child
            }
        }
        return runAndWaitFor(currentStage, childPipelines, threads)
    }

    private static List convertSegmentLists(List segmentObjs) {
        List<Closure> segments = segmentObjs.collect {
            if(it instanceof List) {
                return multiply("*",it)
            }
            else
                return it
        }
        return segments
    }

    /*
     * Check if branches of the given id are filtered out from running
     */
    private static boolean isBranchFiltered(String id) {
        if(!Config.config.branchFilter.isEmpty() && !Config.config.branchFilter.contains(id)) {
            System.out.println "Skipping branch $id because not in branch filter ${Config.config.branchFilter}"
            return true
        }
        return false
    }

    @CompileStatic
    static List<PipelineFile> runAndWaitFor(PipelineStage currentStage, List<Pipeline> pipelines, List<Runnable> threads) {
            Pipeline current = Pipeline.currentRuntimePipeline.get()
            
            pipelines.each { 
                Concurrency.theInstance.registerResourceRequestor(it)
            }
            
            try {
                current.isIdle = true
                Concurrency.theInstance.execute(threads, current.branchPath.size())
            }
            finally {
                current.isIdle = false
            }
            
            List<PipelineFile> finalOutputs
            if(pipelines.any { it.failed }) {
                throwCombinedParallelError(current, pipelines)
            }
            else
            if(pipelines.isEmpty()) {
                return currentStage.context.@input
            }
            else {
               
                def nextInputs = []
                Pipeline parent = Pipeline.currentRuntimePipeline.get()
                log.info "Parent pipeline of concurent sections ${pipelines.collect { it.hashCode() }} is ${parent.hashCode()}"
                
                mergeChildStagesToParent(parent, pipelines)
    
                if(pipelines.every { it.aborted }) {
                    log.info "Branch will terminate because all of its children aborted successfully"
                    throw new UserTerminateBranchException("All branches [" + pipelines*.branch.unique().join(",") + "] self terminated")
                }
                else
                    return parent.stages[-1].context.@output
            }
    }
    
    /**
     * Compute a unique name for a child branch based on its parent branch and its position
     * within its siblings with respect to its parent.
     */
    @CompileStatic
    static String computeSubBranchName(String defaultName, List<Closure> segments, Closure segmentClosure) {
        String childName = defaultName
        int segmentNumber = segments.indexOf(segmentClosure) + 1
        if(segments.size()>1) {
            if(defaultName == "all")
                childName = segmentNumber.toString()
            else
                childName = defaultName + "." + segmentNumber
        }
        return childName
    }

    /**
     * Combines all the errors from the given pipelines into one summarized error and throws
     * it as an exception, setting the errors on the parent (current) pipeline object.
     * 
     * @param current
     * @param pipelines
     */
    private static void throwCombinedParallelError(Pipeline current, List pipelines) {
        def messages = summarizeErrors(pipelines)

        for(Pipeline p in pipelines.grep { it.failed }) {
            // current.failExceptions.addAll(p.failExceptions)
        }
        current.failReason = messages

        List<PipelineError> childFailures = pipelines.grep { it.failed }*.failExceptions.flatten()

        Exception e
        if(pipelines.every { p -> p.failExceptions.every { it instanceof PipelineTestAbort } }) {
            e = new PipelineTestAbort()
        }
        else {
            e = new SummaryErrorException(childFailures)
        }
        e.summary = true
        throw e
    }
    
    /**
     * Merge 'like' stages from the given pipelines together so that they appear 
     * as single stages and insert them into the current pipeline.
     * Add all 'unlike' stages to the parent separately.
     * 
     * To explain why merging of 'like' stages is useful, consider the pipeline below:
     *
     *       ---- B --- C ----
     * A -- |                 | ----- D
     *       ---- B --- C ----
     *
     * We want Stage D to see a "virtual" flat pipeline where outputs of the B stages
     * and C stages are merged down and the pipeline looks like A -- B -- C -- D.
     * Consider alternatively the following pipeline:
     * 
     *       ---- B --- C ----
     * A -- |                 | ----- F
     *       ---- D --- E ----
     *
     * We want Stage F to see a different "virtual" flat pipeline 
     * looking like this:
     * 
     *   A -- B -- D -- C -- E -- F
     */
    static List mergeChildStagesToParent(final Pipeline parent, final List<Pipeline> pipelines) {
        // Get the output stages without the joiners
        List<List<PipelineStage>> stagesList = pipelines.collect { c -> c.stages.grep { !it.joiner && !(it in parent.stages) && it.stageName != "Unknown" } }
        
        int maxLen = stagesList*.size().max()
        log.info "Maximum child stage list length = $maxLen"
        
        // Fill in shorter stages with nulls
        stagesList = stagesList.collect { it + ([null] * (maxLen - it.size())) }
        
        log.info "###### Merging results of parallel split in parent branch ${parent.branch?.name} #####"
        
        def transposed = stagesList.transpose()
        transposed.eachWithIndex { List<PipelineStage> stagesAtIndex, int i ->
            log.info "Grouping stages at index $i ${stagesAtIndex*.stageName} for merging"
            
            if(stagesAtIndex.size() == 0)
                throw new IllegalStateException("Encountered pipeline segment with zero parallel stages?")
                
            Map<String,List<PipelineStage>> grouped = stagesAtIndex.groupBy { it?.stageName }
            grouped.each { stageName, stages ->
                  
                if(!stageName || !stages)
                    return
                  
                if(stages.size()>1)
                  log.info "Parallel segment $i contains ${stages.size()} identical ${stageName} stages - Merging outputs to single stage"
                  
                // Create a merged stage
                PipelineContext mergedContext = new PipelineContext(null, parent.stages, stages[0].context.pipelineJoiners, stages[0].context.branch)
                def mergedOutputs = stages.collect { s ->
                    Utils.box(s.context.@output)
                }.sum()
                
                def mergedNextInputs = stages.collect { s ->
                    Utils.box(s?.context?.nextInputs)
                }.sum() 
                
                log.info "Merged outputs for $stageName(s) are $mergedOutputs"
                mergedContext.setRawOutput(mergedOutputs)
                mergedContext.setNextInputs(mergedNextInputs)
                  
                PipelineStage mergedStage = new PipelineStage(mergedContext, stages[0].body)
                mergedStage.stageName = stages[0].stageName
                parent.addStage(mergedStage)
            }
        }
        
        // Finally add a merged stage that has all the outputs from the last stages
        log.info "There are ${transposed.size()} stages from nested pipelines"
        List<PipelineStage> finalStages = stagesList.collect { List<PipelineStage> stages -> stages.reverse().find { it != null } }
        log.info "There are ${finalStages} parallel paths in final stage"
        
        List<Set> joinersList = finalStages.grep { it != null }.collect { it.context.pipelineJoiners }
        
        Set joiners = joinersList.sum()
                                 
        PipelineContext mergedContext = 
            new PipelineContext(null, parent.stages, joiners, new Branch(name:'all'))
            
        List mergedOutputs = finalStages.collect { s ->
            
            if(s?.context) {
                assert s.context.@output.every { it instanceof PipelineFile }
            }
            
            if(s?.context?.nextInputs) {
                if(!s.context.nextInputs.every { it instanceof PipelineFile }) {
                    assert false: "Stage $s.stageName output a nexInput that was not a PipelineFile!"
                }
            }
  
            
            Utils.box(s?.context?.@output) 
        }.sum().collect { it.normalize() }.unique()

       
        List mergedNextInputs = finalStages.collect { s ->
            Utils.box(s?.context?.nextInputs)
        }.sum().collect { it.normalize() }.unique()
        
        log.info "Last merged outputs are $mergedOutputs"
        mergedContext.setRawOutput(mergedOutputs)
        mergedContext.setNextInputs(mergedNextInputs)
        
        Closure flattenedBody = finalStages.find { it != null }?.body
        if(flattenedBody == null) {
            flattenedBody = {}
        }
        
        PipelineStage mergedStage = new FlattenedPipelineStage(
                mergedContext, 
                flattenedBody,
                finalStages,
                pipelines*.branch
            )
        log.info "Merged stage name is $mergedStage.stageName"
        parent.addStage(mergedStage)
        
        return mergedOutputs
    }
    
    static String summarizeErrors(List<Pipeline> pipelines) {
        
        // Need to associate each exception to a pipeline
        Map<Throwable,Pipeline> exPipelines = [:]
        pipelines.each { Pipeline p -> p.failExceptions.each { ex -> exPipelines[ex] = p } }
        
        pipelines*.failExceptions
                  .flatten()
                  .groupBy { it.message }
                  .collect { String msg, List<Throwable> t ->
                      
                        if(t instanceof SummaryErrorException) {
                            
                            assert t.summarisedErrors.every { (!it instanceof SummaryErrorException) }
                            return t.summarisedErrors*.message.join("\n")
                        }
                        
                        List<String> branches = t.collect { exPipelines[it] }*.branchPath.flatten()
                        
                        List<String> stages = t.grep { it instanceof PipelineError && it.ctx != null }.collect { it.ctx.stageName }.unique()
                        
                        """
                            Branch${branches.size()>1?'es':''} ${branches.join(", ")} in stage${stages.size()>1?'s':''} ${stages.join(", ")} reported message:
            
                       """.stripIndent() + msg 
                  }.join("\n")
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
     * Pattern to match the last suffix preceded by a dot in a filename
     */
    private static Pattern LAST_FILE_EXTENSION = ~'\\.[^\\.]*?$'
    
    /**
     * Trim the last file extension from the given String and return it
     * 
     * @param value
     * @return
     */
    static String getPrefix(String value) {
        return value.replaceAll(LAST_FILE_EXTENSION, '')    
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
