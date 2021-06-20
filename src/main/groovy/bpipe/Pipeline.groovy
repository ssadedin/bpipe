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

import groovy.json.JsonOutput;
import groovy.text.GStringTemplateEngine;
import groovy.text.GStringTemplateEngine.GStringTemplate;
import groovy.time.TimeCategory
import groovy.transform.CompileStatic

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger;

import groovy.util.logging.Log;
import groovy.xml.MarkupBuilder;
import groovy.transform.CompileStatic;

import java.util.logging.Level;
import java.util.logging.Logger
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream

import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.runtime.ReverseListIterator

import bpipe.graph.Graph;
import bpipe.storage.StorageLayer
import bpipe.storage.UnknownStoragePipelineFile

import static bpipe.Utils.isContainer 
import static bpipe.Utils.unbox 

import static bpipe.Edge.*

/**
 * Utility to convert a Node structure to a Map structure (primarily, for export as Json)
 */
class NodeListCategory {
    static Map toMap(Node n) {
        return  n.children()?[name: n.name(), type: n.attributes().type, children: n.children()*.toMap()]:[name:n.name(), type: n.attributes().type]
    }
    static String toJson(Node n) {
        groovy.json.JsonOutput.toJson(n.toMap())
    }
}

class ClosureScript extends Script {
    Closure closure
    def run() {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = this
        closure.call()
    }
}

/**
 * Main Pipeline class.  Used by client to start a pipeline
 * running by specifying initial inputs and setting up 
 * surrounding category to enable implicit pipeline stage functions.
 */
public class Pipeline implements ResourceRequestor {
    
    public final static Logger log = Logger.getLogger('bpipe.Pipeline')
    
    /**
     * Default imports added to the top of all files executed by Bpipe
     */
    static final String PIPELINE_IMPORTS = "import static Bpipe.*; import static bpipe.RegionSet.bed; import static bpipe.Pipeline.filetype; import Preserve as preserve; import Intermediate as intermediate; import Accompanies as accompanies; import Produce as produce; import Transform as transform; import Filter as filter;"
    
    /**
     * The thread id of the master thread that is running the baseline root
     * pipeline
     */
    static Long rootThreadId
    
    /**
     * The top level pipeline at the root of the pipeline hierarchy
     */
    static Pipeline rootPipeline
    
    /**
     * A map of script file names to internal Groovy class names. This is needed
     * because Bpipe loads and evaluates pipeline scripts itself, which results in
     * scripts being assigned random identifiers. When such identifiers appear
     * in error messages it is confusing for users since they can't tell which
     * actual file th error occurred in (or which line). This map is populated
     * as each script is loaded by code that is prepended to each script file
     * by Bpipe on the first line.
     */
    public static Map<String,String> scriptNames = Collections.synchronizedMap([:])
    
    /**
     * Index of pipeline stage nodes, keyed of the closure that implements the stage
     */
    static Map<Closure, Node>   stageNodeIndex = Collections.synchronizedMap([:])
    
    /**
     * Global binding - variables and functions (including pipeline stages)
     * that are available to all pipeline stages.  Don't put
     * variables that are specific to a pipeline in here (even though this
     * is an instance method) because they are injected in a way that makes
     * them appear across all pipeline instances.  For pipeline instance 
     * specific variables, see {@link #variables}
     */
    Binding externalBinding = new Binding()
    
    /**
     * Variables that are only available to this specific instance of a 
     * running pipeline.
     */
    Map<String,String> variables = [:]
    
   
    /**
     * File name mappings belonging to this pipeline instance
     */
    Aliases aliases = new Aliases()
    
    /**
     * Id of thread that is running this pipeline
     */
    Long threadId
    
    /**
     * List of past stages that have already produced outputs.  This 
     * list is built up progressively as pipeline stages execute.
     */
    List<PipelineStage> stages = []
    
    /**
     * List of pipeline stages executed by *this* pipeline
     */
    List<PipelineStage> myStages = []
    
    /**
     * A list of "dummy" stages that are actually used to link other stages together
     */
    Set<Closure> joiners = Collections.newSetFromMap(new ConcurrentHashMap())
    
    /**
     * Files accumulated from channels in children of this pipeline
     */
    Map<String,List<PipelineFile>> channelResults = [:]
    
    /**
     * A node representing the graph structure of the pipeline underneath this 
     * pipeline
     */
    Node node = new Node(null, "root", [type:"pipeline"])
    
    /**
     * If this pipeline was spawned as a child of another pipeline, then
     * the parent is set to that pipeline
     */
    Pipeline parent = null
    
    /**
     * The number of children that have been forked from this parent
     */
    int childCount = 0
    
    /**
     * The index of this child in the children of its parent
     */
    int childIndex = -1
    
    String id = "0"
    
    /**
     * Metadata about the branch within which this pipeline is running. 
     * The primary meta data is a name for the pipeline that is added to 
     * output file names when the pipeline is run as a child pipeline.
     * This is null and not used in the default, root pipeline
     */
    Branch branch = new Branch()
    
    /**
     * The channel to which the pipeline belongs
     */
    String channel
    
    /**
     * If there are unmerged child branches pending for this branch, then they are set here
     */
    List<Branch> inboundBranches
    
    /**
     * Date / time when this pipeline started running
     * Only populated for main / root level pipeline instance
     */
    Date startDate 
    
    /**
     * Date / time when this pipeline finished running
     * Only populated for main / root level pipeline instance
     */
    Date finishDate 
    
    boolean isIdle = false
    
    boolean isBidding() {
        return !isIdle;
    }
    
    void setBranch(Branch value) {
        this.branch = value
        if(this.parent) {
            this.branch.setParent(this.parent.branch)
        }
    }
    
    /**
     * Whether the name for the pipeline has been applied in the naming of
     * files that are produced by the pipeline.  A pipeline segment with a 
     * name should only inject its name into the output file name sequence
     * once, so this flag is set true when that happens.
     */
    boolean nameApplied = false
    
    void setNameApplied(boolean value) {
        this.nameApplied = value
    }
    
    /**
     * Flag that is set to true if this pipeline has executed and experienced 
     * a failure
     */
    boolean failed = false
    
    /**
     * Set to true if the user aborted this branch using 'succeed'
     */
    boolean aborted = false
    
    /**
     * Set to true for child pipelines when they are terminated
     */
    boolean finished = false
    
    /**
     * If a pipeline failed with an exception, it sets the exception(s) here
     */
    List<Throwable> failExceptions = Collections.synchronizedList([])
    
    /**
     * If a pipeline fails but not with an exception, the reason, if known
     * is set here
     */
    String failReason
    
    /**
     * The base context from which all others are generated
     */
    PipelineContext rootContext
    
    /**
     * Due to certain constraints in how Groovy handles operator 
     * overloading and injections of methods into classes at runtime,
     * we can only add certain methods in a static context.  To handle that,
     * we have a global (non-thread-safe) "current" pipeline.
     * <p>
     * To access the pipeline, use the #withCurrentUnderConstructionPipeline method
     * which will synchronize on the variable so that we can guarantee that no two
     * are accessed at the same time.
     */
    static Pipeline currentUnderConstructionPipeline = null
    
    /**
     * In a situation where multiple parallel exeuction paths are
     * running there are child pipelines. The child stages
     * inside the child pipeline are associated with the pipeline 
     * by this thread local variable
     */
    static ThreadLocal<Pipeline> currentRuntimePipeline = new ThreadLocal()
    
    
    /**
     * Documentation about this pipeline
     */
    static Map<String,Object> documentation = [:]
    
    /**
     * Create a pipeline, initialized with all the already discovered
     * segment-joining closures
     */
    Pipeline() {
        this.joiners = segmentJoiners.clone()
    }
    
    String getName() {
        return branch?.name?:""
    }
    
    /**
     * Allow user to add arbitrary documentation about their pipeline
     */
    static about(Map<String,Object> docs) {
        documentation += docs
    }
    
    static title(String theTitle) {
        about(title:theTitle)
    }
    
    static name(String theName) {
        about(name:theName)
    }
    
    static version(Object version) {
        about(version:version)   
    }
    
    /**
     * Inputs that are declared as required by the user
     */
    static Map requiredInputs = [:]
    
    /**
     * Declare the given inputs as required
     */
    static inputs(Map<String,Object> requiredInputs) {
        // Check that the given inputs are provided, or throw an error
        Pipeline.requiredInputs += requiredInputs
    }
    
    /**
     * The current context of the pipeline executing in this thread,
     * or null if there is no context.
     */
   static ThreadLocal<PipelineContext> currentContext = new ThreadLocal()
    
    static requires(Map<String,Object> requiredInputs) {
        
        if(currentContext.get()) { // called from within pipeline stage
            currentContext.get().requires(requiredInputs)
        }
        else {
            requiredInputs.each { k, v ->
                if(!Runner.binding.variables.containsKey(k)) {
                    throw new ValueMissingError(
                    """
                    Variable or parameter '$k' was not specified but is required to run this pipeline.
    
                    You can specify it in the following ways:
    
                               1. define the variable in your pipeline script: $k="<value>"
                               2. provide it from the command line by adding a flag:  -p $k=<value>
    
                    The parameter $k is described as follows:
    
                                   $v
                    """.stripIndent().trim()) 
                }
            }
        }
    }
    
    void checkRequiredInputs(def providedInputs) {
        requiredInputs.each { key, details ->
            log.info "Checking if input matching $key is provided in $providedInputs"
            if(!providedInputs.any { key.equals(file(it).name) || it.endsWith('.' + key)}) {
                throw new InputMissingError(key,details)
            }
            else
                log.info "Input type $key provided"
        }
    }
    
    /*
    static void var(String value, String description) {
        if(!this.localVariables.containsKey(value) && !this.extraBinding.variables.containsKey(value)) {
            log.info "Using default value of variable $k = $v"
            this.localVariables[value] = new UnknownRequiredValue(description:description)
        }
    }
    */
    
    /**
     * Due to certain constraints in how Groovy handles operator 
     * overloading and injections of methods into classes at runtime,
     * we can only add certain methods in a static context.  To handle that,
     * we have a global (non-thread-safe) "current" pipeline.  Fortunately this 
     * is only needed during the pipeline definition stage and not during 
     * execution, so in fact we can afford to be single threaded in how
     * we access this pipeline and only one pipeline is ever under construction
     * at a time (even if multiple pipelines are constructed).
     * 
     * @param c
     * @return
     */
    static Pipeline withCurrentUnderConstructionPipeline(Pipeline p, Closure c) {
        synchronized(Pipeline.class) {
            currentUnderConstructionPipeline = p
            c(currentUnderConstructionPipeline)    
            currentUnderConstructionPipeline = null
        }
    }
    
    /**
     * Creates a set of Chr objects based on regions supplied by the arguments
     * which can be string values ('X','Y'), integers (1,3,4) or ranges (1..3),
     * or optionally a Map which is treated as a configuration object and passed
     * through to the created Chr objects. Map values can be passed as individual
     * map entries (groovy named arguments), or the whole map can be passed as a literal
     * object, in which case it must be the first argument.
     * <p>
     * In practice this is used for parallezing pipelines based on chromosome.
     * See {@link PipelineCategory#multiply(java.util.Set, java.util.List)}
     */
    static Set<Chr> chr(Object... objs) {
        
        // Default configuration
        def cfg = [ filterInputs: "auto" ]
        
        // If a configuration is passed then override the default config with that
        // Note: the Map will always be first because Groovy enforces that
        if(objs && objs[0] instanceof Map) {
            cfg += objs[0]
            objs = objs[1..-1]
        }
        
        if(!objs) 
            throw new PipelineError("Attempt to parallelize by chromosome is missing required argument for which chromosomes to parallelize over")
        
        Set<Chr> result = [] as Set
        for(Object o in objs) {
            
            if(o instanceof Closure) 
                o = o()
            
            if(o instanceof Range) {
                for(r in o) {
                    result << new Chr(defaultGenomeChrPrefix+r, cfg)
                }
            }
            else 
            if(o instanceof String || o instanceof Integer) {
                result << new Chr(defaultGenomeChrPrefix+o, cfg)
            }
        }
        
        // If a region was specified on the command line or in config, 
        // check for overlap
        if(Config.region) {
            if(result.any { it.name == Config.region.value }) {
                log.info "Overriding pipeline regions with region specified on command line: ${Config.region}"
                result.clear()
                result.add(new Chr(Config.region.value, cfg))
            }
            else {
                println "WARNING: region specified on command line or configuration (${Config.region})  does not overlap regions specified in pipeline: $objs"
                println "WARNING: region will be ignored for this portion of the pipeline"
            }
        }
        
        return result
    }
    
    /**
     * Define a pipeline segment - allows a pipeline segment to
     * be created and stored in a variable
     */
    static def segment(Closure pipelineBuilder) {
        
        Pipeline pipeline = new Pipeline()
        PipelineCategory.addStages(pipelineBuilder.binding)
        
        log.info "Segment is loading external stages"
        if(!pipelineBuilder.binding.variables.containsKey("BPIPE_NO_EXTERNAL_STAGES"))
            pipeline.loadExternalStages()

        log.info "Segment finished loading external stages"
        
        Object result = pipeline.execute([], pipelineBuilder.binding, pipelineBuilder, false)
        
        segmentJoiners.addAll(pipeline.joiners)
        
        if(!(result in segmentBuilders))
            segmentBuilders[result] = pipelineBuilder
        
        return result
    }
    
    /**
     * A map from the generated closure for a segment to the closure that
     * built it. This allows us to know which builder was used for a closure when
     * it is encountered. It is used in the constructing the graph structure of the
     * pipeline which needs to understand which closures are segments and 
     * to invoke the builder to recreate the segment in graph form (see
     *  DefinePipelineCategory).
     */
    static Map<Closure,Closure> segmentBuilders = [:]
    
    static List<Closure> segmentJoiners = []
    
    /**
     * Default run method - introspects all the inputs from the binding of the
     * pipeline closure.
     */
    static def run(Closure pipeline) {
       run(pipeline.binding.variables.args, pipeline.binding, pipeline) 
    }
    
    static def run(Object host, Closure pipeline) {
        if((host instanceof String) || (host instanceof List)) {
            // When it is a string, interpret it as an input file
            run(host, pipeline.binding, pipeline) 
        }
        else
            run(pipeline.binding.variables.args, host, pipeline) 
    }
    
    /**
     * Launch a pipeline, possibly in one of the subsidiary modes (test, diagram, etc)
     * 
     * @param inputFile
     * @param host
     * @param pipelineBuilder
     */
    static def run(def inputFile, Object host, Closure pipelineBuilder) {
        
        if(Config.config.mode in ["run","retry","resume","remake"] && !Runner.testMode) { 
            ExecutorPool.startPools(ExecutorFactory.instance, Config.userConfig, false, true) 
        }  
        
       
        log.info("Running with INPUT " + inputFile)
        
        initResourceUnitMetaClass()
        
        Pipeline pipeline = new Pipeline()
        
        if(Config.region != null) {
            String regionValue = Config.region
            pipeline.branch.region = new RegionValue(regionValue)
        }
        else {
            pipeline.branch.region = new RegionValue("")
        }
        
        // To make life easier when a single argument is passed in,
        // debox it from the array so that pipeline stages that 
        // expect a single input do not unexpectedly get an array    
        inputFile = Utils.unbox(inputFile)
        
        if(host)
            PipelineCategory.addStages(host)
            
        if(!(host instanceof Binding))
            PipelineCategory.addStages(pipelineBuilder.binding)
            
        ToolDatabase.instance.setToolVariables(pipeline.externalBinding)
        
        Config.lockUserConfig()
        
        NotificationManager.instance.setChannelVariables(pipeline.externalBinding)
        
        pipeline.loadExternalStages()
        pipeline.joiners += segmentJoiners

        def mode = Config.config.mode 
        if(mode in ["run","documentation","register","resume"]) // todo: documentation should be its own mode! but can't support that right now
            pipeline.execute(inputFile, host, pipelineBuilder)
        else
        if(mode in ["diagram","diagrameditor"])
            pipeline.renderMxGraph(pipeline.diagram(host, pipelineBuilder), Runner.opts.arguments()[0], mode == "diagrameditor")
        else
        if(mode in ["documentation"])
            pipeline.documentation(host, pipelineBuilder, Runner.opts.arguments()[0])
            
        return pipeline;    
    }

    /**
     * Set some magic properties on integers that let you (ab)use them
     * to create resource specifications, such as 1024.GB, etc.
     */
    private static void initResourceUnitMetaClass() {
        Integer.metaClass.getProperty = { String name ->
            Integer n = delegate
            if(name == "GB") {
                return new ResourceUnit(amount: n * 1024 as Integer, key: "memory")
            }
            else
            if(name == "MB") {
                return new ResourceUnit(amount: n as Integer, key: "memory")
            }
            else
                return new ResourceUnit(amount:n as Integer, key: name)
        }
    }
    
    /**
     * Set this branch to name the next output file as a merge of output files from 
     * the most recent parallel section. This supports implementation of the 
     * merge point operator (>>>).
     * 
     * @param currentStage
     */
    void setMergePoint(PipelineStage currentStage) {
        int lastStageIndex = stages.findLastIndexOf { it instanceof FlattenedPipelineStage }
        FlattenedPipelineStage lastStage = lastStageIndex>=0 ? stages[lastStageIndex] : null
        if(lastStage) {
            log.info "Mergepoint initiated for branches: " + lastStage.branches*.name + " at stage " + currentStage.stageName
            this.inboundBranches = lastStage.branches
        }
        else {
            log.warning "Can't merge branches: there was no previous parallel stage!"
            System.err.println "WARNING: A merge point was specified using >>> but no previous parallel stage to merge from was identified. The merge operator will be ignored."
        }
    }
    
    /**
     * Runs the specified closure in the context of this pipeline 
     * and both decrements and notifies the given counter when finished.
     */
    void runSegment(def inputs, Closure s) {
        try {
            
            currentRuntimePipeline.set(this) 
        
            this.rootContext = createContext()
            
            this.threadId = Thread.currentThread().id
            
            PipelineStage currentStage = new PipelineStage(rootContext, s)
            currentStage.synthetic = true
            log.info "Running segment with inputs $inputs"
            this.addStage(currentStage)
            
            List<PipelineFile> inputCopy = Utils.resolveToDefaultStorage(inputs)
            
            currentStage.context.@input = inputCopy
            currentStage.context.branchInputs = inputCopy
            
            try {
                currentStage.run()
                log.info "Pipeline segment ${this.branch} in thread ${Thread.currentThread().id} has finished normally"
            }
            catch(UserTerminateBranchException e) {
                log.info "Pipeline segment ${this.branch} has terminated by 'succeed' in user script: $e.message"
                
                println "${new Date()} MSG: Branch ${branch=='all'?'':branch} completed: $e.message"
                aborted = true
            }
            catch(PipelinePausedException e) {
                log.info "Pipeline segment ${this.branch} has terminated by user initiated pause"
                
                println "${new Date()} MSG: Branch ${branch=='all'?'':branch} aborted due to user initiated pause"
                aborted = true
            }
            catch(PipelineError e) {
                log.info "Pipeline segment in thread ${Thread.currentThread().id} failed (2): " + e.message
//                System.err << "Pipeline failed! (2) \n\n"+e.message << "\n\n"
                
                if(!e.summary) {
                    if(e.ctx && e.ctx.stageName != "Unknown")
                        System.println "ERROR: stage $e.ctx.stageName failed: $e.message \n"
                    else
                        System.println "ERROR: $e.message \n" 
                }
                        
                failed = true
                if(e instanceof PatternInputMissingError)
                    throw e
                failExceptions << e
            }
            catch(PipelineTestAbort e) {
                log.info "Pipeline segment aborted due to test mode"
                if(!e.summary)
                    println "\n\nAbort due to Test Mode!\n\n" + Utils.indent(e.message) 
                failReason = "Pipeline was run in test mode and was stopped before performing an action. See earlier messages for details."
                failed = true
            }
            catch(Error e) {
                // This is important to prevent the parent from allowing the pipeline to continue
                // in the face of things like OutOfMemoryError etc.
                failed = true 
                log.severe "Internal error in thread ${Thread.currentThread().id}: " + e.toString()
                System.err.println "Internal error: " + e.toString()
                throw e
            }
        }
        finally {
            log.info "Finished running segment in thread ${Thread.currentThread().id} for inputs $inputs"
            Concurrency.instance.unregisterResourceRequestor(this)
        }
    }
    
    /**
     * The current pipeline build operation - if building the real pipeline,
     * it's PipelineCategory, but when the pipeline structure graph is being 
     * created, it's DefinePipelineCategory
     */
    static Class builderCategory = PipelineCategory
    

    /**
     * Construct the pipeline using the given pipelineBuilder closure, and then 
     * if the launch flag is true, launch it
     * 
     * @param inputFile
     * @param host
     * @param pipelineBuilder
     * @param launch
     * @return
     */
    private Closure execute(def inputFile, Object host, Closure pipelineBuilder, boolean launch=true) {
        
        List<String> inputFiles = Utils.box(inputFile)
        
        Pipeline.rootThreadId = Thread.currentThread().id
        this.threadId = Pipeline.rootThreadId
        Pipeline.rootPipeline = this
        
        // We have to manually add all the external variables to the outer pipeline stage
        initializeBindingWithExternalVariables(pipelineBuilder)
        
        initializeBindingWithGenomes(pipelineBuilder)
        
         // Add all the pipeline variables to the external binding
        this.externalBinding.variables += pipelineBuilder.binding.variables
        
        def cmdlog = CommandLog.cmdLog
        startDate = new Date()
        if(launch) {
            initializeRunLogs(inputFiles)
        }
        
        Map pipelineStructure = launch ? diagram(host, pipelineBuilder) : null
        
        def constructedPipeline = constructPipeline(pipelineBuilder)
        
        if(launch) {
            EventManager.instance.signal(PipelineEvent.STARTED, "Pipeline started", [pipeline:pipelineStructure])
            launchPipeline(constructedPipeline, inputFiles, startDate)
        }

        // Make sure the command log ends with newline
        // as output is not terminated with one by default
        cmdlog << ""
       
        return constructedPipeline
    }

    
    private Closure constructPipeline(Closure pipelineBody) {
        def constructedPipeline
        use(builderCategory) {
            // Build the actual pipeline
            Pipeline.withCurrentUnderConstructionPipeline(this) {
                constructedPipeline = pipelineBody()
                // See bug #60
                if(constructedPipeline instanceof List) {
                    currentRuntimePipeline.set(this)
                    constructedPipeline = (Closure)PipelineCategory.splitOnFiles("*", constructedPipeline, false)
                }
            }
        }
        return constructedPipeline
    }

    private void initializeBindingWithExternalVariables(Closure pipelineBody) {
        this.externalBinding.variables.each {
            log.info "Loaded external reference: $it.key"
            if(pipelineBody.binding.variables.containsKey(it.key))
                log.info "External reference $it.key is overridden by local reference"
            pipelineBody.binding.variables.put(it.key,it.value)
        }
    }

    private void initializeBindingWithGenomes(Closure pipelineBody) {
        Pipeline.genomes.each {
            log.info "Loaded genome reference: $it.key"
            if(!pipelineBody.binding.variables.containsKey(it.key))
                pipelineBody.binding.variables.put(it.key,it.value)
            else
                log.info "Genome $it.key is overridden by local reference"
        }
    }
    
    /**
     * Run the pipeline, handling any errors and printing out results to the
     * console.
     * 
     * @param constructedPipeline
     * @param inputFile
     */
    @CompileStatic
    void launchPipeline(Closure constructedPipeline, List<String> rawInputFiles, Date startDate) {
        
        CommandLog cmdlog = CommandLog.cmdLog
        
        String failureMessage = null
        try {
            
            this.checkRequiredInputs(rawInputFiles)
            
            List<PipelineFile> resolvedInputFiles = StorageLayer.resolve(rawInputFiles)
            checkForMissingInputs(resolvedInputFiles)
            
            if(!Runner.opts['t']) {
                writeJobPIDFile()
                scheduleStatsUpdate()
            }
            
            if(!Config.userConfig.getOrDefault('allowGlobalWrites',false)) {
                Runner.binding.readOnly = true
            }
            
            runSegment(resolvedInputFiles, constructedPipeline)
                    
            if(failed) {
                failureMessage = summarizeExceptions(failExceptions)
            }
        }
        catch(PatternInputMissingError e) {
            clearMissingFilePromptText()
        }
        catch(InputMissingError e) {
            failureMessage = """
                A required input was missing from the files given as input.
                        
                         Input Type:  $e.inputType
                        Description:  $e.description""".stripIndent()
            failed = true
        }
        catch(PipelineError e) {
            failed = true
            failExceptions << e
            failureMessage = e.getMessage()
        }
        catch(Exception e) {
            log.throwing "Pipeline", "Unexpected failure", e
            failed = true
            throw e
        }
                
        finishDate = new Date()
        
        printCompletionMessages(failureMessage, cmdlog, startDate)
               
        // See if any checks failed
        List<Check> allChecks = Check.loadAll()
        List<Check> failedChecks = allChecks.grep { Check c -> !c.passed && !c.override }
        if(failedChecks) {
            println "\nWARNING: ${failedChecks.size()} check(s) failed. Use 'bpipe checks' to see details.\n"
        }
        
        sendFinishedEvent(startDate, allChecks)
        
        if(!Runner.opts['t'])
            saveResultState(failed, allChecks, failedChecks) 
        
        if(!failed) {
            summarizeOutputs(stages)
        }
    }
    
    @CompileStatic
    String summarizeExceptions(List<Throwable> failExceptions) {
        return '\n' + failExceptions.collect { e ->
            if(e instanceof PipelineError && ((PipelineError)e).ctx) {
                PipelineError pe = (PipelineError)e
                String branchPart = pe.ctx?.branch?.name ? " ($pe.ctx.branch.name) " : ""
                return "In stage ${pe.ctx?.stageName}$branchPart: " + pe.message
            }
            else 
                return e.message
        }.join('\n')
    }
    
    private void clearMissingFilePromptText() {
        new File(".bpipe/prompt_input_files." + Config.config.pid).text = ''
    }

    private void printCompletionMessages(String failureMessage, CommandLog cmdlog, Date startDate) {
        if(Runner.testMode && failed && failExceptions.empty) {
            println("\n"+" Pipeline Test Succeeded ".center(Config.config.columns,"="))
        }
        else {
            println("\n"+" Pipeline ${failed?'Failed':'Succeeded'} ".center(Config.config.columns,"="))
        }

        if(failed) {
            println failureMessage
            println()
            println "Use 'bpipe errors' to see output from failed commands."
            println()
        }

        if(rootContext)
            rootContext.msg "Finished at " + finishDate

        about(finishedAt: finishDate)
        cmdlog << "# " + (" Finished at " + finishDate + " Duration = " + TimeCategory.minus(finishDate,startDate) +" ").center(Config.config.columns,"#")
    }

    /**
     * Send the event signalling that the pipeline has completed
     */
    private void sendFinishedEvent(Date startDate, List allChecks) {
        log.info "Sending FINISHED event for $startDate - $finishDate"

        EventManager.instance.signal(PipelineEvent.FINISHED, "Pipeline " + (failed?"Failed":"Succeeded"),
                [
                    pipeline:this,
                    checks:allChecks,
                    result:!failed,
                    startDate:startDate,
                    finishDate:finishDate,
                    commands: CommandManager.executedCommands
                ])
    }
    
    /**
     * If any of the input files could not be resolved to a storage system, throw an error.
     * 
     * @param files
     */
    @CompileStatic
    void checkForMissingInputs(List<PipelineFile> files) {
        List<PipelineFile> missing = files.grep { it instanceof UnknownStoragePipelineFile }
        if(!missing.isEmpty()) {
            throw new PipelineError("""
            One or more provided inputs could not be resolved to an existing file in any configured filesystem:

                ${missing.join('\n                ')}
            
            Please check if these files exist, and that you have configured your storage correctly, if these
            are non-local files.
            """.stripIndent())
        }
    }
    
    private writeJobPIDFile() {
        new File(".bpipe/run.pid").text = Config.config.pid
        File jobFile = new File(Runner.LOCAL_JOB_DIR, Config.config.pid)
        jobFile.append("-----------------------\npguid: " + Config.config.pguid+"\n")
    }
    
    private final static int STATS_POLLER_INTERVAL = 120000
    
    private scheduleStatsUpdate() {
        long intervalMs = Config.userConfig.getOrDefault('stats_update_interval', STATS_POLLER_INTERVAL)
        
        Poller.instance.executor.scheduleAtFixedRate({
            try {
                saveResultState(failed, [], [])
            }
            catch(Throwable t) {
                log.severe "Failed to save result state!"
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
    }
    
    final static String DATE_FORMAT="yyyy-MM-dd HH:mm:ss"
    
    void saveResultState(boolean failed, List<Check> allChecks, List<Check> failedChecks) {
       
        long nowMs = System.currentTimeMillis()
        
        new File(".bpipe/results").mkdirs()
        
        // Compute the total runtime of all tools
        List<Command> commandList
        synchronized(CommandManager.executedCommands) {
          commandList = CommandManager.executedCommands.collect { it } // clone not supported natively
        }
        
        long commandTimeMs = commandList.sum {  Command cmd -> (cmd.stopTimeMs - cmd.startTimeMs) }
        if(commandTimeMs == null)
            commandTimeMs = 0
                  
        new File(".bpipe/results/${Config.config.pid}.xml").withWriter { w ->
            MarkupBuilder xml = new MarkupBuilder(w)
            xml.job(id:Config.config.pid) {
                succeeded(String.valueOf(!failed))
                startDateTime(startDate.format(DATE_FORMAT))
                if(finishDate)
                    endDateTime(finishDate.format(DATE_FORMAT))
                totalCommandTimeSeconds(commandTimeMs/1000)
                
                commands {
                   commandList.each {  Command cmd ->
                        command {
                            id(cmd.id)
                            stage(cmd.name)
                            branch(cmd.branch?.name?:"")
                            content(cmd.command)
                            start(new Date(cmd.startTimeMs).format(DATE_FORMAT))
                            end(new Date(cmd.stopTimeMs).format(DATE_FORMAT))
                            exitCode(cmd.exitCode)
                        }
                   }
                }
            }
        }
        
        log.info "Saved pipeline state in ${System.currentTimeMillis() - nowMs}ms"
    }
    
    void initializeRunLogs(List<String> inputFiles) {
        def cmdlog = CommandLog.cmdLog
        cmdlog.write("")

        cmdlog << "#"*Config.config.columns 
        cmdlog << "# Starting pipeline at " + (new Date())
        cmdlog << "# Input files:  $inputFiles"
        cmdlog << "# Output Log:  " + Config.config.outputLogPath 
            
        printStartMessage(startDate)
            
        about(startedAt: startDate)
    }

    private static printStartMessage(Date startDate) {
        Calendar cal = Calendar.instance
        cal.time = startDate
        String startDateTime = cal.format("yyyy-MM-dd HH:mm") + " "

        OutputLog startLog = new OutputLog("----")
        startLog.bufferLine("╒" + "═"*(Config.config.columns-2) + '╕')
        
        if(documentation?.title) {
            startLog.bufferLine("|" + " " * (Config.config.columns-2) + "|")
            startLog.bufferLine("| " + documentation.title.center(Config.config.columns-3) + "|")
            startLog.bufferLine("|" + " " * (Config.config.columns-2) + "|")
            startLog.bufferLine("|" + " Starting at $startDateTime".center(Config.config.columns-2) + "|")
        }
        else {
            startLog.bufferLine("|" + " Starting Pipeline at $startDateTime".center(Config.config.columns-2) + "|")
        }
        startLog.bufferLine("╘" + "═"*(Config.config.columns-2) + '╛')
        startLog.flush()
    }
    
    PipelineContext createContext() {
       def ctx = new PipelineContext(this.externalBinding, this.stages, this.joiners, this.branch) 
        if(branch.properties.containsKey("dir")) {
            ctx.outputDirectory = branch.dir
        }
        else
            ctx.outputDirectory = Config.config.defaultOutputDirectory
       return ctx
    }
    
    /**
     * Create an artificial stage that supplies inputs to this pipeline as if they were outputs
     * of a previous set of stages joined to this pipeline's stages.
     */
    PipelineStage createDummyStage(List files) {
        PipelineContext dummyPriorContext = this.createContext()
        PipelineStage dummyPriorStage = new PipelineStage(dummyPriorContext,{})

        // Need to set this without redirection to the output folder because otherwise
        dummyPriorContext.setRawOutput(files)
        dummyPriorContext.@input = files.collect {
            it instanceof PipelineFile ? it : new UnknownStoragePipelineFile(it)
        }
        
        long threadId = Thread.currentThread().id

        log.info "Created dummy prior stage for thread ${threadId} with outputs : ${dummyPriorContext.@output}"
        return dummyPriorStage
    }
    
    List<Pipeline> children = []
    
    /**
     * Create a new pipeline that is initialized with copies of all the same state
     * that this pipeline has.  The new pipeline can execute concurrently with this
     * one without interfering with this one's state.
     * <p>
     * Note:  the new pipeline is not run by this method; instead you have to
     *        call one of the {@link #run(Closure)} methods on the returned pipeline
     */
    @CompileStatic
    Pipeline fork(Node branchPoint, String childName, String id = null) {
        
//        assert branchPoint in this.node.children()
        
        Pipeline p = new Pipeline()
        p.node = new Node(branchPoint, childName, [type:'pipeline',pipeline:p])
        p.stages = [] + this.stages
        p.joiners =  this.joiners
        p.aliases = this.aliases
        p.parent = this
        p.childIndex = this.childCount
        
        if(id == null) 
            p.id = this.stages[-1].id + "_" + this.childCount
        else
            p.id = id
            
        this.children << p
        ++this.childCount
        return p
    }
    
    @CompileStatic
    void initBranch(final String childName, final boolean nameApplied) {
        assert !childName.is(null)
        this.branch = new Branch(name:childName)
        this.branch.@name = childName
        this.nameApplied = nameApplied
    }
    
    private void loadExternalStages() {
        GroovyShell shell = new GroovyShell(externalBinding)
        def pipeFolders = [new File(System.properties["user.home"], "bpipes")]
        if(System.getenv("BPIPE_LIB")) {
            pipeFolders = System.getenv("BPIPE_LIB").split(Utils.isWindows()?";":":").collect { new File(it) }
        }
        
        while(true) {
          pipeFolders.addAll(loadedPaths)
          loadedPaths = []
          loadExternalStagesFromPaths(shell, pipeFolders, true, true)
          pipeFolders.clear() 
          
          if(loadedPaths.isEmpty())
              break
        }
    }
    
    static Set allLoadedPaths = new HashSet()
    
    static List<File> currentLoadingPaths = Collections.synchronizedList([])
    
    private static void loadExternalStagesFromPaths(GroovyShell shell, List<File> paths, cache=true, includesLibs=false) {
        
        for(File pipeFolder in paths) {
            
            List<File> libPaths = []
            if(!pipeFolder.exists()) {
                log.warning("Pipeline folder $pipeFolder could not be found")
            }
            else
            if(pipeFolder.isFile()) {
                libPaths += [pipeFolder]
            }
            else
            if(pipeFolder.isDirectory()) {
                libPaths = pipeFolder.listFiles().grep { it.name.endsWith(".groovy") }
            }
            else {
                log.warning("Pipeline folder $pipeFolder was not a normal directory or file")
                System.err.println("Pipeline folder $pipeFolder was not a normal directory or file")
            }
            
            // Avoid recursive loading; caused by segments defined inside loaded files,
            // which attempt to reload everything inside the loadedPaths list
            libPaths = libPaths.grep { f -> !currentLoadingPaths.any { it.absolutePath == f.absolutePath } }
            
            libPaths.sort()
            
            currentLoadingPaths.add(pipeFolder)
            
            try {
                loadExternalStagesFromPathList(shell, libPaths, cache, includesLibs)
            }
            finally {
                currentLoadingPaths.remove(pipeFolder)
            }
            
            log.info "Adding stages from evaluation of $paths"
            PipelineCategory.addStages(shell.context)
            log.info "Added stages from evaluation of $paths"

        }
    }
    
    private static void loadExternalStagesFromPathList(GroovyShell shell, List<File> libPaths, boolean cache, boolean includesLibs) {
        
            log.info "Evaluating paths: $libPaths"
                
            // Load all the scripts from this path / folder
            libPaths.each { File scriptFile ->
                if(cache && allLoadedPaths.contains(scriptFile.canonicalPath)) {
                    log.info "Skip loading $scriptFile.canonicalPath (already loaded)"
                    return
                }
                
                log.info("Evaluating library file $scriptFile")
                try {
                    String scriptClassName = scriptFile.name.replaceAll('.groovy$','_bpipe.groovy')
                    String scriptText = scriptFile.text
                    if(scriptText.startsWith('#!')) {
                       scriptText = scriptText.substring(scriptText.indexOf('\n')+1) 
                    }
                    
                    Script script = shell.evaluate(PIPELINE_IMPORTS+
                        (includesLibs?" binding.variables['BPIPE_NO_EXTERNAL_STAGES']=true;":"") +
                        "bpipe.Pipeline.scriptNames['$scriptFile']=this.class.name;" +
                         scriptText + "\nthis", scriptClassName)
                    
                    log.info "Successfully evaluated " + scriptFile
                    script.getMetaClass().getMethods().grep { CachedMethod m ->
                        (m.declaringClass.name.endsWith("_bpipe") && !["__\$swapInit","run","main"].contains(m.name)) 
                    }.each { CachedMethod m ->
                        shell.context.variables[m.name] = { Object[] args -> script.getMetaClass().invokeMethod(script,m.name,args) }
                    }
                    log.fine "Binding now has variables: " + shell.context.variables
                    
                    if(cache)
                        allLoadedPaths.add(scriptFile.canonicalPath)
                }
                catch(Exception ex) {
                    log.log(Level.SEVERE,"Failed to evaluate script $scriptFile: "+ ex, ex)
                    throw new PipelineError("Error evaluating script '$scriptFile': " + ex.getMessage())
                }
            }
    }
    
    /**
     * This method is invoked from code generated by the @Transform annotation
     * 
     * @param stageName     name of stage that is declared to be a transformation
     * @param ext           new extension for file name produced by stage
     */
    static void declareTransform(String stageName, String ext) {
        println "Transform Declared:  $stageName maps to $ext"
        PipelineCategory.wrappers[stageName] = { body, input ->
            PipelineCategory.transform(ext) {
                body(input)
            }
        }
    }
    
    static Closure declarePipelineStage(String stageName, Closure c) {
        // println "Pipeline stage declared $stageName"
        PipelineCategory.closureNames[c] = stageName
        if(c.properties.containsKey("binding")) {
            c.binding.variables[stageName] = c
        }
        return c
    }
    
    /**
     * Paths that have been added to the script using the <code>load</code>
     * Bpipe command.
     */
    static List<File> loadedPaths = []
    
    /**
     * Genomes that have been loaded
     */
    static Map<String,RegionSet> genomes = [:]
    
    static Map<String, Map<String,Integer>> genomeChromosomeSizes = [:]
    
    /**
     * Include pipeline stages from the specified path into the pipeline
     * @param path
     */
    static synchronized void load(String path) {
        File f = new File(path)
        if(!Utils.fileExists(f)) {
            // Attempt to resolve the file relative to the main script location if
            // it cannot be resolved directly
            f = new File(Config.scriptDirectory, path)
        }
        
        if(!f.exists() && (path ==~ 'http(s){0,1}://.*$')) {
            new File(".bpipe/scripts").mkdirs()
            f = new File(".bpipe/scripts/" + Utils.urlToFileName(path, "groovy"))
            if(!f.exists())
                f.text = new URI(path).toURL().text
        }
        
        if(!f.exists()) 
            throw new PipelineError("A script, requested to be loaded from file '$path', could not be accessed.")
            
        if(currentRuntimePipeline.get()) {
            Pipeline pipeline = currentRuntimePipeline.get()
            Binding binding = new Binding()
            binding.variables = Runner.binding.variables.clone()
            def shell = new GroovyShell(binding)
            
            // Note: do not cache - different branches may need to load the same file
            // which caching would prevent
            loadExternalStagesFromPaths(shell, [f], false) 
            shell.context.variables.each { name, value ->
                log.info "Loaded variable $name into branch $pipeline.branch.name"
                pipeline.branch[name] = value
            }
        }
        else  {
            def shell = new GroovyShell(Runner.binding)
            loadExternalStagesFromPaths(shell, [f])
        }
            
        loadedPaths << f
    }
    
    /**
     * Map of custom file types (extension => list of mappings)
     */
    static Map<String,List<String>> fileTypeMappings = Collections.synchronizedMap([:])

    static synchronized void filetype(Map<String,List<String>> mappings) {
        mappings.each { 
            log.info "Registered custom file type $it.key => $it.value"
            fileTypeMappings[it.key]=it.value 
        }
    }
    
    static synchronized void config(Closure cfgClosure) {
        ConfigObject newConfig = new ConfigSlurper().parse(new ClosureScript(closure:cfgClosure))
        Config.userConfig.merge(newConfig)
        
        // In case there were any limits set, update the concurrency 
        // But we do not want to override settings from command line or bpipe.config here
        Concurrency.instance.initFromConfig(false)
    }
    
    /**
     * These genomes are automatically converted from UCSC format if specified.
     * That is, we still download gene / genome definitions from UCSC in UCSC format,
     * but strip off the 'chr' from sequence names. No liftover is performed.
     */
    static Map CONVERTED_GENOMES = [
                                    'GRCh37' : 'hg19',
                                    'GRCh38': 'hg38'
                                   ] 
    
    static String defaultGenome = null
    
    static String defaultGenomeChrPrefix = 'chr'
    
    static synchronized void genome(String name) {
        genome(contigs:false, name)
    }
    
    /**
     * Load the specified genome model into memory, possibly downloading it from UCSC
     * if necessary
     */
    static synchronized void genome(Map options, String name) {
        File genomesDir = new File(System.getProperty("user.home"), ".bpipedb/genomes")
        if(!genomesDir.exists())
            if(!genomesDir.mkdirs())
                throw new IOException("Unable to create directory to store genomes. Please check permissions for $genomesDir")
                
        // Construct a UCSC URL based on the given name and then download the genes from there
        File cachedGenome = new File(genomesDir, "${name}.ser.gz")
        File chromFile = new File(genomesDir,"chromInfo.${name}.txt")
        
        // Since we use UCSC as a data source we need to intelligently convert to a
        // corresponding UCSC genome identifier as well as record wither 'chr' is
        // prepended to chromosome symbols
        String ucscName = name
        boolean convertChromosomes = false
        if(name in CONVERTED_GENOMES) {
            ucscName = CONVERTED_GENOMES[name]
            convertChromosomes = true
            Pipeline.defaultGenomeChrPrefix = ''
        }
        else {
            Pipeline.defaultGenomeChrPrefix = 'chr'
        }
        
        try {
            Pipeline.genomes[name] = loadCachedGenome(cachedGenome, options.contig?true:false)
        }
        catch(Exception e) {
            
            log.info "Not able to load genome from cache file $cachedGenome($e): will attempt to download"
             
            if(cachedGenome.exists())
                cachedGenome.delete()
                
            String url = "http://hgdownload.soe.ucsc.edu/goldenPath/$ucscName/database/refGene.txt.gz"
            log.info "Downloading ensembl gene database from $url"
            println "MSG: Downloading genome from $url"
            new URL(url).openStream().withStream { stream ->
                RegionSet genome = RegionSet.index(stream, convertChromosomes) 
                genome.name = name
                new FileOutputStream(cachedGenome).withStream { outStream ->
                    new ObjectOutputStream(outStream) << genome
                }
            }
                
            // Download chromosome sizes for specified genome
            url = "http://hgdownload.soe.ucsc.edu/goldenPath/$ucscName/database/chromInfo.txt.gz"
            log.info "Downloading chromosome sizes from $url"
            println "MSG: Downloading chromosome sizes from $url"
            new GZIPInputStream(new URL(url).openStream()).withStream { stream ->
                chromFile.withOutputStream { outStream ->
                    Files.copy(stream,outStream)
                }
            }
            Pipeline.genomes[name] = loadCachedGenome(cachedGenome, options.contig?true:false)
        }
        
       
        
        Pipeline.genomeChromosomeSizes[name] = 
            chromFile.readLines()*.tokenize('\t').collectEntries { [ convertChromosomes ? it[0].replaceAll('^chr','') :  it[0], it[1].toInteger() ]}
        
        Pipeline.defaultGenome = name
    }
    
    /**
     * Load a genome from the given file, which should be a serialized RegionSet object.
     * 
     * @param cachedGenome  The file to load from
     * @param loadContigs   If true, unassembled contigs will be included, otherwise only
     *                      major chromosomes will be loaded.
     * @return  A regionset containing the entire genome which can be referenced in the 
     *          pipeline
     */
    static RegionSet loadCachedGenome(File cachedGenome, boolean loadContigs) {
        log.info "Loading cached genome : $cachedGenome"
        long startTimeMs = System.currentTimeMillis()
        RegionSet genome = RegionSet.load(cachedGenome) 
        if(!loadContigs) {
            genome.removeMinorContigs()
        }
        println "Finished loading genome $cachedGenome in ${System.currentTimeMillis() - startTimeMs} ms"
        return genome
    }
    
    
    /**
     * This method creates documentation for a pipeline based on the 
     * pipeline stage names and any configured documentation that is added to them
     */
    def documentation() {
        
        if(!documentation.title)
            documentation.title = "Pipeline Report"

        def outFile = Config.config.defaultDocHtml
        
        // We build up a list of pipeline stages
        // so the seed for that is a list with one empty list
        
        new ReportGenerator().generateFromTemplate(this,"index.html", outFile)
    }
    
    def generateCustomReport(String reportName) {
        try {
            def outFile = reportName + ".html"
          documentation.title = "Report"
          new ReportGenerator().generateFromTemplate(this,reportName + ".html", outFile)
        }
        catch(PipelineError e) {
            System.err.println "\nA problem occurred generating your report:"
            System.err.println e.message + "\n"
        }
    }
    
    /**
     * Fills the given list with meta data about pipeline stages derived 
     * from the current pipeline's execution.  
     * 
     * @param pipelines
     */
    void fillDocStages(List pipelines) {
        
        log.fine "Filling stages $stages"
        
        for(List docStages in pipelines.clone()) {
            
            for(PipelineStage s in stages) {
                    
                // No documentation for anonymous joiner stages
                if(s.body in joiners)
                    continue
                    
                if(!s.children && s?.context?.stageName != null) {
                    log.info "adding stage $s.context.stageName from pipeline $this"
                    pipelines.each { it << s }
                }
                    
                if(s in docStages || (parent != null && s in parent.stages))
                    continue
                    
                // if it has children, generate them
                if(s.children) {
                
                    // Take out the original
                    pipelines.remove(docStages)
                    
                    // Replace it for an entry with each child pipeline
                    pipelines.addAll s.children.collect { Pipeline childPipeline ->
                        List childStages = [ [] ]
                        childPipeline.fillDocStages(childStages)
                        return childStages
                    }.collect { it[0] }
                }
            }
        }
        
        log.fine "Result: $pipelines"
    }
    
    /**
     * Generate a model of the pipeline in the form of Groovy Node objects
     * 
     * @param host
     * @param pipeline  Closure that is to execute the pipeline
     * @return  A tree of Nodes reflecting the pipeline structure
     */
    Map diagram(Object host, Closure pipeline) {
        
        try {
            Pipeline.builderCategory = DefinePipelineCategory
            
            // We have to manually add all the external variables to the outer pipeline stage
            this.externalBinding.variables.each {
                log.info "Loaded external reference: $it.key"
                if(!pipeline.binding.variables.containsKey(it.key))
                    pipeline.binding.variables.put(it.key,it.value)
                else
                    log.info "External reference $it.key is overridden by local reference"
            }
            
            // Figures out what the pipeline stages are 
            if(host)
                pipeline.setDelegate(host)
                
            DefinePipelineCategory.reset()
            use(DefinePipelineCategory) {
                def realizedPipeline = pipeline()
                Utils.box(realizedPipeline).each { realizedBranch ->
                    if(!(realizedBranch in PipelineCategory.closureNames) && (realizedBranch !=null)) {
                        if(realizedBranch instanceof Closure) {
                            realizedBranch()
                        }
                    }
                    else {
                        DefinePipelineCategory.inputStage.appendNode(
                            PipelineCategory.closureNames[realizedBranch])
                        
                        Node rootNode = DefinePipelineCategory.createNode(realizedBranch)
                        DefinePipelineCategory.edges.add(
                            new Edge(DefinePipelineCategory.inputStage, rootNode, RESOURCE))
                    }
                }
            }
            return [
                root : DefinePipelineCategory.inputStage, 
                nodes: DefinePipelineCategory.nodes, 
                edges: DefinePipelineCategory.edges*.toMap()
            ]
        }
        finally {
            Pipeline.builderCategory = PipelineCategory
        }
    }
    
    final int dumpTabWidth = 8
    
    void dump(Writer w, int indentLevel=0) {
        
        String indent = " " * (indentLevel+dumpTabWidth)
        w.println((" "*indentLevel) + this.node.name() + ":")
        this.node.children().each { Node n ->
            
            if(n.attributes().type == 'stage') {
                PipelineStage stage = n.attributes().stage
                if(!stage.synthetic)
                    w.println indent + stage.stageName
            }
            else
            if(n.attributes().type == 'pipeline') {
                n.attributes().pipeline.dump(w,indentLevel+dumpTabWidth)
            }
            else
            if(n.attributes().type == 'branchpoint') {
                w.println indent + "o------>"
                for(Node childPipelineNode in n.children()) {
                    def atts = childPipelineNode.attributes()
                    Pipeline p = atts.pipeline
                    p.dump(w,indentLevel+dumpTabWidth*2)
                }
            }
        }
    }
    
    /**
     * This method creates a diagram of the pipeline instead of running it
     */
    void renderMxGraph(Map structure, String fileName, boolean editor) {
               
        // Now make a graph
        // println "Found stages " + DefinePipelineCategory.stages
        Graph g = new Graph(structure.root)
        if(editor) {
            g.display()
        }
        else {
            
            def opts = Runner.opts
            
            String outputExtension = opts.f ? "."+opts.f : ".png"
            String outputFileName = fileName+outputExtension
            println "Creating diagram $outputFileName"
            if(opts.f == "json")  {
//                use(NodeListCategory) {
//                    new File(outputFileName).text = root.toJson()
//                }
                
                use(NodeListCategory) {
                    new File(outputFileName).text =    
                        JsonOutput.toJson(getNodeGraph(structure))
                }
            }
            else
            if(opts.f == "svg") 
                g.renderSVG(outputFileName)
            else
            if(opts.f == "png" || !opts.f) 
                g.renderPNG(outputFileName)
            else
                throw new PipelineError("Output format of ${opts.f} is not recognized / supported")
        } 
    }
    
    /**
     * Convert information from the Node representation of the pipeline into
     * a list of nodes and edges suitable for export as a graph structure.
     * 
     * @param Map   map containing a list of nodes under key 'nodes' and a list 
     *              of edges under entry 'edges'
     * @return
     */
    static Map getNodeGraph(Map structure) {
       [
            nodes: structure.nodes.collect { 
              [ 
                  name: it.value.name(),
                  id: it.value.attributes().id,
                  type: it.value.attributes().type
              ]
           },
           edges: structure.edges
      ] 
    }
    
    /**
     * Stores the given stage as part of the execution of this pipeline
     */
    void addStage(PipelineStage stage) {
        synchronized(this.stages) {
           
            stage.id = Pipeline.stageNodeIndex[stage.body]?.attribute('id')
            
            this.stages << stage
            this.myStages << stage
            
            node.appendNode(stage.stageName, [type:'stage','stage' : stage])
        }
    }
    
    /**
     * Stores a branching of the pipeline as a node on the pipeline structure
     * definition. 
     */
    Node addBranchPoint(String name) {
        this.node.appendNode(name, [type:'branchpoint'])
    }
    
    void summarizeOutputs(List stages) {
        
        Dependencies.instance.reset()
        def graph = Dependencies.instance.outputGraph
        List<PipelineFile> leaves = Dependencies.instance.findLeaves(graph)*.values.flatten()*.outputFile
        
        Utils.time("Save output graph") {
            Dependencies.instance.saveOutputGraphCache()
        }
        
        List<String> all = formatOutputFiles(leaves)
        
        if(all.size() == 1) {
            log.info "Output is " + all[0]
            rootContext.msg "Output is " + all[0]
        }
        else
        if(all) {
            if(all.size() <= 5) {
                log.info "Outputs are: \n\t\t" +  all.join("\n\t\t")
                rootContext.msg "Outputs are: \n\t\t" +  all.join("\n\t\t")
            }
            else {
                rootContext.msg "Outputs are: \n\t\t" +  all[0..4].join("\n\t\t") + "\n\t\t... ${all.size()-5} more ..." 
            }
        }
        else {
            log.info "No tracked output files from this run"
        }
        rootContext.outputLog.flush()
    }

    @CompileStatic
    private List<String> formatOutputFiles(List<PipelineFile> all) {
        
        final Map<String, PipelineFile> runFiles = Dependencies.theInstance.outputFilesGenerated.collectEntries {
            [ it.toString(), it]
        }

        List<String> result = (List<String>)all
        
            // Sort to put files generated by this run first
            .sort { runFiles.containsKey(it?.toString()) ? 0 : 1 }
        
            // Remove files that don't exist any more
            .grep { PipelineFile pf -> pf && pf.exists() }
            
            // Add 'pre-existing' for files that were not generated by this Bpipe run
            .collect {
                runFiles.containsKey(it.toString()) ? it : (it.toString() + ' (pre-existing)')
            }
            
        return result
    }
    
    /**
     * Convenience function to convert the given string to a file.
     * Also ensures that the file has a parent directory, which 
     * is useful so that the user can reliably refer to the folder
     * in which the file exists (otherwise, file.parentFile returns
     * null in certain cases, which can be unexpected).
     * 
     * @param fileName
     * @return
     */
    static File file(def fileName) {
        fileName = fileName.toString()
        File f = new File(fileName)
        if(!f.parentFile)
            f = new File(new File("."),fileName).canonicalFile
            
        if(!f.exists()) {
            new PipelineFile(f.path, StorageLayer.defaultStorage).flushMetadataAndCheckIfMissing(1000)
        }
        return f
    }
    
    List<String> cachedBranchPath = null
    
    @CompileStatic 
    List<String> getBranchPath() {
        
        if(cachedBranchPath != null)
            return cachedBranchPath
        
        Pipeline current = this
        List<String> branches = this.name ? (List<String>)[this.name] : (List<String>)[]
        while(current.parent != null && current.parent != current) {
            if(current.parent.name)
                branches.add(current.parent.name)
            current = current.parent
        }
        cachedBranchPath = branches
        return branches
    }
    
    /**
     * Compute a unique id for the current stage of this pipeline.
     * <p>
     * The id identifies the stage uniquely within the pipeline graph. Two separate
     * instances of the same stage within a pipeline will receive separate ids, while
     * two references at the same position in the graph (for example, executed in
     * parallel) will receive the same id.
     */
    String getStageId() {
        def result = stages.reverse().grep { PipelineStage stage ->
            stage instanceof FlattenedPipelineStage ||
                (!stage.synthetic && PipelineCategory.closureNames[stage.body])
        }.collect { PipelineStage stage ->
            if(stage instanceof FlattenedPipelineStage)
                stage.merged*.stageName.join(DefinePipelineCategory.stageSeparator)
            else
                stage.stageName 
        }.join(DefinePipelineCategory.stageSeparator)         
        
        println "Computed stage id: " + result
        
        return result
    }
    
    List<String> getUnappliedBranchNames() {
        if(nameApplied)
            return []
        Pipeline current = this
        List branches = this.name ? [this.name] : []
        while(current.parent != null && current.parent != current && !current.nameApplied) {
            if(current.parent.name)
                branches.add(current.parent.name)
            current = current.parent
        }
        return branches
    }
    
    /**
     * Define an optional variable, only if it was not already defined
     * 
     * @param variablesWithDefaults
     */
    static void var(Map variablesWithDefaults) {
        if(currentContext.get()) { // called from within pipeline stage
            currentContext.get().var(variablesWithDefaults)
        }
        else 
        for(Map.Entry e in variablesWithDefaults) {
            if(!Runner.binding.variables.containsKey(e.key)) {
                Runner.binding.variables[e.key] = e.value
            }
        }
    }
    
    static void options(Closure c) {
        
        String scriptName = new File(Config.config.script).name
        
        PrintStream oldErr = System.out
        ByteArrayOutputStream b = new ByteArrayOutputStream()
        System.out = new PrintStream(b)

        CliBuilder cli = new CliBuilder(
            usage:'bpipe run <bpipe options> ' + scriptName + ' <pipeline options> <input files>\n',
            width: Config.config.columns)
        cli.with(c)
        
        OptionAccessor opts = cli.parse(Runner.binding.variables.args)
        System.out = oldErr
        if(!opts) {
            printStartMessage(new Date())
            System.err.println "\nERROR: One or more pipeline options were invalid or missing.\n"
            System.err.println(b.toString())
            Runner.normalShutdown = true
            System.exit(1)
        }
        
        Runner.binding.variables.args = opts.arguments() as List
        Runner.binding.variables.opts = opts
    }
}
