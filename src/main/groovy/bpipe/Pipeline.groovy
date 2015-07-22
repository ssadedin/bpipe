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

import groovy.text.GStringTemplateEngine;
import groovy.text.GStringTemplateEngine.GStringTemplate;
import groovy.time.TimeCategory

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import groovy.util.logging.Log;
import groovy.xml.MarkupBuilder;

import java.util.logging.Level;
import java.util.regex.Pattern;

import org.codehaus.groovy.reflection.CachedMethod;

import bpipe.graph.Graph;
import static Utils.isContainer 
import static Utils.unbox 


/**
 * Utility to convert a Node structure to a Map structure (primarily, for export as Json)
 */
class NodeListCategory {
    static Map toMap(Node n) {
        return  n.children()?[name: n.name(), children: n.children()*.toMap()]:[name:n.name()]
    }
    static String toJson(Node n) {
        groovy.json.JsonOutput.toJson(n.toMap())
    }
}

/**
 * Main Pipeline class.  Used by client to start a pipeline
 * running by specifying initial inputs and setting up 
 * surrounding category to enable implicit pipeline stage functions.
 */
@Log
public class Pipeline implements ResourceRequestor {
    
    /**
     * Default imports added to the top of all files executed by Bpipe
     */
    static final String PIPELINE_IMPORTS = "import static Bpipe.*; import Preserve as preserve; import Intermediate as intermediate; import Accompanies as accompanies; import Produce as produce; import Transform as transform; import Filter as filter;"
    
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
    static Map<String,String> scriptNames = Collections.synchronizedMap([:])
    
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
    def stages = []
    
    /**
     * A list of "dummy" stages that are actually used to link other stages together
     */
    def joiners = []
    
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
     * Metadata about the branch within which this pipeline is running. 
     * The primary meta data is a name for the pipeline that is added to 
     * output file names when the pipeline is run as a child pipeline.
     * This is null and not used in the default, root pipeline
     */
    Branch branch = new Branch(name:"")
    
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
     * If a pipeline failed with an exception, it sets the exception(s) here
     */
    List<Throwable> failExceptions = []
    
    /**
     * If a pipeline fails but not with an exception, the reason, if known
     * is set here
     */
    String failReason = "Unknown"
    
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
                    result << new Chr('chr'+r, cfg)
                }
            }
            else 
            if(o instanceof String || o instanceof Integer) {
                result << new Chr('chr'+o, cfg)
            }
        }
        
        // If a region was specified on the command line or in config, 
        // check for overlap
        if(Config.userConfig.region) {
            if(result.any { it.name == Config.userConfig.region.value }) {
              result.clear()
              result.add(new Chr(Config.userConfig.region.value, cfg))
            }
            else {
                println "WARNING: region specified on command line or configuration (${Config.userConfig.region})  does not overlap regions specified in pipeline: $objs"
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
        if(!pipelineBuilder.binding.variables.containsKey("BPIPE_NO_EXTERNAL_STAGES"))
            pipeline.loadExternalStages()

        Object result = pipeline.execute([], pipelineBuilder.binding, pipelineBuilder, false)
        segmentJoiners.addAll(pipeline.joiners)
        return result
    }
    
    static List<Closure> segmentJoiners = []
    
    /**
     * Default run method - introspects all the inputs from the binding of the
     * pipeline closure.
     */
    static def run(Closure pipeline) {
       run(pipeline.binding.variables.args, pipeline.binding, pipeline) 
    }
    
    static def run(Object host, Closure pipeline) {
       run(pipeline.binding.variables.args, host, pipeline) 
    }
    
    static def run(def inputFile, Object host, Closure pipelineBuilder) {
        
        log.info("Running with input " + inputFile)
        
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
        
        Pipeline pipeline = new Pipeline()
        
        // To make life easier when a single argument is passed in,
        // debox it from the array so that pipeline stages that 
        // expect a single input do not unexpectedly get an array    
        inputFile = Utils.unbox(inputFile)
        
        PipelineCategory.addStages(host)
        if(!(host instanceof Binding))
            PipelineCategory.addStages(pipelineBuilder.binding)
            
        pipeline.loadExternalStages()
        pipeline.joiners += segmentJoiners

        def mode = Config.config.mode 
        if(mode == "run" || mode == "documentation" || mode == "register") // todo: documentation should be its own mode! but can't support that right now
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
     * Runs the specified closure in the context of this pipeline 
     * and both decrements and notifies the given counter when finished.
     */
    void runSegment(def inputs, Closure s) {
        try {
            
            currentRuntimePipeline.set(this) 
        
            this.rootContext = createContext()
            
            this.threadId = Thread.currentThread().id
            
            def currentStage = new PipelineStage(rootContext, s)
            currentStage.synthetic = true
            log.info "Running segment with inputs $inputs"
            this.addStage(currentStage)
            if(inputs instanceof List) {
                def inputCopy = []
                inputCopy.addAll(inputs)
                currentStage.context.@input = inputCopy
                currentStage.context.branchInputs = inputCopy
            }
            else {
                currentStage.context.@input = inputs
                currentStage.context.branchInputs = inputs
            }
            try {
                currentStage.run()
                log.info "Pipeline segment ${this.branch} in thread ${Thread.currentThread().id} has finished normally"
            }
            catch(UserTerminateBranchException e) {
                log.info "Pipeline segment ${this.branch} has terminated by 'succeed' in user script: $e.message"
                
                println "${new Date()} MSG: Branch ${branch=='all'?'':branch} completed: $e.message"
                aborted = true
            }
            catch(PipelineError e) {
                log.info "Pipeline segment failed (2): " + e.message
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
                log.severe "Internal error: " + e.toString()
                System.err.println "Internal error: " + e.toString()
                throw e
            }
        }
        finally {
            log.info "Finished running segment for inputs $inputs"
            Concurrency.instance.unregisterResourceRequestor(this)
        }
    }
    

    private Closure execute(def inputFile, Object host, Closure pipeline, boolean launch=true) {
        
        Pipeline.rootThreadId = Thread.currentThread().id
        this.threadId = Pipeline.rootThreadId
        Pipeline.rootPipeline = this
        
        // We have to manually add all the external variables to the outer pipeline stage
        this.externalBinding.variables.each { 
            log.info "Loaded external reference: $it.key"
            if(!pipeline.binding.variables.containsKey(it.key))
                pipeline.binding.variables.put(it.key,it.value) 
            else
                log.info "External reference $it.key is overridden by local reference"    
        }
        
        // We have to manually add all the external variables to the outer pipeline stage
        Pipeline.genomes.each { 
            log.info "Loaded genome reference: $it.key"
            if(!pipeline.binding.variables.containsKey(it.key))
                pipeline.binding.variables.put(it.key,it.value) 
            else
                log.info "Genome $it.key is overridden by local reference"    
        }
        
         // Add all the pipeline variables to the external binding
        this.externalBinding.variables += pipeline.binding.variables
        
        def cmdlog = CommandLog.cmdLog
        startDate = new Date()
        if(launch) {
            initializeRunLogs(inputFile)
        }
        
        Node pipelineStructure = launch ? diagram(host, pipeline) : null
        
        def constructedPipeline
        use(PipelineCategory) {
            
           
            // Build the actual pipeline
            Pipeline.withCurrentUnderConstructionPipeline(this) {
                
                constructedPipeline = pipeline()
                
                // See bug #60
                if(constructedPipeline instanceof List) {
                    currentRuntimePipeline.set(this)
                    constructedPipeline = PipelineCategory.splitOnFiles("*", constructedPipeline, false)
                }
            }
            
            if(launch) {
                EventManager.instance.signal(PipelineEvent.STARTED, "Pipeline started", [pipeline:pipelineStructure])
                
                launchPipeline(constructedPipeline, inputFile, startDate)
            }
        }

        // Make sure the command log ends with newline
        // as output is not terminated with one by default
        cmdlog << ""
       
        return constructedPipeline
    }
    
    /**
     * Run the pipeline, handling any errors and printing out results to the
     * console.
     * 
     * @param constructedPipeline
     * @param inputFile
     */
    void launchPipeline(def constructedPipeline, def inputFile, Date startDate) {
        
        def cmdlog = CommandLog.cmdLog
        
        String failureMessage = null
        try {
            this.checkRequiredInputs(Utils.box(inputFile))
            runSegment(inputFile, constructedPipeline)
                    
            if(failed) {
                failureMessage = ("\n" + failExceptions*.message.join("\n"))
            }
        }
        catch(PatternInputMissingError e) {
            new File(".bpipe/prompt_input_files." + Config.config.pid).text = ''
        }
        catch(InputMissingError e) {
            failureMessage = """
                A required input was missing from the files given as input.
                        
                         Input Type:  $e.inputType
                        Description:  $e.description""".stripIndent()
            failed = true
        }
                
        finishDate = new Date()
        if(Runner.opts.t && failed && failExceptions.empty) { 
            println("\n"+" Pipeline Test Succeeded ".center(Config.config.columns,"="))
        }
        else {
            println("\n"+" Pipeline ${failed?'Failed':'Succeeded'} ".center(Config.config.columns,"="))
        }
        if(failed) {
            println failureMessage
            println()
        }
                
        if(rootContext)
          rootContext.msg "Finished at " + finishDate
          
        about(finishedAt: finishDate)
        cmdlog << "# " + (" Finished at " + finishDate + " Duration = " + TimeCategory.minus(finishDate,startDate) +" ").center(Config.config.columns,"#")
               
        /*
        def w =new StringWriter()
        this.dump(w)
        w.flush()
        println w
        */
                
        // See if any checks failed
        List<Check> allChecks = Check.loadAll()
        List<Check> failedChecks = allChecks.grep { !it.passed && !it.override }
        if(failedChecks) {
            println "\nWARNING: ${failedChecks.size()} check(s) failed. Use 'bpipe checks' to see details.\n"
        }
        
        saveResultState(failed, allChecks, failedChecks) 
                
        EventManager.instance.signal(PipelineEvent.FINISHED, "Pipeline " + (failed?"Failed":"Succeeded"), 
            [ 
                pipeline:this, 
                checks:allChecks, 
                result:!failed, 
                startDate:startDate,
                finishDate:finishDate,
                commands: CommandManager.executedCommands
            ])
        
        if(!failed) {
            summarizeOutputs(stages)
        }
    }
    
    static String DATE_FORMAT="yyyy-MM-dd HH:mm:ss"
    
    void saveResultState(boolean failed, List<Check> allChecks, List<Check> failedChecks) {
       
        new File(".bpipe/results").mkdirs()
        
        // Compute the total runtime of all tools
        long commandTimeMs = CommandManager.executedCommands.sum {  Command cmd -> (cmd.stopTimeMs - cmd.startTimeMs) }
        if(commandTimeMs == null)
            commandTimeMs = 0
                  
        new File(".bpipe/results/${Config.config.pid}.xml").withWriter { w ->
            MarkupBuilder xml = new MarkupBuilder(w)
            xml.job(id:Config.config.pid) {
                succeeded(String.valueOf(!failed))
                startDateTime(startDate.format(DATE_FORMAT))
                endDateTime(finishDate.format(DATE_FORMAT))
                totalCommandTimeSeconds(commandTimeMs/1000)
                
                commands {
                   CommandManager.executedCommands.each {  Command cmd ->
                        command {
                            stage(cmd.name)
                            branch(cmd.branch.name)
                            content(cmd.command)
                            start(new Date(cmd.startTimeMs).format(DATE_FORMAT))
                            end(new Date(cmd.stopTimeMs).format(DATE_FORMAT))
                            exitCode(cmd.exitCode)
                        }
                   }
                }
            }
        }
    }
    
    void initializeRunLogs(def inputFile) {
        def cmdlog = CommandLog.cmdLog
        cmdlog.write("")
        String startDateTime = startDate.format("yyyy-MM-dd HH:mm") + " "
        cmdlog << "#"*Config.config.columns 
        cmdlog << "# Starting pipeline at " + (new Date())
        cmdlog << "# Input files:  $inputFile"
        cmdlog << "# Output Log:  " + Config.config.outputLogPath 
            
        OutputLog startLog = new OutputLog("----")
        startLog.bufferLine("="*Config.config.columns)
        startLog.bufferLine("|" + " Starting Pipeline at $startDateTime".center(Config.config.columns-2) + "|")
        startLog.bufferLine("="*Config.config.columns)
        startLog.flush()
            
        about(startedAt: startDate)
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
     * Create a new pipeline that is initialized with copies of all the same state
     * that this pipeline has.  The new pipeline can execute concurrently with this
     * one without interfering with this one's state.
     * <p>
     * Note:  the new pipeline is not run by this method; instead you have to
     *        call one of the {@link #run(Closure)} methods on the returned pipeline
     */
    Pipeline fork(Node branchPoint, String childName) {
        
        assert branchPoint in this.node.children()
        
        Pipeline p = new Pipeline()
        p.node = new Node(branchPoint, childName, [type:'pipeline',pipeline:p])
        p.stages = [] + this.stages
        p.joiners = [] + this.joiners
        p.aliases = this.aliases
        p.parent = this
//        branchPoint.appendNode(p.node)
        ++this.childCount
        return p
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
            
            libPaths.sort()
                
            // Load all the scripts from this path / folder
            libPaths.each { File scriptFile ->
                if(cache && allLoadedPaths.contains(scriptFile.canonicalPath)) {
                    log.info "Skip loading $scriptFile.canonicalPath (already loaded)"
                    return
                }
                
                log.info("Evaluating library file $scriptFile")
                try {
                    String scriptClassName = scriptFile.name.replaceAll('.groovy$','_bpipe.groovy')
                    Script script = shell.evaluate(PIPELINE_IMPORTS+
                        (includesLibs?" binding.variables['BPIPE_NO_EXTERNAL_STAGES']=true;":"") +
                        "bpipe.Pipeline.scriptNames['$scriptFile']=this.class.name;" +
                         scriptFile.text + "\nthis", scriptClassName)
                    
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
                    System.err.println("WARN: Error evaluating script $scriptFile: " + ex.getMessage())
                }
            }
          }
          PipelineCategory.addStages(shell.context)
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
    
    /**
     * Include pipeline stages from the specified path into the pipeline
     * @param path
     */
    static synchronized void load(String path) {
        File f = new File(path)
        if(!Utils.fileExists(f)) {
            // Attempt to resolve the file relative to the main script location if
            // it cannot be resolved directly
            f = new File(new File(Config.config.script).canonicalFile.parentFile, path)
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
     * Load the specified genome model into memory, possibly downloading it from UCSC
     * if necessary
     */
    static synchronized void genome(String name) {
        File genomesDir = new File(System.getProperty("user.home"), ".bpipedb/genomes")
        if(!genomesDir.exists())
            if(!genomesDir.mkdirs())
                throw new IOException("Unable to create directory to store genomes. Please check permissions for $genomesDir")
                
        
        // Construct a UCSC URL based on the given name and then download the genes from there
        File cachedGenome = new File(genomesDir, "${name}.ser.gz")
        RegionSet genome
        if(cachedGenome.exists()) {
            log.info "Loading cached genome : $cachedGenome"
            long startTimeMs = System.currentTimeMillis()
            genome = RegionSet.load(cachedGenome) 
            println "Finished loading genome $cachedGenome in ${System.currentTimeMillis() - startTimeMs} ms"
            
        } 
        else {
            String url = "http://hgdownload.soe.ucsc.edu/goldenPath/$name/database/ensGene.txt.gz"
            log.info "Downloading genome from $url"
            new URL(url).openStream().withStream { stream ->
                genome = RegionSet.index(stream) 
                genome.name = name
                new FileOutputStream(cachedGenome).withStream { outStream ->
                    new ObjectOutputStream(outStream) << genome
                }
            }
        }
                
        Pipeline.genomes[name] = genome
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
    Node diagram(Object host, Closure pipeline) {
        
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
                if(!(realizedBranch in PipelineCategory.closureNames)) {
                    realizedBranch()
                }
                else
                    DefinePipelineCategory.inputStage.appendNode(PipelineCategory.closureNames[realizedBranch])
            }
        }
        return DefinePipelineCategory.inputStage
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
    void renderMxGraph(Node root, String fileName, boolean editor) {
               
        // Now make a graph
        // println "Found stages " + DefinePipelineCategory.stages
        Graph g = new Graph(root)
        if(editor) {
            g.display()
        }
        else {
            
            def opts = Runner.opts
            
            String outputExtension = opts.f ? "."+opts.f : ".png"
            String outputFileName = fileName+outputExtension
            println "Creating diagram $outputFileName"
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
     * Stores the given stage as part of the execution of this pipeline
     */
    void addStage(PipelineStage stage) {
        synchronized(this.stages) {
            this.stages << stage
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
        List all = Dependencies.instance.findLeaves(Dependencies.instance.outputGraph)*.values.flatten()*.outputPath
        
        def runFiles = Dependencies.instance.outputFilesGenerated
        
        // Sort to put files generated by this run first
        all = all.sort {runFiles.contains(it) ? 0 : 1 }
                 // Remove files that don't exist any more
                 .grep { new File(it).exists() }.
                 // Add 'pre-existing' for files that were not generated by this Bpipe run
                 collect { runFiles.contains(it) ? it : it + ' (pre-existing)' }
        
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
        return f
    }
    
    List<String> getBranchPath() {
        Pipeline current = this
        List branches = this.name ? [this.name] : []
        while(current.parent != null && current.parent != current) {
            if(current.parent.name)
                branches.add(current.parent.name)
            current = current.parent
        }
        return branches
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
}
