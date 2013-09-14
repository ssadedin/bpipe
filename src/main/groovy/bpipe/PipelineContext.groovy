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

import groovy.util.logging.Log;
import java.util.regex.Pattern;

import static Utils.*
import bpipe.executor.CommandExecutor
import bpipe.executor.ProbeCommandExecutor

/**
* This context defines implicit functions and variables that are
* made available to Bpipe pipeline stages.   These functions are
* only available inside the context of a Bpipe stage, when it is
* executed by Bpipe (ie. they are introduced at runtime).
* <p>
* Note: currently other functions are also made available by the
* PipelineCategory, however I hope to migrate these eventually
* to all use this context.
*/
@Log
class PipelineContext {
    
    /**
     * File where half processed files will be listed on shutdown
     */
    public static File UNCLEAN_FILE_PATH = new File(".bpipe/inprogress")
    
    /**
     * Directory where metadata for pipeline stage outputs will be written
     */
    public final static String OUTPUT_METADATA_DIR = ".bpipe/outputs/"
    
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
    public PipelineContext(Binding extraBinding, List<PipelineStage> pipelineStages, List<Closure> pipelineJoiners, String branch) {
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
        if(pipeline)
            this.applyName = pipeline.name && !pipeline.nameApplied
         
        this.outputLog = new OutputLog(branch)
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
    String stageName
    
    /**
     * The directory to which the pipeline stage should write its outputs
     */
    String outputDirectory = "."
    
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
   
    /**
     * File patterns that will be excluded as inferred output files because they may be created 
     * frequently as side effects that are not intended to be outputs
     */
    Set<String> outputMask = ['\\.bai$', '\\.log$'] as Set

    File uncleanFilePath
   
    /**
     * Documentation attributes for the the pipeline stage
     * Mostly this is a map of String => String, but 
     * tool information is stored as {@link Tool} objects
     */
    Map<String, Object> documentation = [:]
   
    private List<PipelineStage> pipelineStages
   
    private List<Closure> pipelineJoiners
    
    /**
     * Manager for commands executed by this context
     */
    CommandManager commandManager = new CommandManager()
      
   /**
    * All outputs from this stage, mapped by command 
    * that created them
    */
   Map<String,List<String> > trackedOutputs = [:]
   
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
   String branch = ""
   
   /**
    * A list of executable closures to be executed when the next produce statement completes
    */
   List<Closure> fromCleanups = []
   
   /**
    * The default output is set prior to the body of the a pipeline stage being run.
    * If the pipeline stage does nothing else but references $output then the default output is
    * the one that is returned.  However the pipeline stage may modify the output
    * by use of the transform, filter or produce constructs.  If so, the actual output
    * is stored in the output property.
    */
   private def defaultOutput
   
   /**
    * Log that will write output from messages and commands executed
    * by this context / stage
    */
   private OutputLog outputLog
   
   def getDefaultOutput() {
//       log.info "Returning default output " + this.@defaultOutput
       return this.@defaultOutput
   }
   
   def setDefaultOutput(defOut) {
       this.@defaultOutput = toOutputFolder(defOut)
   }
   
   def output
   
   void setOutput(o) {
       setRawOutput(toOutputFolder(o))
   }
   
   void setRawOutput(o) {
       log.info "Setting output $o on context ${this.hashCode()} in thread ${Thread.currentThread().id}"
       if(Thread.currentThread().id != threadId)
           log.warning "Thread output being set to $o from wrong thread ${Thread.currentThread().id} instead of $threadId"
       
       this.@output = o
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
   def inferredOutputs = []
   
   /**
    * All outputs referenced through output property extensions during the 
    * execution of the pipeline stage
    */
   def allInferredOutputs = []
   
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
   def allResolvedInputs = []
   
   /**
    * The default output property reference.  Actually returns a quasi
    * String-like object that intercepts property references
    */
   def getOutput() {
       String baseOutput = Utils.first(this.getDefaultOutput()) 
       def out = this.@output
       if(out == null || this.currentFileNameTransform) { // Output not set elsewhere, or set dynamically based on inputs
           
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
                   defaultValueIndex = allResolved.findIndexOf { it in branchInputs }
               if(defaultValueIndex<0)
                   defaultValueIndex = 0
                   
               def resolved = Utils.unbox(allResolved[defaultValueIndex])
               
               log.info("Using non-default output due to input property reference: " + resolved + " from resolved inputs " + allResolved)
               
               if(this.currentFileNameTransform != null) {
                   out = this.currentFileNameTransform.transform(Utils.box(allResolved), this.applyName)
               }
               else
                   out = resolved +"." + this.stageName
               
               // Since we're resolving based on a different input than the default one,
               // the pipeline output wrapper should use a different one as a default too
               baseOutput = toOutputFolder(out)
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
              return null
                         
       out = toOutputFolder(out)
       
       def pipeline = Pipeline.currentRuntimePipeline.get()
       String branchName = applyName  ? pipeline.name : null
       
       def po = new PipelineOutput(out,
                                    this.stageName, 
                                 baseOutput,
                                 Utils.box(this.@output), 
                                 { o,replaced -> onNewOutputReferenced(pipeline, o, replaced)}) 
       
       po.branchName = branchName
       po.currentFilter = currentFilter
       po.outputDirChangeListener = { outputTo(it) }
       return po
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
       if(!allInferredOutputs.contains(o)) 
           allInferredOutputs << o; 
       if(!inferredOutputs.contains(o)) 
           inferredOutputs << o;  
       if(applyName) { 
           pipeline.nameApplied=true
        } 
       if(replaced) 
           this.@output = Utils.box(this.@output).collect { if(it == replaced) { replacedOutputs.add(it) ; return o } else { return it } }
   }
   
   def getOutputs() {
       return Utils.box(getOutput())
   }
   
   def getOutputByIndex(int index) {
       try {
           PipelineOutput origOutput = getOutput()
           def o = Utils.box(origOutput.output)
           def result = o[index]
           if(result == null) {
               log.info "No previously set output at $index from ${o.size()} outputs. Synthesizing from index based on first output"
               if(o[0].indexOf('.')>=0) 
                   result = o[0].replaceAll("\\.([^.]*)\$",".${index+1}.\$1")
               else
                   result = o[0] + (index+1)
           }
           
           log.info "Query for output $index base result = $result"
           
           // result = trackOutput(result)
           
           Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
           
           def overrideOutputs = (origOutput.overrideOutputs && origOutput.overrideOutputs.size() >= index ? [ origOutput.overrideOutputs[index] ] : [] )
           
           return new PipelineOutput(result, 
                                     origOutput.stageName, 
                                     origOutput.defaultOutput, 
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
           if(!this.localVariables.containsKey(k) && !this.extraBinding.variables.containsKey(k) && !Runner.binding.variables.containsKey(k)) {
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
           if(!this.localVariables.containsKey(k) && !this.extraBinding.variables.containsKey(k) && !Runner.binding.variables.containsKey(k)) {
               throw new PipelineError(
               """
                       Pipeline stage ${this.stageName} requires a parameter $k but this parameter was not specified

                       You can specify it by adding 'using' to your pipeline. For example:

                               ${this.stageName}.using($k:<value>)

                       The parameter $k is described as follows:

                               $v
               """.stripIndent())
           }
       }
   }
    
    /**
    * Coerce all of the arguments (which may be an array of Strings or a single String) to
    * point to files in the local directory.
    */
   def toOutputFolder(outputs) {
       File outputFolder = new File(this.outputDirectory)
       if(!outputFolder.exists())
           outputFolder.mkdirs()
           
       String outPrefix = this.outputDirectory == "." ? "" : this.outputDirectory + "/" 
       def newOutputs = Utils.box(outputs).collect { outPrefix + new File(it.toString()).name }
       return Utils.unbox(newOutputs)
   }
   
   /**
    * Input to a stage - can be either a single value or a list of values
    */
   def input
   
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
   def branchInputs
   
    /**
     * The inputs to be passed to the next stage of the pipeline.
     * Usually this is the same as context.output but it doesn't have
     * to be.
     */
   def nextInputs
   
   /**
    * Return the value of the specified input<n> where n is the 
    * parameter supplied (index of input to resolve).
    * 
    * @param i
    * @return
    */
   PipelineInput getInputByIndex(int i) {
       
       def boxed = Utils.box(input)
       if(boxed.size()<i)
           throw new PipelineError("Expected $i or more inputs but fewer provided")
           
       this.allResolvedInputs << input[i]
       
       PipelineInput wrapper = new PipelineInput(this.@input, pipelineStages)
       wrapper.currentFilter = currentFilter
       wrapper.defaultValueIndex = i
       
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
           throw new PipelineError("Input expected but not provided")
       }
       if(!inputWrapper || inputWrapper instanceof MultiPipelineInput) {
           inputWrapper = new PipelineInput(this.@input, pipelineStages)
           this.allUsedInputWrappers[0] = inputWrapper
       }
       inputWrapper.currentFilter = currentFilter    
       return inputWrapper
   }
   
   def setInput(def inp) {
       this.@input = inp
   }
   
   def getInputs() {
       if(!inputWrapper || !(inputWrapper instanceof MultiPipelineInput)) {
           this.inputWrapper = new MultiPipelineInput(this.@input, pipelineStages)
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
       
       if(probeMode)
           return
       
       if(!input)
           throw new PipelineError("Attempt to grep on input but no input available")
           
       if(Runner.opts.t)
           throw new PipelineTestAbort("Would execute filterLines on input $input")
       
       String usedInput = Utils.first(input)    
       
       // Do this just to create the file - otherwise it doesn't get created at all for
       // an empty input file
       getOut()
       
       new File(usedInput).eachLine {  line ->
           if(line.startsWith("#") || c(line))
                  getOut() << line << "\n"
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
       
       
       Command command = new Command(id:CommandId.newId(), command: "filterRows")
       this.trackedOutputs[command.id] = command
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
   
   List<String> glob(String... patterns) {
       def result = []
       for(def p in patterns) {
           result.addAll(Utils.glob(p))
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
       
       String fileName = Utils.first(getOutput())
       if(!outFile) {
         if(Runner.opts.t)
             throw new PipelineTestAbort("Would write to output file $fileName")
          
         outFile = new FileOutputStream(fileName)
       }
       
       Command cmd = new Command(id:CommandId.newId(), command: "<streamed>", outputs:[fileName])
       this.trackedOutputs[cmd.id] = cmd
       
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
            
        this.currentFilter = (boxed + Utils.box(this.pipelineStages[-1].originalInputs)).grep { it.indexOf('.')>0 }.collect { it.substring(it.lastIndexOf('.')+1) }
        
        this.currentFileNameTransform = new FilterFileNameTransformer(types: types)
        
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
        
        log.info "Producing $out from $this"
        
        // Unwrap any files that may be wrapped in PipelineInput or PipelineOutput objects
        out = Utils.unwrap(out)      
        
        List globOutputs = Utils.box(out).grep { it.contains("*") }
        
        // Coerce so that files go to the right output folder
        out = toOutputFolder(out)
        
        def lastInputs = this.@input
        boolean doExecute = true
        
        List fixedOutputs = Utils.box(out).grep { !it.contains("*") }
        
        // Check for all existing files that match the globs
        List globExistingFiles = globOutputs.collect { Utils.glob(it) }.flatten()
        if((!globOutputs || globExistingFiles)) {

          // No inputs were newer than outputs, 
          // but were the commands that created the outputs modified?
          this.output = fixedOutputs
          this.probeMode = true
          this.trackedOutputs = [:]
          try {
            PipelineDelegate.setDelegateOn(this, body)
            log.info("Probing command using inputs ${this.@input}")
            body() 
            log.info "Finished probe"
            
            def allInputs = (getResolvedInputs()  + Utils.box(lastInputs)).unique()
            if(!Dependencies.instance.checkUpToDate(fixedOutputs + globExistingFiles,allInputs)) {
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
          finally {
              this.probeMode = false
          }
        }
        
        if(doExecute) {
            if(Utils.box(this.@output)) {
                this.output = Utils.box(fixedOutputs) +  Utils.box(this.@output)
                this.output.removeAll(replacedOutputs)
            }
            else {
                this.output = fixedOutputs
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
            Utils.box(this.@output).each { o ->
                
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
            def normalizedInputs = Utils.box(this.@input).collect { new File(it).absolutePath }
            for(String pattern in globOutputs) {
                def result = Utils.glob(pattern).grep {  !normalizedInputs.contains( new File(it).absolutePath) }
                
                log.info "Found outputs for glob $pattern: [$result]"
                
                String commandId = "-1"
                
                result.each { 
                    if(commandId == "-1")
                        commandId = CommandId.newId(); 
                    trackOutputIfNotAlreadyTracked(it, "<produce>", commandId) 
                }
                
                if(Utils.box(this.@output))
                    this.output = this.@output + result
                else
                    this.output = result
            }
        }
        
        this.currentFileNameTransform = null

        this.fromCleanups.each { it() }
        this.fromCleanups = []
        
        return out
    }
    
    void trackOutputIfNotAlreadyTracked(String o, String command, String commandId) {
        if(!(o in this.referencedOutputs) && !(o in this.inferredOutputs) && !(o in this.allInferredOutputs)) {
             if(!this.trackedOutputs[commandId]) {
                 this.trackedOutputs[commandId] = new Command(id: commandId, outputs: [o], command: command)
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
    void preserve(String pattern, Closure c) {
        def oldFiles = getAllTrackedOutputs()
        c()
        List<String> matchingOutputs = Utils.glob(pattern) - oldFiles
        for(def entry in trackedOutputs) {
            def preserved = entry.value.outputs.grep { matchingOutputs.contains(it) }
            log.info "Outputs $preserved marked as preserved from stage $stageName by pattern $pattern"
            this.preservedOutputs += preserved
        }
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
     * .bai file that exists only to be an index for it's .bam file.
     */
    void accompanies(String pattern, Closure c) {
        def oldFiles = getAllTrackedOutputs()
        c()
        List<String> newOutputs = getAllTrackedOutputs() - oldFiles
        log.info "Found accompanying outputs : ${newOutputs}"
        if(!pattern.contains("*")) {
            pattern = "*."+pattern;
        }
        final Pattern m = FastUtils.globToRegex(pattern)
        def accompanied = getResolvedInputs().grep { m.matcher(it).matches() }
        if(accompanied) {
            for(def accompanyingOutput in newOutputs) {
                log.info "Inputs $accompanied are accompanied by $accompanied (only first will be used)"
                this.accompanyingOutputs[accompanyingOutput] = accompanied[0]
            }        
        }
        else
            log.warning "No accompanied inputs found for outputs $newOutputs using pattern $pattern from inputs ${getResolvedInputs()}"
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
            int n
            try {
                if(e.value instanceof String)
                    n = Integer.parseInt(e.value)
                else
                    n = e.value
            }
            catch(NumberFormatException f) {
                throw new PipelineError("The value for resource $e.key ($e.value) couldn't be parsed as a number")
            }
            
            if(name == "GB") {
                return new ResourceUnit(amount: (n as Integer) * 1024)
            }
            else {
                return new ResourceUnit(amount: n as Integer)
            }
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
                    
                // TODO: extend tyhese checks to memory / other resources
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
      
      c.outputs = commandReferencedOutputs
     
      int exitResult = c.executor.waitFor()
      if(exitResult != 0) {
        // Output is still spooling from the process.  By waiting a bit we ensure
        // that we don't interleave the exception trace with the output
        Thread.sleep(200)
        
        if(!this.probeMode)
            this.commandManager.cleanup(c.executor)
            
        throw new PipelineError("Command failed with exit status = $exitResult : \n$cmd")
      }
      
      if(!this.probeMode)
            this.commandManager.cleanup(c.executor)
    }
    
    
    /**
     * Due to bugs in R with concurrent R sessions being launched simultaneously
     * we actually block using this object
     */
    static Object rLock = new Object()
    
    static long lastRSessionMs = -1L
    
    static final long RSESSION_SEPARATION_MS = 2000
    
    /**
     * Executes the specified script as R code 
     * @param scr
     */
    void R(Closure c) {
        log.info("Running some R code")
        
        // When probing, just evaluate the string and return
        if(probeMode) {
            String rCode = c()
            return
        }
        
        if(!inputWrapper)
           inputWrapper = new PipelineInput(this.@input, pipelineStages)

       synchronized(rLock) {
           long now = System.currentTimeMillis()
           if(now - lastRSessionMs < RSESSION_SEPARATION_MS) {
               log.info "Waiting $RSESSION_SEPARATION_MS due to prior R session started in conflict with this one"
               Thread.sleep(RSESSION_SEPARATION_MS)
           }
           lastRSessionMs = System.currentTimeMillis()
       }
       
       boolean oldEchoFlag = this.echoWhenNotFound
       try {
            this.echoWhenNotFound = true
            log.info("Entering echo mode on context " + this.hashCode())
            String rTempDir = Utils.createTempDir().absolutePath
            String scr = c()
            exec("""unset TMP; unset TEMP; TEMPDIR="$rTempDir" Rscript - <<'!'
            $scr
!""",false)
       }
       finally {
           this.echoWhenNotFound = oldEchoFlag
       }
    }
    
    String capture(String cmd) {
      if(probeMode)
          return ""
          
      CommandLog.cmdLog.write(cmd)
      def joined = ""
      cmd.eachLine { joined += " " + it }
      
      Process p = Runtime.getRuntime().exec((String[])(['bash','-c',"$joined"].toArray()))
      StringWriter outputBuffer = new StringWriter()
      p.consumeProcessOutput(outputBuffer,System.err)
      p.waitFor()
      return outputBuffer.toString()
    }
    
    Command async(String cmd, String config) {
        async(cmd, true, config)
    }
    
    class CommandThread extends Thread {
        int exitStatus = -1
        CommandExecutor toWaitFor
        void run() {
            exitStatus = toWaitFor.waitFor()
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
          def aborts = []
          List<CommandExecutor> execs = cmds.collect { 
              try {
                async(it,true,null,true).executor 
              }
              catch(PipelineTestAbort e) {
                 aborts << e 
              }
          }
          
          if(aborts) {
              throw new PipelineTestAbort("Would execute multiple commands: \n\n" + [ 1..aborts.size(),aborts.collect { it.message.replaceAll("Would execute:","") }].transpose()*.join("\t").join("\n"))
          }
          
          List<Integer> exitValues = []
          List<CommandThread> threads = execs.collect { new CommandThread(toWaitFor:it) }
          threads*.start()
          
          while(true) {
              int stillRunning = threads.count { it.exitStatus == -1 }
              if(stillRunning) {
                  log.info "Waiting for $stillRunning commands in multi block"
              }
              else
                break
              Thread.sleep(2000)
          }
         
          List<String> failed = [cmds,threads*.exitStatus].transpose().grep { it[1] }
          if(failed) {
              throw new PipelineError("Command(s) failed: \n\n" + failed.collect { "\t" + it[0] + "\n\t(Exit status = ${it[1]})\n"}.join("\n"))
          }
        }
        finally {
          this.usedResources = oldResources
        }
    }
     
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
        
      // Replacement of magic $thread variable with real value 
      cmd = cmd.replaceAll(THREAD_LAZY_VALUE, this.usedResources['threads'].amount as String)
      
      if(config == null)
          config = this.defaultConfig
          
      def joined = ""
      if(joinNewLines) {
          joined = Utils.joinShellLines(cmd)
      }
      else
          joined = cmd
          
      // Inferred outputs are outputs that are picked up through the user's use of 
      // $ouput.<ext> form in their commands. These are intercepted at string evaluation time
      // (prior to the async or exec command entry) and set as inferredOutputs until
      // the command is executed, and then we wipe them out
      def checkOutputs = this.inferredOutputs + referencedOutputs
      EventManager.instance.signal(PipelineEvent.COMMAND_CHECK, "Checking command", [ctx: this, command: cmd, joined: joined, outputs: checkOutputs])

      // We expect that the actual inputs will have been resolved by evaluation of the command to be executed 
      // before this method is invoked
      def actualResolvedInputs = Utils.box(this.@inputWrapper?.resolvedInputs)

      log.info "Checking actual resolved inputs $actualResolvedInputs"
      if(!probeMode && checkOutputs && Dependencies.instance.checkUpToDate(checkOutputs,actualResolvedInputs)) {
          String message = "Skipping command " + Utils.truncnl(joined, 30).trim() + " due to inferred outputs $checkOutputs newer than inputs ${this.@input}"
          log.info message
          msg message
          
          return new Command(executor:new ProbeCommandExecutor())
      }
          
      // Reset the inferred outputs - once they are used the user should have to refer to them
      // again to re-invoke them
      this.inferredOutputs = []
      
      if(!probeMode) {
          CommandLog.cmdLog.write(cmd)
          
          // Check the command for versions of tools it uses
          def toolsDiscovered = ToolDatabase.instance.probe(cmd)
          
          // Add the tools to our documentation
          if(toolsDiscovered)
              this.doc(["tools" : toolsDiscovered])
       
          Command command = new Command(command:joined)
          command.executor = 
              commandManager.start(stageName, command, config, Utils.box(this.input), 
                                   new File(outputDirectory), this.usedResources,
                                   deferred, this.outputLog)
          trackedOutputs[command.id] = command              
          List outputFilter = command.executor.ignorableOutputs
          if(outputFilter) {
              this.outputMask.addAll(outputFilter)
          }
          return command
      }
      else {
          return new Command(executor:new ProbeCommandExecutor())
      }
    }
    
    
    /**
     * Write a message to the output of the current stage
     */
    void msg(def m) {
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
       
       log.info "Searching for inputs matching spec $exts"
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
       
       log.info "Input list to check:  $reverseOutputs"
       
       exts = Utils.box(exts)
       
       Map extTotals = exts.countBy { it }
       
       // Counts of how many times each extension has been referenced
       Map<String,Integer> counts = exts.inject([:]) { r,ext -> r[ext]=0; r }
       def resolvedInputs = exts.collect { String ext ->
           
           String normExt = ext
           def matcher
           boolean globMatch = normExt.indexOf('*')>=0
           if(!globMatch) {
             ext.startsWith(".") ? ext : "." + ext
             matcher = { log.info("Check $it ends with $normExt");  it?.endsWith(normExt) }
           }
           else {
             final Pattern m = FastUtils.globToRegex(normExt)
             log.info "Converted glob pattern $normExt to regex ${m.pattern()}"
             matcher = { fileName ->
//                 log.info "Match $fileName to ${m.pattern()}"
                 fileName ? m.matcher(fileName).matches() : false
             }
           }
           
           int previousReferences = counts[ext]
           counts[ext]++
           
           // Count of how many of this kind of extension have been consumed
           int count = 0
           for(s in reverseOutputs) {
               def outputsFound = s.grep { matcher(it) }.collect { it.toString() }
               
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
          
           // Not found - if the file exists as a path in its own right use that
           if(new File(ext).exists())
               return ext
       }
       
       log.info "Found inputs $resolvedInputs for spec $orig"
       
       if(resolvedInputs.any { it == null})
           throw new PipelineError("Stage $stageName unable to locate one or more inputs specified by 'from' ending with $orig")
           
       // resolvedInputs = Utils.unbox(resolvedInputs)
       resolvedInputs = resolvedInputs.flatten().unique()
       
       def oldInputs = this.@input
       this.@input  = resolvedInputs
       
       this.getInput().resolvedInputs.addAll(resolvedInputs)
       
       def fromCleanup = {
           allResolvedInputs.addAll(this.getInput().resolvedInputs)
           this.@input  = oldInputs
           this.@inputWrapper = null 
       }
       
       if(body != null) {
         this.nextInputs = body()
         fromCleanup()
         return this.nextInputs
       }
       else {
         this.fromCleanups << fromCleanup
         return this 
       }
   }
 
   public void forward(nextInputOverride) {
       this.nextInputs = nextInputOverride
       if(this.nextInputs instanceof PipelineInput)
               this.nextInputs = this.nextInputs.@input
   }
   
   /**
    * The current stage is always the most recent stage to have executed
    * @return
    */
   PipelineStage getCurrentStage() {
       return this.stages[-1]
   }
   
    /**
     * First delete and then initialize with blank contents the list of 
     * unclean files
     */
    void initUncleanFilePath() {
        if(!UNCLEAN_FILE_PATH.exists()) 
            UNCLEAN_FILE_PATH.mkdirs()
            
        this.uncleanFilePath = new File(UNCLEAN_FILE_PATH, String.valueOf(Thread.currentThread().id))   
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
     * Return a {@link File} that indicates the path where
     * metadata for the specified output & file should be stored.
     * 
     * @param outputFile The name of the output file
     */
    File getOutputMetaData(String outputFile) {
        File outputsDir = new File(OUTPUT_METADATA_DIR)
        if(!outputsDir.exists()) 
            outputsDir.mkdirs()
        
        return  new File(outputsDir,this.stageName + "." + new File(outputFile).path.replaceAll("[/\\\\]", "_") + ".properties")
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
            
            int childCount = pipeline.parent?.childCount?:1
            
            log.info "Computing threads based on parallel branch count of $childCount"
            
            // When the user said how many threads to use, just go with it
            // But otherwise, resolve it to the number of cores that the computer has
            int maxThreads = Config.config.customThreads?(int)Config.config.maxThreads : Runtime.getRuntime().availableProcessors()
            if(maxThreads > Config.config.maxThreads) {
                log.info "Using only $Config.config.maxThreads instead of configured $maxThreads because the configured value exceeds the number of available cores"
                maxThreads = Config.config.maxThreads
            }
            
            try {
                this.usedResources['threads'].amount = Math.max((int)1, (int)maxThreads / childCount)
            }
            catch(Exception e) {
                e.printStackTrace()
            }
        }
        return THREAD_LAZY_VALUE
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
    List<String> getResolvedInputs() {
       ((this.@inputWrapper?.resolvedInputs?:[]) + this.allResolvedInputs).flatten().unique() 
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
    
    void outputTo(String directoryName) {
        this.outputDirectory = directoryName
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
              throw new PipelineError("An unknown function '$name' was invoked (arguments = ${args.grep {!it.class.name.startsWith('script') }}).\n\nPlease check your script to ensure this function is correct.")
            }
            catch(Exception e) {
              throw new PipelineError("An unknown function '$name' was invoked.\n\nPlease check your script to ensure this function is correct.")
            }
        }    
    }
}




