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

import groovy.transform.CompileStatic
import groovy.util.logging.Log;
import groovy.xml.MarkupBuilder
import java.nio.file.Path
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher
import java.util.regex.Pattern;

import org.codehaus.groovy.runtime.ExceptionUtils;

import bpipe.executor.CommandExecutor
import bpipe.executor.ProbeCommandExecutor
import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.LocalPipelineFile
import bpipe.storage.StorageLayer
import bpipe.storage.UnknownStoragePipelineFile

/**
* This context defines implicit functions and variables that are
* made available to Bpipe pipeline stages.   These functions are
* only available inside the context of a Bpipe stage, when it is
* executed by Bpipe (ie. they are introduced at runtime).
* <p>
* Note: currently other functions are also made available in two
* other places: 
* <li>PipelineCategory
* <li>PipelineDelegate
*/
@Log
class PipelineContext {
    
    /**
     * File where half processed files will be listed on shutdown
     */
    public static File UNCLEAN_FILE_PATH = new File(".bpipe/inprogress")
    
    /**
     * This value is returned when a $thread variable is evaluated in a GString
     * (eg. a command from the user).  The reason for this is that Groovy eagerly
     * evaluates GStrings prior to passing them to a function. That means we can't
     * allow the function itself to set the value - as we wish to do for threads
     * when the user executes a command and we want to  pass the number of threads
     * evaluated "just in time". So this special value is set, and then 
     * it is replaced just before the command executes with an appropriate number
     * of threads for the command to use.
     */
    public final static String THREAD_LAZY_VALUE = '__bpipe_lazy_resource_threads__'
    
    public final static String MEMORY_LAZY_VALUE = '__bpipe_lazy_resource_memory__'
    
    /**
     * Create a Pipeline Context with the specified adidtional bound variables and
     * pipeline stages as well.
     *
     * @param extraBinding    extra variables to make referenceable by pipeline stages
     *                        that execute using this context
     * @param pipelineStages  list of known pipeline stages.  These are used to resolve
     *                        inputs when the user uses the implicit 'from' by appending
     *                        an extension to the implicit input variable (eg: $input.csv).
     *                        The stages are searched to find the most recent stage with
     *                        an output that matches the specified extension.
     *
     */
    public PipelineContext(Binding extraBinding, List<PipelineStage> pipelineStages, List<Closure> pipelineJoiners, Branch branch) {
        super();
        if(pipelineStages == null)
            throw new IllegalArgumentException("pipelineStages cannot be null")
            
        this.pipelineStages = pipelineStages
        this.extraBinding = extraBinding
        this.pipelineJoiners = pipelineJoiners
        this.initUncleanFilePath()
        this.threadId = Thread.currentThread().getId()
        this.branch = branch
        def pipeline = Pipeline.currentRuntimePipeline.get()
        if(pipeline) {
            this.applyName = pipeline.name && !pipeline.nameApplied
            this.aliases = pipeline.aliases 
        }
        this.outputLog = new OutputLog(branch.name)
    }
    
    /**
     * Additional variables that are injected into the pipeline stage when it executes.
     * In practice, these allow it to resolve pipeline stages that are loaded from external
     * files (which otherwise would not be in scope).
     */
    Binding extraBinding
    
    /**
     * Local variables are specific to this instance of this pipeline stage.
     * These are injected in and take values separately even if a stage is used 
     * twice in a single pipeline
     */
    Map<String,Object> localVariables = [:]
    
    /**
     * The stage name for which this context is running
     */
    String stageName = "Unknown"
    
    /**
     * The directory to which the pipeline stage should write its outputs
     */
    String outputDirectory = Config.config.defaultOutputDirectory
    
    /**
     * The id of the thread that created this context
     */
    Long threadId
    
    /**
     * The resources used by commands that run in this stage
     */
    Map<String,ResourceUnit> usedResources = [ "threads":new ResourceUnit(key: "threads", amount: 1)] 
    
    /**
     * Whether or not the user has invoked a 'uses { ... }' command on this context
     */
    boolean customThreadResources = false
   
    File uncleanFilePath
    
    /**
     * Set of aliases to use for mapping file names
     */
    Aliases aliases = null
   
    /**
     * Documentation attributes for the the pipeline stage
     * Mostly this is a map of String => String, but 
     * tool information is stored as {@link Tool} objects
     */
    Map<String, Object> documentation = [:]
    
    /**
     * A list of Checkers created during execution of the corresponding
     * pipeline stage.
     */
    List<Checker> checkers = []
   
    private List<PipelineStage> pipelineStages
   
    /**
     * Pipeline joiners are closures that are introduced by Bpipe for 
     * joining stages together. The context needs to know them (or
     * pass them on to others who need them) mainly to exclude
     * them from certain functions (eg: they shouldn't be displayed
     * to the user in diagrams, etc.)
     */
    private List<Closure> pipelineJoiners
    
    /**
     * Manager for commands executed by this context
     */
    CommandManager commandManager = new CommandManager()
      
   /**
    * All outputs from this stage. The key to this map 
    * is the Bpipe command id (different to process id).
    */
   Map<String,Command> trackedOutputs = [:]
   
   /**
    * Commands indexed by path to output file
    */
   Map<String,String> pathToCommandId = [:]
   
   /**
    * When a command is run, output variables that are referenced in 
    * the command are tracked.  This allows them then to be logged
    * in the audit trail and saved in the history database 
    * so that we know which command created the output.
    * <p>
    * This list is cleared with each new invocation 
    * by {@link #exec(String, boolean, String)}
    */
   List<String> referencedOutputs = []
   
   
   /**
    * A list of internally set inputs/outputs that are not visible to the user
    * (see use in Checker)
    */
   List<String> internalOutputs = []
   
   List<String> internalInputs = []
   
   List<PipelineFile> pendingGlobOutputs = []
   
   /**
    * A list of outputs that are to be marked as preserved.
    * These will not be deleted automatically by user initiated
    * cleanup operations (see {@link Dependencies#cleanup(java.util.List)}
    */
   List<String> preservedOutputs = []
   
   /**
    * A list of outputs that are to be marked as intermediate.
    * These will be deleted automatically by user initiated
    * cleanup operations (see {@link Dependencies#cleanup(java.util.List)},
    * even if they are leaf outputs from the pipeline
    */
   List<String> intermediateOutputs = []
   
   /**
    * A Map of files that are not independent outputs, but which
    * accompany other outputs, as declared by the user.
    * Key is output file name, value is accompanied input
    */
   Map<String,String>  accompanyingOutputs = [:]
   
   /**
    * List of patterns that match accompanying outputs
    */
   Pattern activeAccompanierPattern = null
   
   /**
    * Flag that can be enabled to cause missing properties to resolve to 
    * outputting the name of the property ie. a reference to $x will produce $x.
    * This allows for a crude pass-through of variables from Bpipe to Bash 
    * when executing commands.
    */
   boolean echoWhenNotFound = true
   
   /**
    * When set to true, the context should not execute any commands, it should only 
    * evaluate their arguments to probe $input and $output invocations
    * so that files that will use can be determined
    */
   boolean probeMode = false
   
   /**
    * The name for this segment of the pipeline.  The name is blank by default but 
    * is non-blank when pipeline branches are created from chromosomes or file name matches.
    */
   Branch branch 
   
   /**
    * A list of executable closures to be executed when the next produce statement completes
    */
   List<Closure> inputResets = []
   
   /**
    * The default output is set prior to the body of the a pipeline stage being run.
    * If the pipeline stage does nothing else but references $output then the default output is
    * the one that is returned.  However the pipeline stage may modify the output
    * by use of the transform, filter or produce constructs.  If so, the actual output
    * is stored in the output property.
    */
   private String defaultOutput
   
   /**
    * Log that will write output from messages and commands executed
    * by this context / stage
    */
   private OutputLog outputLog
   
    /**
     * Set the default inputs and outputs for this context according to the current
     * state of the given pipeline, and our stage name.
     * <p>
     * Note: initialize may not always be called - for silent "joiner" stages a context
     * is created but not initialized, because these have no inputs or outputs.
     * 
     * @param pipeline
     */
   void initialize(Pipeline pipeline, String stageName) {
        this.stageName = stageName
        if(this.@output == null && this.@defaultOutput == null) {
            
            String initialDefaultOutput = stageName
            if(this.@input) {
                // If we are running in a sub-pipeline that has a name, make sure we
                // reflect that in the output file name.  The name should only be applied to files
                // produced form the first stage in the sub-pipeline
                if(pipeline.name && !pipeline.nameApplied) {
                    initialDefaultOutput = String.valueOf(Utils.first(this.@input)) + "." + pipeline.name + "."+stageName
                    // Note we don't set pipeline.nameApplied = true here
                    // if it is really applied then that is flagged in PipelineContext
                    // Setting the applied flag here will stop it from being applied
                    // in the transform / filter constructs 
                }
                else {
                    initialDefaultOutput = String.valueOf(Utils.first(this.@input)) + "." + stageName
                }
            }
            
            // Ensure there is no directory attached to the default output
            this.defaultOutput = new File(initialDefaultOutput).name
        }
        
        branch.dirChangeListener = {
            outputTo(it)
        }
    }
   
   def getDefaultOutput() {
//       log.info "Returning default output " + this.@defaultOutput
       return this.@defaultOutput
   }
   
   def setDefaultOutput(defOut) {
       this.@defaultOutput = toOutputFolder(defOut)[0]
   }
   
   /**
    * The list of outputs that this pipeline stage is defined to produce. If specified, this
    * list is enforced. That is, if the user then tries to reference an incompatible 
    * output in one of their commands, they receive an error.
    * <p>
    * More flexible outputs are specified implicitly. These are 'inferred' outputs that
    * are tracked using allInferredOutputs. When a stage executes, this variable is initially
    * empty, and it is populated as the outputs get defined - either explicitly (eg: via produce)
    * or implicitly. When implicit, it only occurs after the stage executes (see #resolveOutputs)
    */
   private List<PipelineFile> output
   
   void setOutput(o) {
       setRawOutput(toOutputFolder(o))
   }
   
   /**
    * Set the specified output for this pipeline stage to the given
    * files.
    * 
    * @param outs   A possibly heterogenous mix of Strings and PipelineFile objects
    */
   void setRawOutput(List outs) {
       
       assert (outs == null) || (outs instanceof List)

       if(outs == null || outs.size()<20)
           log.info "Setting output $outs on context ${this.hashCode()} in thread ${Thread.currentThread().id}"
       else
           log.info "Setting ${outs.size()} outputs starting with ${outs[0..9]} on context ${this.hashCode()} in thread ${Thread.currentThread().id}"

       if(Thread.currentThread().id != threadId)
           log.warning "Thread output being set to $outs from wrong thread ${Thread.currentThread().id} instead of $threadId"
       
       this.@output = outs.collect { Object o ->
           
           assert !(o instanceof List)
           
           if((o instanceof PipelineFile) && !(o instanceof UnknownStoragePipelineFile))
               return o
           else {
               String oString = String.valueOf(o)
               StorageLayer storageLayer = resolveStorageFor(oString)
               if(storageLayer == null)
                   return null
               else
                   return new PipelineFile(oString, storageLayer)
           }
       }.grep { it != null }
           
       log.info "Actual output set: " + this.@output
   }
   
   @CompileStatic
   List<PipelineFile> getRawOutput() {
       this.@output
   }
   
   @CompileStatic
   StorageLayer resolveStorageFor(String outputPath, Map config = null, boolean strict=true) {
       
       if(config == null) {
           String commandId = pathToCommandId[outputPath]
           if(commandId == null) {
               if(!strict)
                   return null
           }
           
           if(commandId == null)
               return new UnknownStoragePipelineFile(outputPath).storage
               
           assert commandId != null
           Command command = trackedOutputs[commandId] 
           if(command == null)
               if(!strict)
                   return null
                   
           if(command == null) {
               log.severe "No command registered for output path $outputPath"
           }
           assert command != null
                  
           config = command.processedConfig
       }
       
       StorageLayer result = resolveStorageForConfig(config)
       log.info "Create storage layer ${result?.class?.name} for output $outputPath"
       return result
   }
   
   StorageLayer resolveStorageForConfig(Map config) {
       String storage = config.containsKey('storage') ? config.storage : null
       if(!storage)
           return new LocalFileSystemStorageLayer()
           
       return StorageLayer.create(storage)
   }
   
   /**
    * A synonym for the output directory, designed to allow commands that really have to know
    * what directory they are writing to access to it.
    */
   String getDir() {
       return this.@outputDirectory
   }
   
   /**
    * Outputs referenced through output property extensions 
    * since the last exec command.  The occurrence of an exec
    * clears this property.
    */
   List inferredOutputs = []
   
   /**
    * All outputs referenced through output property extensions during the 
    * execution of the pipeline stage
    */
   List allInferredOutputs = []
   
   /**
    * A list of inputs resolved directly by references to $input
    * and $input<x> variables. 
    * 
    * Note that inputs resolved by 
    * input variable properties (pseudo file extensions) are not tracked 
    * here but rather inside the PipelineInput wrapper object itself.
    * To get a complete list of resolved inputs, call the #getResolvedInputs
    * method.
    */
   List<PipelineFile> allResolvedInputs = []
   
   /**
    * The default output property reference.  Actually returns a quasi
    * String-like object that intercepts property references
    */
   def getOutput() {
       try {
           return getOutputImpl()
       }
       catch(Exception e) {
           e.printStackTrace()
           throw e
       }
   }
   
   def getOutputImpl() {
       
       String baseOutput = Utils.first(this.getDefaultOutput())
       def out = this.@output?.collect { it.path }
       if(out == null || this.currentFileNameTransform) { // Output not set elsewhere, or set dynamically based on inputs
           
           if(stageName == "world")
               println "Stage world:::"
           
           // If an input property was referenced, compute the default from that instead
           def allResolved = allUsedInputWrappers.collect { k,v -> 
               v.resolvedInputs 
           }
           
           allResolved = allResolved.flatten()
//           if(inputWrapper?.resolvedInputs) {
           if(allResolved) {
               
               // By default, if multiple inputs were resolved by the input wrapper,
               // we take the first UNLESS one of the inputs corresponds to a branching
               // file (a file responsible for splitting the pipeline into multiple parallel
               // paths). In the case of a branching file we use that in preference because 
               // otherwise multiple parallel paths will resolve to the same output.
               int defaultValueIndex = -1;
               if(branchInputs)
                   defaultValueIndex = allResolved.findIndexOf { PipelineFile inp -> branchInputs.any { it.path == inp.path } }
               if(defaultValueIndex<0)
                   defaultValueIndex = 0
                   
               PipelineFile resolved = Utils.unbox(allResolved[defaultValueIndex])
               
               log.info("Using non-default output due to input property reference: " + resolved + " from resolved inputs " + allResolved)
               
               if(this.currentFileNameTransform != null) {
                   out = this.currentFileNameTransform.transform(Utils.box(allResolved), this.applyName)
                   this.@output = toOutputFolder(out)
               }
               else
                   out = resolved.newName(resolved.name +"." + this.stageName)
                   
               this.checkAccompaniedOutputs([resolved])
               
               // Since we're resolving based on a different input than the default one,
               // the pipeline output wrapper should use a different one as a default too
               baseOutput = toOutputFolder(out)[0]
           }
           else {
               log.info "No inputs resolved by input wrappers: outputs based on default ${this.defaultOutput}"
               if(out == null)
                   out = this.getDefaultOutput()
           }
       }
       else {
           log.info "Using previously set output: ${this.@output}"
       }
      
       if(!out)
              out = getDefaultOutput()
                         
       out = toOutputFolder(out)
       
       Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
       String branchName = applyName  ? pipeline.unappliedBranchNames.join(".") : null
       
       def po = new PipelineOutput(out,
                                   this.stageName, 
                                   baseOutput,
                                   Utils.box(this.@output),
                                   { o,replaced -> onNewOutputReferenced(pipeline, o, replaced)}) 
       
       po.branchName = branchName
       if(this.currentFileNameTransform instanceof FilterFileNameTransformer)
         po.currentFilter = currentFileNameTransform
         
       po.resolvedInputs = this.resolvedInputs
       po.outputDirChangeListener = { outputTo(it) }
       
       if(this.activeAccompanierPattern)
           po.transformMode = "extend"
           
       return po
   }
   
    /**
     * A pipeline stage can specify outputs in different ways: explicitly (eg: via a 
     * prescriptive  produce() or transform() statement), or implicitly (eg: via references
     * to $output variables in exec statements). When outputs are specified explicitly,
     * they are set directly on this.@output. In that case we prioritise those.
     * However if outputs are not stated explicitly, we derive them by looking at 
     * all outputs that were referenced implicitly during the stage execution.
     * <p>
     * Here we finalise the outputs by transferring the implicit ones into the explicit
     * output set.
     */
   @CompileStatic
    void resolveOutputs() {
        if(!this.@output && this.allInferredOutputs) {
            log.info "Using inferred outputs $allInferredOutputs as outputs because no explicit outputs set"
            this.setRawOutput(this.allInferredOutputs)
        }
    }
   
   /**
    * Outputs that have been replaced by overriding from a filter whose extension was 
    * inferred by an output property reference
    */
   List<String> replacedOutputs = []
   
   /**
    * Called by the embedded {@link PipelineOutput} object
    * that wraps the $output variable whenever a new output
    * is referenced in a pipeline.
    * 
    * @param pipeline
    * @param replaced   if the output replaces a previously inferred output, 
    *                   the output that should be replaced. This happens when
    *                   an output is inferred first using $output but then that
    *                   becomes replaced by in input extension reference using
    *                   $output.bam
    */
   void onNewOutputReferenced(Pipeline pipeline, Object o, String replaced = null) {
       
       assert o != null
       assert !(o instanceof List)
       
       if(!allInferredOutputs.contains(o)) 
           allInferredOutputs << o; 
       if(!inferredOutputs.contains(o)) 
           inferredOutputs << o;  
       
       if(replaced) {
           allInferredOutputs.remove(replaced)
           inferredOutputs.remove(replaced)
       } 
       
       if(applyName && pipeline) { 
           pipeline.nameApplied=true
        } 
        
       if(replaced)  {
           this.setRawOutput(Utils.box(this.@output).collect { 
               if(it.path == replaced) { replacedOutputs.add(replaced) ; return o } else { return it } 
           })
       }
   }
   
   def getOutputs() {
       def raw =  getOutput()
       // Used to 'box' the result, but now this is a PipelineOutput that supports direct access by index, 
       // so should not need it
//       def result =  Utils.box(raw)
       raw.multiple = true
       return raw
   }
   
   def getOutputByIndex(int index) {
       try {
           PipelineOutput origOutput = getOutput()
           def o = Utils.box(origOutput.output)
           def result = o[index]
           String origDefaultOutput = origOutput.defaultOutput
           if(result == null) {
               log.info "No previously set output at $index from ${o.size()} outputs. Synthesizing from index based on first output"
               if(o[0].indexOf('.')>=0) {
                   result = o[0].replaceAll("\\.([^.]*)\$",".${index+1}.\$1")
                   origDefaultOutput = origDefaultOutput.replaceAll("\\.([^.]*)\$",".${index+1}.\$1")
               }
               else
                   result = o[0] + (index+1)
           }
           
           log.info "Query for output $index base result = $result"
           
           // result = trackOutput(result)
           
           Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
           
           def overrideOutputs = (origOutput.overrideOutputs && origOutput.overrideOutputs.size() >= index ? [ origOutput.overrideOutputs[index] ] : [] )
           
           return new PipelineOutput(result, 
                                     origOutput.stageName, 
                                     origDefaultOutput,
                                     overrideOutputs, { op,replaced -> onNewOutputReferenced(pipeline, op, replaced)}) 
       }
       catch(Exception e) {
           e.printStackTrace()
       }
   }
     
   private trackOutput(def output) {
       log.info "Tracking output $output"
       referencedOutputs << output
       return output
   } 
   
   void var(Map values) {
       values.each { k,v ->
           if(!this.localVariables.containsKey(k) && !this.extraBinding.variables.containsKey(k) && !Runner.binding.variables.containsKey(k) && !branch.properties.containsKey(k)) {
               log.info "Using default value of variable $k = $v"
               if(v instanceof Closure)
                 this.localVariables[k] = v()
               else
                 this.localVariables[k] = v
           }
       }
   }
   
   void requires(Map values) {
       values.each { k,v ->
           
           if([localVariables, 
               extraBinding.variables, 
               Runner.binding.variables, 
               branch.properties,
               Pipeline.currentRuntimePipeline.get().variables].any { it.containsKey(k) }) 
           {
               // Variable found, OK
               return
           }
           
           throw new PipelineError(
           """
                   Pipeline stage ${this.stageName} requires a parameter $k but this parameter was not specified

                   You can specify it in the following ways:

                           1. add 'using' to the pipeline definition: ${this.stageName}.using($k:<value>)
                           2. define the variable in your pipeline script: $k="<value>"
                           3. provide it from the command line by adding a flag:  -p $k=<value>

                   The parameter $k is described as follows:

                           $v
           """.stripIndent())
       }
   }
    
    /**
    * Coerce all of the arguments (which may be an array of Strings or a single String) to
    * point to files in the local directory.
    * This method is (and must remain) side-effect free
    */
   List<String> toOutputFolder(outputs) {
       
       List boxed = Utils.box(outputs)
       
       if(outputDirectory == null)
           Utils.toDir(boxed, ".")
       else
           Utils.toDir(boxed, outputDirectory)
   }
   
   void checkAccompaniedOutputs(List<PipelineFile> inputsToCheck) {
       def outDir = this.outputDirectory
       if(((outDir == null) || (outDir==".")) && this.activeAccompanierPattern) {
           
           List<PipelineFile> resolved = getResolvedInputs() + inputsToCheck
           
           PipelineFile matchedInput = resolved.find { this.activeAccompanierPattern.matcher(it.toString()) }
           
           // If one of the resolved inputs matches an accompanying pattern, then it should
           // output to the same directory as the output
           if(matchedInput) {
               log.info "Input $matchedInput matches accompanier pattern $activeAccompanierPattern"
               
               // TODO - CLOUD - should really be java.nio.path ops
               File f = new File(matchedInput.toString())
               if(!f.parentFile)
                   f = new File(new File("."),f.name)
                   
               this.outputDirectory = f.parentFile.path
           }
       }
   }
   
   /**
    * Input(s) to a pipeline stage 
    */
   List<PipelineFile> input
   
   /**
    * Wrapper that intercepts calls to resolve input properties. This is what
    * is returned to a pipeline stage, not the raw input!
    */
   PipelineInput inputWrapper
   
   /**
    * All input wrappers that got referenced during a pipeline stage, keyed on 
    * index
    */
   Map<Integer,PipelineInput> allUsedInputWrappers = new TreeMap()
   
   /**
    * If this context is spawning a new branch in the pipeline, the inputs that
    * are responsible for the branch are set here. This is used in certain cases
    * to set a different default output
    */
   List<PipelineFile> branchInputs
   
    /**
     * The inputs to be passed to the next stage of the pipeline.
     * Usually this is the same as context.output but it doesn't have
     * to be.
     */
   List<PipelineFile> nextInputs
   
   
   @CompileStatic
   List<PipelineFile> getRawInput() {
       return this.@input
   }
   
   /**
    * Return the value of the specified input<n> where n is the 
    * parameter supplied (index of input to resolve).
    * 
    * @param i
    * @return
    */
   PipelineInput getInputByIndex(int i) {
       
       
       PipelineInput wrapper = new PipelineInput(this.@input, pipelineStages, this.aliases)
       wrapper.currentFilter = currentFilter
       wrapper.defaultValueIndex = i
       
       def boxed = Utils.box(input)
       if(boxed.size()<i) {
           wrapper.parentError = new InputMissingError("Stage '$stageName' expected $i or more inputs but fewer provided", this)
       }
       else {
           this.allResolvedInputs << input[i]
       }
       
       if(!inputWrapper) 
         this.inputWrapper = wrapper
       
       if(!allUsedInputWrappers.containsKey(i)) {
           allUsedInputWrappers[i] = wrapper
       }    
       return wrapper
   }
  
    /**
    * Check if there is an input, if so, return it.  If not,
    * throw a helpful error message.
    * <br>
    * Note: if you wish to access the 'input' property without performing
    * this check (eg: to check if it is empty yourself) then you can use
    * direct property access - eg: ctx.@input
    * @return
    */
   def getInput() {
       if(this.@input == null || Utils.isContainer(this.@input) && this.@input.size() == 0) {
           throw new InputMissingError("Stage '$stageName' expects an input but none are available", this)
       }
       if(!inputWrapper || inputWrapper instanceof MultiPipelineInput) {
           inputWrapper = new PipelineInput(this.@input, pipelineStages, this.aliases)
           this.allUsedInputWrappers[0] = inputWrapper
       }
       inputWrapper.currentFilter = currentFilter    
       return inputWrapper
   }
   
   def setInput(def inp) {
       this.@input = Utils.box(inp)
   }
   
   def getInputs() {
       if(!inputWrapper || !(inputWrapper instanceof MultiPipelineInput)) {
           this.inputWrapper = new MultiPipelineInput(this.@input, pipelineStages, this.aliases)
           this.allUsedInputWrappers[0] = inputWrapper
       }
       inputWrapper.currentFilter = currentFilter    
       return this.inputWrapper;
   }
   
   /**
    * Output stream, only opened if the stage body references
    * the "out" variable
    */
   FileOutputStream outFile
   
   /**
    * Iterate through the file line by line and pass each line to the given closure.
    * Output lines for which the closure returns true to the output.
    * Lines beginning with # will not be passed to the body, 
    * but will be automatically output.
    */
   void filterLines(Closure c) {
       
       if(!input)
           throw new PipelineError("Attempt to grep on input but no input available")
           
       if(Runner.opts.t)
           throw new PipelineTestAbort("Would execute filterLines on input $input")
       
       PipelineFile usedInput = Utils.first(input)    
       
//       if(!probeMode && !(usedInput.storage instanceof LocalFileSystemStorageLayer))
//           throw new PipelineError("Built in filtering only works with local file system storage")
       
       // Do this just to create the file - otherwise it doesn't get created at all for
       // an empty input file
       getOut()
       
       if(!probeMode) {
           new File(usedInput.path).eachLine {  line ->
               if(line.startsWith("#") || c(line))
                      getOut() << line << "\n"
           }
       }
       
       this.allResolvedInputs << usedInput
       
       log.info "Filter lines in context " + this.hashCode() + " resulted in resolved inputs " + this.allResolvedInputs
   }
   
   void filterRows(Closure c) {
       if(probeMode)
           return
       
       if(!input)
           throw new PipelineError("Attempt to grep on input but no input available")
           
       if(Runner.opts.t)
           throw new PipelineTestAbort("Would execute filterRows on input $input")
           
       String fileName = Utils.first(this.getOutput())
       if(Runner.opts.t)
           throw new PipelineTestAbort("Would write to output file $fileName")
        
       def outStream = new FileOutputStream(fileName)
  
       File f = new File(isContainer(input)?input[0]:input)
       boolean first = true
       List<String> header = null
       f.eachLine {  line ->
           if(first) {
             first = false
             if(line.startsWith('#') && !line.startsWith('##')) {
                 header = line.substring(1).split('\t').collect { it.trim() }
             }  
           }
           
           def cols
           if(!line.startsWith('#')) {
               cols = line.split('\t')
               if(header) {
                   header.eachWithIndex { key,i ->
                       this.localVariables[key] = cols[i]
                   }
               }
               this.localVariables["col"] = cols
           }
           
           if(line.startsWith("#"))
               outStream << line
           else 
           if(c(cols)) {
               outStream << cols.join("\t") << "\n"
           }
       }       
       
       
       String commandId = CommandId.newId()
       Command command = new Command(id:commandId, command: "filterRows")
       this.trackedOutputs[command.id] = command
       this.pathToCommandId[fileName] = commandId
       command.outputs << fileName 
   }
   
   
   /**
    * Search in the input file for lines matching speficied regex pattern
    * For each line found, if a function is passed, call it, otherwise
    * output the matching line to the default output file.
    * <p>
    * Lines starting with '#' are treated as comments or headers and are
    * not passed to the body.
    */
   def grep(String pattern, Closure c = null) {
       if(probeMode)
           return
           
       if(!input)
           throw new PipelineError("Attempt to grep on input but no input available")
           
       def regexp = Pattern.compile(pattern)
       new File(isContainer(input)?input[0]:input).eachLine {
           if(it.startsWith("#"))
               return
               
           if(it =~ regexp) {
              if(c != null)
                  c(it)
              else
                  getOut() << it << "\n"
           }
       }
   }
   
   /**
    * @deprecated   this resolves files directly from the file system when it should 
    *               actually search the pipeline branch hierarchy.
    * 
    * @param patterns
    * @return
    */
   List<String> glob(String... patterns) {
       
       // By default, we get the storage from one of the inputs
       StorageLayer storage
       if(this.@input?.any { !(it.storage instanceof UnknownStoragePipelineFile) }) {
           storage = this.@input[0].storage
       }
       else {
           storage = new LocalFileSystemStorageLayer()
       }
       
       FileGlobber globber = new FileGlobber(storage:storage)
       
       def result = []
       for(String p in patterns) {
           List<String> globResults = globber.glob(p)
           result.addAll(globResults)
       }
       return result
   }
   
   /**
    * Returns an output stream to which the current pipeline stage can write
    * directly to create output.
    *
    * @return
    */
   OutputStream getOut() {
       
       PipelineFile fileName = new LocalPipelineFile(Utils.first(getOutput()).toString())
       if(!outFile) {
         if(Runner.opts.t)
             throw new PipelineTestAbort("Would write to output file $fileName")
          
         outFile = probeMode ? null : new FileOutputStream(fileName.toString())
       }
       
       String commandId = CommandId.newId()
       Command cmd = new Command(id:commandId, command: "<streamed>", outputs:[fileName])
       cmd.setRawProcessedConfig(storage:'local')
       this.trackedOutputs[cmd.id] = cmd
       this.pathToCommandId[fileName] = commandId
       
       return outFile
   }
   
   private boolean applyName = false
   
   /**
    * Specifies an output that converts an input file to a different kind of file,
    * ("transforms" it). Convenience wrapper for {@link #produce(Closure, Object, Closure)}
    * for the case where the file extension is replaced with a new one.
     * <p>
     * <b>Note</b>: This is a "magic" method invoked by the user 
     * as "transform" - see {@link PipelineDelegate#methodMissing(String, Object)}
     * for how this actually happens.
     */
   Object transformImpl(List<String> exts, Closure body) {
       
       def tx = new TransformOperation(this,exts,body)
       if(body)
           tx.execute()
       else
           return tx
   }
 
   List<String> currentFilter = []
   
   /**
    * Transform to be applied to inputs to determine output file names
    * Used by filter below, to ensure that inputs get remapped if a different
    * input is inferred by the actual command executed. If set, this is re-executed
    * when the output is queried (see #getOutput())
    */
   FileNameTransformer currentFileNameTransform = null
    
    /**
     * Specifies an output that keeps the same type of file but modifies 
     * it ("filters" it). Convenience wrapper for {@link #produce(Closure, Object, Closure)}
     * for the case where the same file extension is kept but a transformation
     * type is added to the name.
     * <p>
     * <b>Note</b>: This is a "magic" method invoked by the user 
     * as "filter" - see {@link PipelineDelegate#methodMissing(String, Object)}
     * for how this actually happens.
     */
    Object filterImpl(List<String> types, Closure body) {
        
        def boxed = Utils.box(this.@input)
            
        this.currentFilter = (boxed + Utils.box(this.pipelineStages[-1].originalInputs)).grep { PipelineFile f ->
             f.path.indexOf('.')>=0  // only consider file names that actually contain periods
        }.collect { PipelineFile f ->
            Utils.ext(f.path) // For each such file, return the file extension
        }
        
        this.currentFileNameTransform = new FilterFileNameTransformer(types: types, exts: currentFilter)
        
        def files = currentFileNameTransform.transform(boxed, applyName)
        
        // Coerce any inputs coming from different folders to the correct output folder
        files = toOutputFolder(files)
        produceImpl(files, body)
        
        this.currentFilter = []
        this.currentFileNameTransform = null
    }
    
  
    /**
     * Specifies that the given output(s) (out) will be produced
     * by the given closure, and skips execution of the closure
     * if the output(s) are newer than all the current inputs.  
     * <p>
     * The outputs can contain glob characters to specify filename
     * patterns rather than concrete file names.  In such cases, 
     * <code>produce</code> specifies that at <i>least</i> one 
     * file name matching the glob pattern will be produced by the
     * body, and execution is skipped only if all files matching
     * the glob are newer than the inputs to the stage. All files
     * matching the glob become outputs of the pipeline stage.
     * Note that no checking is done to really verify that the
     * files were actually created by commands run by the body; if
     * the files were really in earlier stages then they will
     * be recapitulated as outputs again in this stage which
     * may result in undesirable behavior.
     * <p>
     * <b>Note</b>: This is a "magic" method invoked by the user 
     * as "produce" - see {@link PipelineDelegate#methodMissing(String, Object)}
     * for how this actually happens.
     * 
     * @TODO above issue can possibly be mitigated if we do an 
     *       upwards hierachical search to check outputs from prior
     *       stages
     * @TODO the case where an output directory is set is not yet
     *       properly handled in the glob matching
     *       
     */
    Object produceImpl(Object out, Closure body) { 
        produceImpl(out,body,false)
    }
    
    Object produceImpl(Object rawOut, Closure body, boolean explicit /* the end user actually invoked produce themself */) { 
        
        log.info "Producing $rawOut from $this"
        
        // out could be a mix of these types of objects
        // 
        //  (a) Strings (b) Pattern (c) PipelineFile (d) PipelineInput (d) PipelineOutput
        // 
        // In most cases, they should be converted back to Strings
        //
        List out = rawOut.collect {
            if(it instanceof Pattern)
                it
            else
                String.valueOf(it)
        }
        
        List globOutputs = Utils.box(toOutputFolder(Utils.box(out).grep { it instanceof Pattern || it.contains("*") }))
        
        // Coerce so that files go to the right output folder
        if(explicit) { // User invoked produce directly. In that case, if they put a directory into the produce
                       // we should preserve it
            out = Utils.box(out).collect { it.toString().contains("/") ? it : toOutputFolder(it)[0]  }
        }
        else {
            out = toOutputFolder(out)
        }
        
        def lastInputs = this.@input
        boolean doExecute = true
        
        List fixedOutputs = 
            Utils.box(out).grep { !(it instanceof Pattern) &&  !it.contains("*") }
                          .collect { f ->
                              new UnknownStoragePipelineFile(f)
                          } 
        
        // Check for all existing files that match the globs
        List globExistingFiles = globOutputs.collect { 
            new GlobPipelineFile(it)
        }.flatten()
        
        // No inputs were newer than outputs,
        // but were the commands that created the outputs modified?
        this.setRawOutput(fixedOutputs)

        // Probing can be nested: ie, an outer function can initiate probe mode
        // and then call this one, so we need to ensure that we restore
        // the state  upon exit
        boolean oldProbeMode = this.probeMode
        boolean probeFailure = false
        this.probeMode = true
        this.trackedOutputs = [:]
        this.pathToCommandId = [:]
        List<Command> candidateCommandsToAssociate = []
        Command associatedCommand = null
        StorageLayer associatedStorage = null
        
        try {
            PipelineDelegate.setDelegateOn(this, body)
            log.info("Probing command using inputs ${this.@input}")
            List oldInferredOutputs = this.allInferredOutputs.clone()
            try {
                body()
            }
            catch(PipelineError e) {
                throw e
            }
            catch(Exception e) {
                log.info "Exception occurred during probe: $e.message"
                Utils.logException log, "Exception during probe: ", e
                probeFailure = true
            }
            log.info "Finished probe"

            log.info "Tracked commands after probe are: " + this.trackedOutputs*.key.join(',')
            
            // For now, treat the last command as the one to associate: this will be possibly confusing
            // if multiple commands exist with different storage
            candidateCommandsToAssociate += this.trackedOutputs*.value.sort { -it.id.toInteger()  }[0]
            associatedCommand = candidateCommandsToAssociate[0]
            if(associatedCommand != null) {
                associatedStorage = resolveStorageForConfig(associatedCommand.getProcessedConfig())
            }
            else
            if(!this.aliases.aliases.isEmpty()) { // can we get storage from an input alias?
                // For now, we will use just the storage of the first alias
                // aliasing outputs to inputs derived from multiple different storages 
                // will cause an issue here
                associatedStorage = this.aliases.aliases*.value[0]?.to?.storage
            }
            
            assert associatedStorage != null : "Unable to find any storage to associate to outputs"
            
            // Set the storage on any glob outputs that need it
            globExistingFiles*.setStorage(associatedStorage)
            
            this.pendingGlobOutputs += globExistingFiles

            def allInputs = (getResolvedInputs()  + Utils.box(lastInputs)).unique()
            
            // Associate storage to any outputs that did not resolve storage already
            fixedOutputs = fixedOutputs.collect { o ->
                if(o instanceof UnknownStoragePipelineFile)
                    new PipelineFile(o.path, associatedStorage) 
                else
                    o
            }

            def outputsToCheck = fixedOutputs.clone()
            List newInferredOutputs = this.allInferredOutputs.clone()
            newInferredOutputs.removeAll(oldInferredOutputs)
            outputsToCheck.addAll(newInferredOutputs)

            // In some cases the user may specify an output explicitly with a direct produce(...)
            // but then not reference that output variable at all in any of their
            // commands. In such a case the command should still execute, even though the command
            // would seem not to create the output - if we can't see any other way the output
            // is going to get created, we infer that the command is going to create it "magically"
            // see produce_and_output_function_no_output_ref test.
            for(def o in fixedOutputs) {
                if(explicit && !trackedOutputs.containsKey(o) && !inferredOutputs.contains(o)) {
                    this.internalOutputs.add(o)
                }
            }
            

            log.info "Checking " + (outputsToCheck + globExistingFiles)
            if(probeFailure) {
                log.info "Not up to date because probe failed"
            }
            else {
                List<PipelineFile> unaliasedInputs = allInputs.collect { aliases[it] }
                List<String> outOfDateOutputs = Dependencies.instance.getOutOfDate(outputsToCheck + globExistingFiles, unaliasedInputs)
                if(outOfDateOutputs) {
                    log.info "Not up to date because input inferred by probe of body newer than outputs"
                }
                else
                if(!Config.config.enableCommandTracking || !checkForModifiedCommands()) {
                    msg("Skipping steps to create ${Utils.box(out).unique()} because " + (lastInputs?"newer than $lastInputs" : " file already exists"))
                    log.info "Skipping produce body"
                    doExecute = false
                }
                else {
                    log.info "Not skipping because of modified command"
                }
            }
        }
        finally {
            this.probeMode = oldProbeMode
        }
        
        List boxedOutputs = Utils.box(this.@output)
        
        // If resuming, the command may actually be running
        // In that case we want to wait for it and then skip if it executed
        if(Config.config.mode == "resume") {
            doExecute = waitForResumableOutputs(boxedOutputs)
        }
        
        if(doExecute) {
            if(boxedOutputs) {
                this.setRawOutput(
                    (fixedOutputs  + globExistingFiles).unique { it.path }
                )
                this.removeReplacedOutputs()
            }
            else {
                this.setRawOutput(fixedOutputs)
            }
             
            // Store the list of output files so that if we are killed 
            // they can be cleaned up
            this.uncleanFilePath.text += Utils.box(this.output)?.join("\n") 
            
            PipelineDelegate.setDelegateOn(this, body)
            log.info("Producing from inputs ${this.@input}")
            body()
        }
        
        if(this.@output) {
            log.info "Adding outputs " + this.@output + " as a result of produce"
           
            String commandId = "-1"
            this.@output.each { PipelineFile o ->
                
                // If no inputs were resolved, we assume generically that all the inputs
                // to the stage were used. This is necessary to deal with
                // filterLines which doesn't trigger inferred inputs because
                // it does not execute at all
                if(!allResolvedInputs && !this.inputWrapper?.resolvedInputs) {
                    
                    allResolvedInputs.addAll(Utils.box(this.@input))
                }
                
                if(commandId == "-1")
                    commandId = CommandId.newId()
  
                // It's possible the user used produce() but did not actually reference
                // the output variable anywhere in the body. In that case, we
                // don't know which command used the output variable so we add an "anonymous" 
                // output
                trackOutputIfNotAlreadyTracked(o, "<produce>", commandId)
            }
        }
        
        if(globOutputs) {
            List<Path> normalizedInputs = Utils.box(this.@input).collect { PipelineFile pf -> pf.toPath().normalize().toAbsolutePath() }
            for(pattern in globOutputs) {
                
                FileGlobber glob = new FileGlobber(storage:associatedStorage)
                def result = glob.glob(pattern)
                
                // We are not interested in any outputs that were actually inputs
                // But why are only direct inputs and not prior inputs from upstream stages considered?
//                def result = Utils.glob(pattern).grep {  !normalizedInputs.contains( new File(it).absolutePath) }
                
                log.info "Found outputs for glob $pattern: [$result]"
                
                String commandId = associatedCommand?.id?:"-1"
                
                result.each { String output ->
                    
                    if(commandId == "-1")
                        commandId = CommandId.newId();
                        
                    pathToCommandId[output] = commandId
                    trackOutputIfNotAlreadyTracked(output, "<produce>", commandId) 
                }
                
                if(Utils.box(this.@output))
                    this.setRawOutput(this.@output + result)
                else
                    this.setRawOutput(result)
            }
        }
        
        this.currentFileNameTransform = null

        this.inputResets.each { it() }
        this.inputResets = []
        
        return out
    }
    
    @CompileStatic
    void removeReplacedOutputs() {
        this.@output.removeAll { PipelineFile f -> 
            (f.path in replacedOutputs)  || (toOutputFolder(f.path)[0] in replacedOutputs)
        }        
    }
    
    @CompileStatic
    void trackOutputIfNotAlreadyTracked(String o, String command, String commandId) { 
        trackOutputIfNotAlreadyTracked(new PipelineFile(o, resolveStorageFor(o)), command, commandId)
    }
    
    @CompileStatic
    void trackOutputIfNotAlreadyTracked(PipelineFile o, String command, String commandId) {
        String path = o.path
        if(!(path in this.referencedOutputs) && !(path in this.inferredOutputs) && !(path in this.allInferredOutputs)) {
             if(!this.trackedOutputs[commandId]) {
                 this.trackedOutputs[commandId] = new Command(id: commandId, outputs: [o], command: command)
                 this.pathToCommandId[path] = commandId
             }
             else
                 this.trackedOutputs[commandId].outputs << o
        } 
    }
    
    /**
     * Cause output files created by the given closure, and which also match the 
     * given pattern to be preserved.
     * @param pattern
     */
    @CompileStatic
    void preserveImpl(List patterns, Closure c) {
        def oldFiles = getAllTrackedOutputs()
        c()
        List<String> matchingOutputs = findMatchingOutputs(patterns)
        
        matchingOutputs.removeAll(oldFiles)
        
        log.info "Files not in previously created outputs but matching preserve patterns $patterns are: $matchingOutputs"
        for(Map.Entry<String,Command> entry in trackedOutputs) {
            List<PipelineFile> preserved = entry.value.outputs.grep { PipelineFile f -> matchingOutputs.contains(f.toPath().normalize()) }
            log.info "Outputs $preserved marked as preserved from stage $stageName by patterns $patterns"
            
            this.preservedOutputs.addAll(preserved.toString())
        }
    }
    
    /**
     * 
     * @param patterns
     * @return
     */
    List<String> findMatchingOutputs(List patterns) {
        return patterns.collect { 
            Utils.glob(it).collect { new File(it).canonicalPath } // not sure what to do here
                                                                  // how will it behave with non-local storage provider?
        }.flatten()
    }
    
    /**
     * Cause output files created by the given closure to be considered
     * intermediate files and hence be cleaned up by a user initiated 
     * cleanup operation, even if they are leaf outputs from the pipeline.
     */
    void intermediate(String pattern, Closure c) {
        def oldFiles = getAllTrackedOutputs()
        c()
        List<String> matchingOutputs = Utils.glob(pattern) - oldFiles
        log.info "Files not in previously created outputs but matching intermediate patterns $pattern are: $matchingOutputs"
        for(def entry in trackedOutputs) {
            def intermediates = entry.value.outputs.grep { matchingOutputs.contains(it) }
            log.info "Outputs $intermediates marked as intermediate files from stage $stageName by pattern $pattern"
            this.intermediateOutputs += intermediates
        }        
    }
    
    /**
     * Cause output files created by the given closure to be considered
     * accompanying outputs whose lifecycle depends on that of their 
     * input or companion file. The prototypical example here is a 
     * .bai file that exists only to be an index for its .bam file.
     */
    void accompanies(String pattern, Closure c) {
        def oldFiles = getAllTrackedOutputs()
        this.activeAccompanierPattern = FastUtils.globToRegex(pattern)
        try {
          c()
          List<String> newOutputs = getAllTrackedOutputs() - oldFiles
          log.info "Found accompanying outputs : ${newOutputs}"
          if(!pattern.contains("*")) {
              pattern = "*."+pattern;
          }
          def accompanied = getResolvedInputs().grep { activeAccompanierPattern.matcher(it.path).matches() }
          if(accompanied) {
              for(def accompanyingOutput in newOutputs) {
                  log.info "Inputs $accompanied are accompanied by $accompanied (only first will be used)"
                  this.accompanyingOutputs[accompanyingOutput] = accompanied[0].toString()
                  forward(accompanied[0])
              }        
          }
          else
              log.warning "No accompanied inputs found for outputs $newOutputs using pattern $pattern from inputs ${getResolvedInputs()}"
        }
        finally {
            this.activeAccompanierPattern = null
        }
    }
    
    List<String> getAllTrackedOutputs() {
        trackedOutputs.values()*.outputs.flatten().unique()       
    }
    
    void uses(ResourceUnit newResources, Closure block) {
        resources([newResources], block)
    }
    
    void uses(ResourceUnit r1, ResourceUnit r2, Closure block) {
        resources([r1,r2], block)
    }
    
    void uses(ResourceUnit r1, ResourceUnit r2, ResourceUnit r3, Closure block) {
        resources([r1,r2], block)
    }
    
    void uses(Map resourceSpec, Closure block) {
        List<ResourceUnit> res = resourceSpec.collect { e ->
            String name = e.key
            int n = 1
            int max = 0
            try {
                if(e.value instanceof String)
                    n = Integer.parseInt(e.value)
                else 
                if(e.value instanceof IntRange) {
                    n = e.value.from 
                    max = e.value.to 
                }
                else 
                    n = e.value as Integer
            }
            catch(NumberFormatException f) {
                throw new PipelineError("The value for resource $e.key ($e.value) couldn't be parsed as a number")
            }
            
            if(name == "GB") {
                name = "memory"
                n = n * 1024
            }
            
            new ResourceUnit(key: name, amount: n, maxAmount: max)
        }
        resources(res, block)
    }
    
    /**
     * Executes the enclosed block with <i>threadCount</i> concurrency
     * reserved from the global concurrency semaphore. This allows
     * you to declare that a particular block uses more resources and 
     * should have more than n=1 weight in reserving concurrency from the 
     * system.
     * 
     * @param threadCount
     * @param block
     */
    void resources(List<ResourceUnit> newResources, Closure block) {
        
        def oldResources = this.usedResources
        
        try {
            this.usedResources = oldResources.clone()
            
            newResources.each { r ->
                def key = r.key
                if(r.amount<1) 
                    throw new PipelineError("Resource amount $r.amount of type $key < 1 declared in stage $stageName")
                    
                // TODO: extend these checks to memory / other resources
                if(key == "threads" && r.amount > Config.config.maxThreads)
                    throw new PipelineError("Concurrency required to execute stage $stageName is $r.amount, which is greater than the maximum configured for this pipeline ($Config.config.maxThreads). Use the -n flag to allow higher concurrency.")
                    
                if(key == "threads" && this.usedResources.threads.amount != 1)
                    throw new PipelineError("Stage $stageName contains a nested concurrency declaration (prior request = $usedResources.threads.amount, new request = $r.amount).\n\nNesting concurrency requests is not currently supported.")
                    
               this.usedResources.put(key,r) 
               
               if(key == "threads") {
                   this.customThreadResources = true
               }
            }
               
            block()
        }
        finally {
            this.usedResources = oldResources
            this.customThreadResources = false
        }
    }
    
    /**
     * Causes the given closure to execute and for files that appear during the
     * execution, and which match the pattern, to be considered as 
     * outputs.  This makes it easy to define a pipeline stage that has an 
     * unknown number of outputs.  A common example is splitting input files
     * into size-based or line-based chunks:
     * <code>split -b 100kb $input</code>
     * The number of output files is not known, and explicit output file names
     * are not provided to the command, but should be discovered afterward, 
     * based on the input pattern.
     * 
     * @param pattern
     * @deprecated      This functionality is migrated into {@link #produce(Object, Closure)}
     */
    void split(String pattern, Closure c) {
        
        def files = Utils.glob(pattern)
        try {
            if(files && !Utils.findOlder(files, this.@input)) {
                msg "Skipping execution of split because inputs [" + this.@input + "] are newer than ${files.size()} outputs starting with " + Utils.first(files)
                log.info "Split body not executed because inputs " + this.@input + " older than files matching split: $files"
                return
            }
            
            // Execute the body
            log.info "Executing split body with pattern $pattern in stage $stageName"
            c()
        }
        finally {
            
            def normalizedInputs = Utils.box(this.@input).collect { new File(it).absolutePath }
            def result = Utils.glob(pattern).grep {  !normalizedInputs.contains( new File(it).absolutePath) }
            
            log.info "Found outputs for split by scanning pattern $pattern: [$output]" 
            
            if(Utils.box(this.@output)) 
                this.output = this.@output + out
            else
                this.output = result
        }
    }
    
    /**
     * @see #exec(String, boolean, String)
     * @param cmd
     * @param config
     */
    void exec(String cmd, String config) {
        exec(cmd, true, config)
    }
    
    /**
     * @see #exec(String, boolean, String)
     * @param cmd
     */
    void exec(String cmd) {
        exec(cmd, true)
    }
    
    /**
     * Adds user provided documentation to the pipeline stage
     * 
     * @param attributes    can be a string or Map of attributes
     */
    void doc(Object attributes) {
        if(attributes instanceof Map) {
            this.documentation += attributes
        }
        else
        if(attributes instanceof String) {
            this.documentation["desc"] = attributes
        }
    }
    
    /**
     * Provides an implicit "exec" function that pipeline stages can use
     * to run commands.  This variant blocks and waits for the 
     * shell command to exit.  If the command returns a failure exit code
     * (non zero) then an exception is thrown.
     * 
     * @param cmd            the command line to execute, which will be 
     *                       passed to a bash shell for execution
     * @param joinNewLines   whether to concatenate the command into one long string
     * @param config         optional configuration name to use in running the
     *                       command
     * 
     * @see #async(Closure, String)
     */
    void exec(String cmd, boolean joinNewLines, String config=null) {
        
      log.info "Tracking outputs referenced=[$referencedOutputs] inferred=[$inferredOutputs] for command $cmd" 
      
      this.referencedOutputs += inferredOutputs
      
      def commandReferencedOutputs = this.referencedOutputs
      
      // Reset referenced outputs so they can be re-evaluated for next command independently of this one
      this.referencedOutputs = []
      
      Command c = async(cmd, joinNewLines, config)
      
      for(String o in commandReferencedOutputs)
          pathToCommandId[o] = c.id
      
      Map configObject = c.getConfig(this.@input)
      c.outputs = commandReferencedOutputs.collect { String o ->
          
//          assert c.processedConfig != null
          new PipelineFile(o, resolveStorageFor(o, configObject))
      }
      assert c.outputs.every { it instanceof PipelineFile }
     
      c.exitCode = c.executor.waitFor()
      if(c.stopTimeMs <= 0)
          c.stopTimeMs = System.currentTimeMillis()
          
      if(c.exitCode != 0) {
        // Output is still spooling from the process.  By waiting a bit we ensure
        // that we don't interleave the exception trace with the output
        Thread.sleep(200)
        
        if(!this.probeMode)
            this.commandManager.cleanup(c)
            
        log.info "Command $c.id in branch $branch failed with exit code $c.exitCode"
        
        throw new CommandFailedException("Command in stage $stageName failed with exit status = $c.exitCode : \n\n$c.command", this, c)
      }
      
      if(!this.probeMode)
            this.commandManager.cleanup(c)
    }
    
    /**
     * Executes the specified script as R code 
     * @param scr
     */
    void R(Closure c) {
        R(c,null)
    }
    
    void R(Closure c, String config) {
        
        // When probing, just evaluate the string and return
        if(probeMode) {
            String rCode = c()
            return
        }
        
        if(!inputWrapper)
           inputWrapper = new PipelineInput(this.@input, pipelineStages, this.aliases)
           
           
       // On OSX and Linux, R actively attaches to and listens to signals on the
       // whole process group. This means that any SIGINT gets intercepted
       // by R and it halts execution - even the user just using Ctrl-C on the
       // command line to break after launching Bpipe.
       // See http://stackoverflow.com/questions/6803395/child-process-receives-parents-sigint
       // for a little more information. I do not yet have a good answer for OSX
       // but see here: 
       // http://stackoverflow.com/questions/20338162/how-can-i-launch-a-new-process-that-is-not-a-child-of-the-original-process
       String setSid = Utils.isLinux() ? " setsid " : ""
       String rscriptExe = Utils.resolveRscriptExe()
       boolean oldEchoFlag = this.echoWhenNotFound
       try {
            this.echoWhenNotFound = true
            log.info("Entering echo mode on context " + this.hashCode())
            String rTempDir = Utils.createTempDir().absolutePath
            String scr = c()
            exec("""unset TMP; unset TEMP; TEMPDIR="$rTempDir" $setSid $rscriptExe - <<'!'
            $scr
!
""",false, config)
       }
       finally {
           this.echoWhenNotFound = oldEchoFlag
       }
    }
    
    void python(String pythonCommand) {
        String python = resolveExe("python","python")
        exec("""
        $python <<!
        $pythonCommand
        !
        """.stripIndent(), false)    
    }
    
    /**
     * Execute the given sqlite command against the given database
     * <p>
     * The main reason this was implemented is because sqlite 
     * attaches to the parent process group signal so it terminates on
     * control-c unless we use setSid - see below. So it gets annoying to run
     * it in Bpipe without this helper.
     * 
     * @param db
     * @param sqlCommand
     */
    void sqlite(def db, String sqlCommand, String config = null) {
        
        String setSid = Utils.isLinux() ? " setsid " : ""
        String sqliteCommand = resolveExe("sqlite","sqlite3")
        exec("""
        $setSid $sqliteCommand $db <<!
        $sqlCommand
        !
        """.stripIndent(), false, config)    
    }
    
    void groovy(String groovyCommand, String config) {
        groovy([config:config], groovyCommand)
    }
    
    void groovy(String groovyCommand) {
        groovy([:], groovyCommand)
    }
    
    void groovy(Map opts, String groovyCommand) {
        
        String cp = ""
        if(Config.userConfig.containsKey("libs")) {
            def libs = Config.userConfig.libs
            if(libs instanceof List)
                libs = libs.join(':')
            cp = "-cp ${libs}"
        }
        
        String javaOpts = "-noverify " + (opts.javaOpts?:"")
        
        String javaExe = Utils.resolveExe("java", null)
        String SET_JH = ""
        if(javaExe != null) {
           String javaHome = new File(javaExe).absoluteFile.parentFile.parentFile 
           SET_JH="""unset JAVA_HOME; export PATH="$javaHome/bin:\$PATH";"""
        } 
        
        String groovyExe = Utils.resolveExe("groovy","groovy")
        String groovyHome = new File(groovyExe).absoluteFile.parentFile.parentFile 
        
        String cmd = """
        $SET_JH
        unset GROOVY_HOME

        GROOVY_CMD=\$(cat <<XXXX
        $groovyCommand
        XXXX
        )

        JAVA_OPTS='$javaOpts' $groovyExe $cp -e "\$GROOVY_CMD"
        """.stripIndent()
        
        if(opts.config) {
            exec(cmd, false, opts.config)    
        }
        else {
            exec(cmd, false)    
        }
    } 
    
    /**
     * Undocumented feature: run a command and capture its output into a pipeline variable
     * This currently just runs the command directly.
     * 
     * @TODO run it properly through the CommandManager
     * @param cmd
     * @return
     */
    String capture(String cmd) {
      if(probeMode)
          return ""
          
      CommandLog.cmdLog.write(cmd)
      def joined = ""
      cmd.eachLine { joined += " " + it }
      Process p = Runtime.getRuntime().exec((String[])(['bash','-c',"$joined"].toArray()))
      StringWriter outputBuffer = new StringWriter()
      Thread t1 = p.consumeProcessOutputStream(outputBuffer)
      Thread t2 = p.consumeProcessOutputStream(System.err)
      int exitCode = p.waitFor()
      try { t1.join(); } catch(Exception e) {}
      try { t2.join(); } catch(Exception e) {}
      if(exitCode != 0)
          throw new PipelineError("Command $cmd failed with error $exitCode")
      return outputBuffer.toString()
    }
    
    Command async(String cmd, String config) {
        async(cmd, true, config)
    }
    
    static int EXIT_ABORTED = -2i
    
    class CommandThread extends Thread implements ResourceRequestor {
        Command toWaitFor
        PipelineTestAbort abort
        Pipeline pipeline
        void run() {
            Pipeline.currentRuntimePipeline.set(pipeline)
            Concurrency.instance.registerResourceRequestor(this)
            try {
                log.info "CommandThread entering command waitFor (executor=" + toWaitFor.executor.class.name + ")"
                toWaitFor.exitCode = toWaitFor.executor.waitFor()
            }
            catch(PipelineTestAbort e) {
                abort = e 
                toWaitFor.exitCode = EXIT_ABORTED
            } 
            finally {
                log.info "Exiting command thread " + this + ", unregistering resource requestor"
                Concurrency.instance.unregisterResourceRequestor(this)
            }
        }
        
        @Override
        public boolean isBidding() {
            return true;
        }
        
        void writeLog() {
            CommandLog.cmdLog.write(toWaitFor.command)
        }
    }
    
    /**
     * Replaces the default config within the body to the one specified
     */
    void config(String config, Closure c) { 
        String oldConfig = this.defaultConfig
        this.defaultConfig = config
        c()
        this.defaultConfig = oldConfig
    }
    
    String defaultConfig = null
    
    /**
     * Execute the given list of commands simultaneously and wait for the result, 
     * producing a sensible consolidated error message for any of the commands that
     * fail.
     * <p>
     * <i>Note: this command implements the <code>multi</code> command that can
     * be called from inside pipeline stages. See {@link PipelineDelegate#methodMissing(String, Object)}
     * for how that is translated to a call to this function.</i>
     * 
     * @param cmds  List of commands (strings) to execute
     */
    void multiExec(List cmds) {
        
        // When the list is a Map it means the user used the syntax
        // multi foo:"some command", bar:"some other command"
        // which is how to assign a configuration to each command
        if(cmds[0] instanceof Map) {
            cmds = cmds[0].collect { it } 
        }
        
        // Each command will be assumed to use the full value of the currently
        // specified resource usage. This is unintuitive, so we scale them to achieve
        // the expected effect: the total used by all the commands will be equal 
        // to the amount specified
        def oldResources = this.usedResources
        this.usedResources = [:]
        for(def entry in oldResources) {
            this.usedResources[entry.key] = 
                new ResourceUnit(key: entry.value.key, amount:Math.max(Math.floor(entry.value.amount/cmds.size()),1))
        }
        log.info "Scaled resource use to ${usedResources.values()} to execute in multi block"
        
        try {
          List<Command> execCmds = cmds.collect { cmd -> 
              // Could be map entry or direct string
              if(cmd instanceof Map.Entry) {
                  Utils.box(cmd.value).collect { nestedCommand ->
                      async(nestedCommand,true,cmd.key,true) 
                  }
              }
              else {
                  async(cmd,true,null,true) 
              }
           }.flatten()
          
          List<Integer> exitValues = []
          List<CommandThread> threads = execCmds.collect { new CommandThread(toWaitFor:it, pipeline:Pipeline.currentRuntimePipeline.get()) }
          threads*.start()
          
         if(!Runner.opts.t && !probeMode) {
              // Wait for them to have resources allocated
              long waitTimeMs = 0
              while(execCmds.any { !it.resourcesSatisfied }) {
                  Thread.sleep(100)
                  waitTimeMs += 100
                  if(waitTimeMs > 10000 && waitTimeMs % 5000 == 0) {
                      log.info "Waiting for thread allocation in multi block: " + execCmds.count { it.allocated } + " allocated / " + execCmds.size() + " exitCodes = " + execCmds*.exitCode + " probeMode = " + probeMode
                  }
              }
              
              // Now we can log them to the command log, if they did not abort
              threads.each { if(!it.abort) { it.writeLog() } }
          }
              
          while(!probeMode) {
              int stillRunning = threads.count { it.toWaitFor.exitCode == -1 }
              if(stillRunning) {
                  log.info "Waiting for $stillRunning commands in multi block"
              }
              else
                break
                
              Thread.sleep(2000)
          }
          
          List aborts = threads*.abort.grep { it != null }
          if(aborts) {
              throw new PipelineTestAbort("Would execute multiple commands: \n\n" + [ 1..aborts.size(),aborts.collect { it.message.replaceAll("Would execute:","") }].transpose()*.join("\t").join("\n"))
          }
          
          if(probeMode) {
              log.info "Not checking command success because probe mode"
          }
          else {
              List<String> failed = [cmds,threads*.toWaitFor*.exitCode].transpose().grep { it[1] }
              if(failed) {
                  throw new PipelineError("Command(s) failed: \n\n" + failed.collect { "\t" + it[0] + "\n\t(Exit status = ${it[1]})\n"}.join("\n"))
              }
          }
        }
        finally {
          this.usedResources = oldResources
        }
    }
    
    /**
     * Regular expression for identifying string matching a range of integers,
     * eg: 10 - 30
     */
    final static Pattern INT_RANGE_PATTERN = ~/([0-9]*) *- *([0-9]*)$/
     
    /**
     * Asynchronously executes the given command by creating a CommandExecutor
     * and starting the command using it.  The exit code is not checked and
     * the command may still be running on return.  The Job instance 
     * is returned.  Callers can use the
     * {@link CommandExecutor#waitFor()} on the command's CommandExecutor to wait for the Job to finish.
     * @param deferred      If true, the command will not actually be started until
     *                      the waitFor() method is called
     */
    Command async(String cmd, boolean joinNewLines=true, String config = null, boolean deferred=false) {
        
      if(config == null)
          config = this.defaultConfig
          
      def joined = ""
      if(joinNewLines) {
          joined = Utils.joinShellLines(cmd)
      }
      else
          joined = cmd
          
      // note - set the command here, so that it can be used to resolve
      // the right configuration. However we set it again below
      // after we have resolved the right thread / procs value
      Command command = new Command(command:joined, configName:config)
      
      this.inferUsedProcs(command)

      // Inferred outputs are outputs that are picked up through the user's use of 
      // $ouput.<ext> form in their commands. These are intercepted at string evaluation time
      // (prior to the async or exec command entry) and set as inferredOutputs until
      // the command is executed, and then we wipe them out
      List unconvertedOutputs = (this.inferredOutputs + this.referencedOutputs + this.internalOutputs + this.pendingGlobOutputs).unique()
      List<PipelineFile> checkOutputs = convertToPipelineFiles(command, unconvertedOutputs)
      
      // TODO: here we need to resolve these to a storage layer
      
      EventManager.instance.signal(PipelineEvent.COMMAND_CHECK, "Checking command", [ctx: this, command: cmd, joined: joined, outputs: checkOutputs])

      // We expect that the actual inputs will have been resolved by evaluation of the command to be executed 
      // before this method is invoked
      def actualResolvedInputs = 
          convertToPipelineFiles(command, Utils.box(this.@inputWrapper?.resolvedInputs) + internalInputs).collect { aliases[it] }

      log.info "Checking actual resolved inputs $actualResolvedInputs"
      
      List<String> outOfDateOutputs = null
      if(probeMode) {
          log.info "Skip check dependencies due to probe mode"
      }
      else
      if(!checkOutputs) {
          log.info "Skip check dependencies due to no outputs to check"
      }
      else {
          outOfDateOutputs = Dependencies.instance.getOutOfDate(checkOutputs,actualResolvedInputs)
          if(!outOfDateOutputs) {
              return createUpToDateExecutor(command, checkOutputs)
          }
      }
          
      // Reset the inferred outputs - once they are used the user should have to refer to them
      // again to re-invoke them
      this.inferredOutputs = []
      
      command.id = CommandId.newId()
      trackedOutputs[command.id] = command              
      
      if(probeMode) {
          log.info("Skip command start for $command.command due to probe mode")
          command.executor = new ProbeCommandExecutor()
          return command
      } 
      
      // Check the command for versions of tools it uses
      if(!Runner.opts.t) { // Don't execute tool probes if the user is just testing the pipeline
        def toolsDiscovered = ToolDatabase.instance.probe(cmd)
          
        // Add the tools to our documentation
        if(toolsDiscovered)
            this.doc(["tools" : toolsDiscovered])
      }

      command.branch = this.branch
      command.outputs = checkOutputs.unique() // TODO: unique { it.path } ?
      
      assert command.outputs.every { it instanceof PipelineFile }
      
      command.stageId = this.pipelineStages[-1].id
      try {
          command = commandManager.start(stageName, command, config, Utils.box(this.input), 
                                         this.usedResources,
                                         deferred, this.outputLog)
      }
      catch(PipelineTestAbort exPta) {
          exPta.missingOutputs = outOfDateOutputs
          throw exPta
      }
      finally {
          // If deferred then the thread count is not allocated yet, so we can't 
          // write the log line yet
          if(!deferred)
              CommandLog.cmdLog.write(command.command)
      }
          
      // log.info "Command $command.id started with resources " + this.usedResources
          
      List outputFilter = command.executor.ignorableOutputs

      return command
    }
    
    List<PipelineFile> convertToPipelineFiles(Command command, List miscInputs) {
        miscInputs.collect {  f ->
            if(f instanceof PipelineFile)
                return f
            else {
                String path = String.valueOf(f)
                return new PipelineFile(path, resolveStorageFor(path, command.processedConfig))
            }
        }
    }
    
    /**
     * Inspect the given command to figure out the actual procs it will use by combining
     * the configured procs from bpipe.config with any configured resources by the 
     * use(...) statement or other means.
     * 
     * TODO: it is not clear to me this is actually used any more. 
     * 
     * @param command
     */
    void inferUsedProcs(Command command) {
      // Work out how many threads to request
      String actualThreads = this.usedResources['threads'].amount as String  
      
      // If the config itself specifies procs, it should override the auto-thread magic variable
      // which may get given a crazy high number of threads
      def commandCfg = command.getConfig(Utils.box(this.input))
      if(commandCfg.containsKey('procs')) {
          def procs = commandCfg.procs
          int maxProcs = 0
          if(procs instanceof String) {
             // Allow range of integers
             Matcher intRangeMatch = INT_RANGE_PATTERN.matcher(procs)
             if(intRangeMatch) {
                 procs = intRangeMatch[0][1].toInteger()
                 maxProcs = intRangeMatch[0][2].toInteger()
             }
             else {
                 // NOTE: currently SGE is using a procs option like
                 // 'orte 3' for 3 processes. This here is a hack to enable
                 // that not to fail, but we need to think about how to handle that better
                 procs = procs.trim().replaceAll('[^0-9]','').toInteger()
             }
          }
          else
          if(procs instanceof IntRange) {
              maxProcs = procs.to
              procs = procs.from
          }
          log.info "Found procs value $procs (maxProcs = $maxProcs) to override computed threads value of $actualThreads"
          this.usedResources['threads'].amount = procs
          this.usedResources['threads'].maxAmount = maxProcs
      }
    }
    
    /**
     * Create an executor for the command given its outputs appear newer than its 
     * input. The executor may be a probe or the original executor if resume is
     * specified.
     * 
     * @param m
     */
    Command createUpToDateExecutor(Command command, List checkOutputs) {
      // If resuming, the input may be up to date but still in process of being created
      if(Config.config.mode == "resume") {
          if(waitForResumableOutputs(checkOutputs)) {
              return new Command(executor:new ProbeCommandExecutor())
          }
      }
          
      String message
      if(this.@input)
          message = "Skipping command " + Utils.truncnl(command.command, 30).trim() + " due to inferred outputs $checkOutputs newer than inputs ${this.@input}"
      else
          message = "Skipping command " + Utils.truncnl(command.command, 30).trim() + " because outputs $checkOutputs already exist (no inputs referenced)"
          
      log.info message
      msg message
          
      return new Command(command: command.command, executor:new ProbeCommandExecutor())
    }
    
    /**
     * Check if any of the given outputs are currently being created and if so,
     * waits for their process to finish and throws an error if it fails.
     * 
     */
    boolean waitForResumableOutputs(List boxedOutputs) {
        List<String> boxedOutputPaths = boxedOutputs*.toString()
        List<Command> commandsForMyOutputs = Runner.runningCommands.grep { cmd -> cmd.outputs?.any { it in boxedOutputPaths } }
        if(!commandsForMyOutputs.isEmpty()) {
            log.info "Resume will wait for commands ${commandsForMyOutputs*.id} that produce files overlapping with outputs $boxedOutputPaths"
                
            for(Command waitCommand in commandsForMyOutputs) {
                msg "Resuming command ${waitCommand.id} in stage $stageName to create ${boxedOutputPaths.join(',')} ..."
                outputLog.flush()
                
                CommandStatus waitCommandStatus = waitCommand.executor.status()
                if(waitCommandStatus == CommandStatus.RUNNING) {
                    // NOTE: race condition - command could finish right here - what to do?
                    waitCommand.exitCode = waitCommand.executor.waitFor()
                    this.commandManager.cleanup(waitCommand.id)
                    if(waitCommand.exitCode != 0) {
                        throw new PipelineError("Resumed command $waitCommand.id failed with error $waitCommand.exitCode:\n\n$waitCommand.command")
                    }
                }
                else {
                    log.info "Command $waitCommand.id in state $waitCommandStatus != RUNNING at time of wait: assume completed."
                }
            }
            return true
        }
        else {
            log.info "No running commands overlap outputs for stage $stageName"
            return false
        }
    }
    
    /**
     * Write a message to the output of the current stage
     */
    void msg(def m) {
        if(probeMode)
            return
        def date = (new Date()).format("HH:mm:ss")
        if(branch)
            this.outputLog.buffer "$date MSG [$branch]:  $m"
        else
            this.outputLog.buffer "$date MSG:  $m"
    }
   
   /**
    * Executes the given body with 'input' defined to be the
    * most recently produced output file(s) matching the
    * extensions specified by input
    * <p>
    * TODO: this performs essentially the same function as 
    * {@link PipelineInput#propertyMissing(String)}, it would 
    * be nice to merge them together and get rid of this one.
    *
    * @param c
    * @param inputs
    * @param body
    * @return
    */
   Object fromImpl(Object exts, Closure body) {
       
       log.info "From clause searching for inputs matching spec $exts"
       
       if(!exts || exts.every { it == null })
           throw new PipelineError("A call to 'from' was invoked with an empty or null argument. From requires a list of file patterns to match.")
       
       def orig = exts
       
       // Find all the pipeline stages outputs that were created
       // in the same thread
       def reverseOutputs 
       synchronized(pipelineStages) {
           reverseOutputs = pipelineStages.reverse().grep { 
                  isRelatedContext(it.context) && !it.context.is(this)
           }.collect { Utils.box(it.context.@output) }
           
           // Add a final stage that represents the original inputs (bit of a hack)
           // You can think of it as the initial inputs being the output of some previous stage
           // that we know nothing about
           reverseOutputs.add(Utils.box(pipelineStages[0].context.@input))
       }
       
       // Add an initial stage that represents the current input to this stage.  This way
       // if the from() spec is used and matches the actual inputs then it will go with those
       // rather than searching backwards for a previous match
       reverseOutputs.add(0,Utils.box(this.@input))
       
       int outputCount = reverseOutputs*.size().sum()
       if(outputCount<20) {
           log.info "Input list to check:  $reverseOutputs"
       }
       else {
           log.info "Input list to check has $outputCount entries (" + reverseOutputs[0].size() + ") in tier 1"
       }
       
       assert reverseOutputs.every { outs -> outs.every { it instanceof PipelineFile } }
       
       exts = Utils.box(exts).collect { (it instanceof PipelineOutput || it instanceof PipelineInput) ? it.toString() : it }
       
       Map extTotals = exts.countBy { it }
       
       // Counts of how many times each extension has been referenced
       Map<String,Integer> counts = exts.inject([:]) { r,ext -> r[ext]=0; r }
       def resolvedInputs = exts.collect { String ext ->
           
           String normExt = ext
           def matcher
           boolean globMatch = normExt.indexOf('*')>=0
           if(!globMatch) {
             ext.startsWith(".") ? ext : "." + ext
             matcher = { log.info("Check $it ends with $normExt");  it?.path?.endsWith(normExt) }
           }
           else {
             final Pattern m = FastUtils.globToRegex(normExt)
             log.info "Converted glob pattern $normExt to regex ${m.pattern()}"
             matcher = { PipelineFile fileToMatch ->
//                 log.info "Match $fileName to ${m.pattern()}"
                 fileToMatch ? m.matcher(fileToMatch.path).matches() : false
             }
           }
           
           int previousReferences = counts[ext]
           counts[ext]++
           
           // Count of how many of this kind of extension have been consumed
           int count = 0
           for(s in reverseOutputs) {
               def outputsFound = s.grep { matcher(it) }
               
               log.info "Matched : $outputsFound"
               
               if(outputsFound) {
                   
                   if(globMatch) {
                       return outputsFound
                   }
                   else
                   if(previousReferences - count < outputsFound.size()) {
                     log.info("Checking ${s} vs $normExt Y")
//                     int start = previousReferences - count
//                     int end =   outputsFound.size() - (previousReferences - count)
//                     if(previousReferences >= extTotals[ext]-1)
//                       return outputsFound[start..end]
//                     else
                       return outputsFound[previousReferences - count]
                   }
                   else
                       count+=outputsFound.size()
               }
//               log.info("Checking outputs ${s} vs $inp N")
           }
          
       }
       
       log.info "Found inputs $resolvedInputs for spec $orig"
       
       List missingInputs = resolvedInputs.findIndexValues { it == null }
       if(missingInputs) {
           if(exts.size()>1)
               throw new PipelineError("Stage $stageName unable to locate one or more inputs specified by 'from' ending with $orig\nMost likely missing extensions: ${exts[missingInputs]}")
           else
               throw new PipelineError("Stage $stageName unable to locate one or more inputs specified by 'from' ending with $orig")
       }
           
       // resolvedInputs = Utils.unbox(resolvedInputs)
       resolvedInputs = resolvedInputs.flatten().unique()
       
       def oldInputs = this.@input
       this.@input  = resolvedInputs
       
       this.getInput().resolvedInputs.addAll(resolvedInputs)
      
       def result = withInputs(resolvedInputs,body)
       if(body != null)
           return this.nextInputs
       else
           return this // Allows chaining of the next command
   }
   
   /**
    * Executes the given closure with the inputs replaced with the specified
    * inputs, and then restores them afterwards. If the closure passed is null,
    * sets the 
    * 
    * @param newInputs
    * @param body
    * @return
    */
   def withInputs(List<PipelineFile> newInputs, Closure body) {
       
       def oldInputs = this.@input
       this.@input  = newInputs
       
       this.getInput().resolvedInputs.addAll(newInputs)
       
       def resetInputs = {
           allResolvedInputs.addAll(this.getInput().resolvedInputs)
           this.@input  = oldInputs
           this.@inputWrapper = null 
       }
       
       if(body != null) {
         def result = body()
         resetInputs()
         return result
       }
       else {
         this.inputResets << resetInputs
         return null
       }
   }
 
   /**
    * Implementation of "magic" forward method. See {@link PipelineDelegate#methodMissing} for
    * where this gets called.
    * 
    * @param values
    */
    void forwardImpl(List values) {
        
        
       StorageLayer storage = this.@input.find { !(it instanceof UnknownStoragePipelineFile) }?.storage
        
       // Note: filtering out null because some of the tests do fowrard(null) - 
       // but not sure if that should actually be valid?
       this.nextInputs = values.flatten().grep { it != null }.collect { inp ->
           
           // TODO - CLOUD - what would forwarding an output do?
           if(inp instanceof MultiPipelineInput) {
               return inp.resolvedValues
           }
           else
           if(inp instanceof PipelineInput) {
               return inp.resolvedValue
           }
           else
           if((inp instanceof String) && (storage != null)) {
               return new PipelineFile(inp,storage)
           }
           else
           if((inp instanceof String) && (new File(inp).exists())) {
               return new LocalPipelineFile(inp)
           }
           else {
               assert false : "Forward of type " + inp?.class?.name + " is not supported"
           }
       }.flatten()
       
       assert this.@nextInputs.every { it instanceof PipelineFile }
       
       log.info("Forwarding ${nextInputs.size()} inputs ${nextInputs}")
   }
    
   @CompileStatic
   Aliaser alias(PipelineInput value) {
       PipelineFile inputFile = value.resolvedValue
       return new Aliaser(this.aliases, inputFile)
   }
   
   /**
    * The current stage is always the most recent stage to have executed
    * @return
    */
   PipelineStage getCurrentStage() {
       return this.pipelineStages[-1]
   }
   
    /**
     * First delete and then initialize with blank contents the list of 
     * unclean files
     */
    void initUncleanFilePath() {
        if(!UNCLEAN_FILE_PATH.exists()) 
            UNCLEAN_FILE_PATH.mkdirs()
            
        this.uncleanFilePath = new File(UNCLEAN_FILE_PATH, String.valueOf(Thread.currentThread().id))   
        if(uncleanFilePath.exists())
            this.uncleanFilePath.delete()
        this.uncleanFilePath.text = ""
    }
    
    /**
     * Cache of related contexts so that we do not compute them too often
     */
    Set<PipelineContext> relatedContexts = new HashSet<PipelineContext>()
    
    boolean isRelatedContext(PipelineContext ctx) {
        
        if(ctx.threadId == threadId)
            return true
            
        if(ctx.threadId == Pipeline.rootThreadId)
            return true
            
        if(ctx in relatedContexts)
            return true
            
        // NOTE: THIS SHOULD BE A RECURSIVE UPWARDS SEARCH
        // TODO: FIX THIS
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
        def parentContexts = pipeline.parent.stages*.context
        if(ctx in parentContexts) {
            relatedContexts.add(ctx)
            return true
        }
            
        return false
    }
    
    /**
     * A convenience to allow the user to reference the number of threads for a command
     * simply by typing "$threads" in their command. 
     * @return
     */
    String getThreads() {
        
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
        
        // When the user has specified custom resources
        if(!this.customThreadResources) {
            
            int childCount = 1
            while(pipeline.parent) {
                if(pipeline.parent.childCount) {
                    childCount *= pipeline.parent.childCount
                }
                pipeline = pipeline.parent
            }
            
            log.info "Computing threads based on parallel branch count of $childCount"
            
            // When the user said how many threads to use, just go with it
            // But otherwise, resolve it to the number of cores that the computer has
            int maxThreads = Config.config.customThreads?(int)Config.config.maxThreads : Runtime.getRuntime().availableProcessors()
            if(maxThreads > Config.config.maxThreads) {
                log.info "Using only $Config.config.maxThreads instead of configured $maxThreads because the configured value exceeds the number of available cores"
                maxThreads = Config.config.maxThreads
            }
            
            try {
                  // Old logic: divide by numer of branches - overly conservative
                  // this.usedResources['threads'].amount = Math.max((int)1, (int)maxThreads / childCount)
                  // The actual value is not resolved now until resources are negotiated inside Concurrency.allocateResources
                
                // Note the value 1 here is interpreted as "default" or "unset by the user" by 
                // the ThrottledDelegatingCommandExecutor
                this.usedResources['threads'].amount = 1
            }
            catch(Exception e) {
                e.printStackTrace()
            }
        }
        return THREAD_LAZY_VALUE
    }
    
    @CompileStatic
    String getMemory() {
        return MEMORY_LAZY_VALUE
    }
    
    /**
     * 
     * @return
     */
    String getAuto() {
        '__bpipe_auto_value__'
    }
    
    /**
     * An entire list of resolved inputs including those directly resolved 
     * and those inferred by input variable file extensions.
     */
    List<PipelineFile> getResolvedInputs() {
        List wrapperResolved = this.@inputWrapper?.resolvedInputs?:[]
        
       return (wrapperResolved + this.allResolvedInputs).flatten().unique { it.path }
    }
    
    /**
     * @return true if one or more of the commands in the current
     *         {@link #trackedOutputs} is inconsistent with those
     *         stored in the file system
     */
    boolean checkForModifiedCommands() {
        boolean modified = false
        trackedOutputs.each { String cmd, Command command ->
            
            List<String> outputs = command.outputs
            if(modified)
                return
                
            for(def o in outputs) {
                o = Utils.first(o)
                if(!o)
                    continue
                    
                // We do get outputs logged that weren't actually used,
                // so in this phase we ignore files that don't 
                // actually exist
                if(!new File(o).exists()) 
                    continue
                    
                File file = getOutputMetaData(o)
                if(!file.exists()) {
                    log.info "No metadata for file $o found"
                    continue
                }
                
                Properties p = new Properties()
                file.withInputStream { ifs ->
                    p.load(ifs)
                }
                
                String hash = Utils.sha1(cmd+"_"+o)
                if(p.fingerprint != hash) {
                    log.info "File $o was generated by command with different fingerprint [$p.fingerprint] to the one that produced current output [$hash]. Stage will run even though output files are newer than inputs."
                    modified = true
                    return
                }
                else
                    log.info "Fingerprint for file $o matches: up to date"
            }
        }
        
        return modified
    }
    
    /**
     * Change the default output directory for this pipeline stage.
     * Will rebase any outputs declared by transform / filter, etc
     * to the new output directory.
     * 
     * @param directoryName
     */
    void outputTo(String directoryName) {
        this.outputDirectory = directoryName
        if(this.@output)
            this.setRawOutput(toOutputFolder(this.@output))
            
        this.setDefaultOutput(this.@defaultOutput)
        
        OutputDirectoryWatcher.getDirectoryWatcher(directoryName)
        
        log.info "Output set to $directoryName (new default output = ${this.@defaultOutput})"
    }
    
    PipelineDelegate myDelegate = null
    
    /**
     * In order for "magic" methods to get resolved when invoked directly on the 
     * context (as opposed to referenced in an unqualified way inside a pipeline body
     * closure) we need to forward calls to missing methods explicitly through to the
     * delegate.
     */
    Object methodMissing(String name, args) {
        if(myDelegate)
            return myDelegate.methodMissing(name,args)
        else {
            try {
              msg ="An unknown function '$name' was invoked (arguments = ${args.grep {!it.class.name.startsWith('script') }}).\n\nPlease check your script to ensure this function is correct."
              log.severe(msg + ":\n" + Thread.currentThread().stackTrace*.toString().collect { '\t'+it}.join('\n'))
              throw new PipelineError(msg)
            }
            catch(Exception e) {
              def msg = "An unknown function '$name' was invoked.\n\nPlease check your script to ensure this function is correct."
              log.severe(msg + ":\n" + Thread.currentThread().stackTrace*.toString().collect { '\t'+it}.join('\n'))
              throw new PipelineError(msg)
            }
        }    
    }
    
    /**
     * Cause the pipeline to explicitly fail with a given message
     * @param message
     */
    void fail(String message) {
        if(currentCheck) {
            currentCheck.message = message
        }
        throw new PipelineError("Pipeline stage ${stageName} aborted with the following message:\n\n$message\n")
    }
    
    Sender fail(Sender s) {
        s.onSend = { details ->
            throw new PipelineError("Pipeline stage ${stageName} aborted with the following message:\n\n$details.subject\n")
        }
        return s
    }    
    
    /**
     * Cause the current branch to terminate and not process any further, but 
     * without causing a pipeline failure
     * 
     * @param message   message or reason for success (displayed to end user)
     */
    void succeed(String message) {
        if(currentCheck) {
            currentCheck.message = message
        }
        throw new UserTerminateBranchException(message)
    }
    
    def currentBuilder = null
    
    Check currentCheck = null
    
    Sender succeed(Sender s) {
        s.onSend = { details ->
            throw new UserTerminateBranchException(details.subject)
        }
        return s
    }
    
    Sender html(Closure c) {
        new Sender(this).html(c)
    }
    
    Sender text(Closure c) {
        new Sender(this).text(c)
    }
    
    Sender json(Object obj) {
        this.json({ obj })
    }
    
    Sender json(Closure c) {
        new Sender(this).json(c)
    }
    
    Sender jms(Closure c) {
        new Sender(this).json(c)
    }
    
    Sender report(String reportName) {
        new Sender(this).report(reportName)
    }
    
    Sender send(Sender s) {
        return s
    }
    
    Checker check(String name, Closure c) {
        Checker checker = new Checker(this,name, c)
        this.checkers << checker
        return checker
    }
    
    Checker check(Closure c) {
        Checker checker = new Checker(this,c)
        this.checkers << checker
        return checker
    }
    
    String output(String value) {
        
        // If the user did not specify a directory then 
        // redirect to whatever the current output folder is
        if(new File(value).parentFile == null) 
            value = toOutputFolder(value)[0]
        
        // setOutput(value)
        this.onNewOutputReferenced(null, value)
        List newOutputs = this.@output ? output + value : [value]
        this.setRawOutput(newOutputs)
        return value
    }
    
    /**
     * Cleanup outputs matching the specified patterns from the current 
     * file output hierarchy (ie: resolvable within current branch).
     * <p>
     * The list of patterns can contain Strings (or any object coerceable to a string)
     * or it can contain regular expression Pattern objects (eg, created using ~"....")
     * or a mixture of both. The Strings will be treated as file extensions, while the 
     * patterns will be treated as end-matching patterns on the outputs visible to this 
     * pipeline stage.
     * 
     * @param patterns
     */
    void cleanup(java.lang.Object... patterns) {
        
        // This is used as a way to resolve inputs correctly in the same way
        // that we would look for inputs (following branch semantics)
        PipelineInput inp = getInput()
        
        List<String> exts = patterns.grep { !(it instanceof Pattern) }.collect { val ->
            val.toString()
        }
        
        List results = []
        if(exts) {
            results = inp.resolveInputsEndingWith(exts)
            log.info "Resolved upstream outputs matching $exts for cleanup : $results"
        }
        
        List<String> pats = patterns.grep { (it instanceof Pattern) }.collect { val ->
            val.toString()
        }
        
        if(pats) {
            List patResults = inp.resolveInputsEndingWithPatterns(pats, pats)
            log.info "Resolved upstream outputs matching $pats for cleanup : $results"
            results.addAll(patResults)
        }
        
        log.info "Removing outputs that were aliased from cleanup targets: aliased = $aliases"
        
        results = results.grep { !aliases.isAliased(it) }
        
        // Finally, cleanup all these files
        log.info "Attempting to cleanup files: $results"
        if(results) {
            
            // Are there actually existing files matching the patterns? If not, we can avoid the 
//            // expensive process of scanning the output folder for them
//            int countMatches = OutputDirectoryWatcher.countGlobalGlobMatches(patterns.grep { it instanceof String }) +
//                               OutputDirectoryWatcher.countGlobalPatternMatches(patterns.grep { it instanceof Pattern })
//                
//            if(countMatches == 0) {
//                log.info "Skipping cleanup process because no files observed to match patters: $patterns"
//                return
//            }
//            else {
//                log.info "Estimate of files matching cleanup patterns is $countMatches"
//            }
//                               
            List resultFiles = results.collect { new File(it) }
            
//            List<OutputMetaData> props = Dependencies.instance.scanOutputFolder()
            
            List<File> cleaned = []
            for(File result in resultFiles) {
                
                // Note we protect attempt to resolve canonical path with 
                // the file name equality because it is very slow!
//                OutputMetaData resultProps = props.find { (it.outputFile.name == result.name) && (GraphEntry.canonicalPathFor(it) == result.canonicalPath) }
                
                GraphEntry graph = Dependencies.instance.outputGraph
                Lock lock = Dependencies.instance.outputGraphLock.readLock()
                OutputMetaData resultProps 
                
                lock.lock()
                try {
                    GraphEntry resultEntry = graph.entryFor(result)
                    if(!resultEntry) {
                        log.warning("Unable to find output graph entry for output file $result. Will not clean up this file.")
                        continue
                    }
                    
                   resultProps = resultEntry.values.find { it.outputPath = result.path }
                }
                finally {
                    lock.unlock()
                }
                
                if(resultProps == null) {
                    log.warning "Unable to file matching known output $result.name"
                    System.err.println("WARNING: unable to cleanup output $result because meta data file could not be resolved")
                }
                else
                if(resultProps.cleaned) {
                    log.info "File $result already cleaned"
                }
                else {
                    Dependencies.instance.removeOutputFile(resultProps)
                    cleaned.add(result)
                }
            }
            
            if(cleaned) {
                println "MSG: The following files were cleaned: " + cleaned.join(",")
            }
        }
    }
    
    String memory(Object memoryValue) {
        int memoryAmountMB = Integer.parseInt(String.valueOf(memoryValue)) * 1000
        this.usedResources["memory"] = 
            new ResourceUnit(key:"memory", amount: memoryAmountMB, maxAmount: memoryAmountMB)
        return String.valueOf(memoryValue)
    }
    
    void setOutputDirectory(String outputDirectory) {
        assert outputDirectory != null
        this.@outputDirectory = outputDirectory
    }
    
    void debug() {
//        groovy.ui.Console console = new groovy.ui.Console();
        console.setVariable("pipeline", bpipe.Pipeline.currentRuntimePipeline.get());
        console.setVariable("context", this)
        console.run()
        while(console.frame.visible) {
            Thread.sleep(1000);
        }
    }
}


