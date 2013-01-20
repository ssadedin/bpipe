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
    Map<String,ResourceUnit> usedResources = [ "threads":new ResourceUnit(key: "threads", amount:1)] 
   
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
    * The default output is set prior to the body of the a pipeline stage being run.
    * If the pipeline stage does nothing else but references $output then the default output is
    * the one that is returned.  However the pipeline stage may modify the output
    * by use of the transform, filter or produce constructs.  If so, the actual output
    * is stored in the output property.
    */
   private def defaultOutput
   
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
   
   def allResolvedInputs = []
   
   /**
    * The default output property reference.  Actually returns a quasi
    * String-like object that intercepts property references
    */
   def getOutput() {
       String baseOutput = Utils.first(this.getDefaultOutput()) 
       def out = this.@output
       if(out == null) { // Output not set elsewhere
           
           // If an input property was referenced, compute the default from that instead
           if(inputWrapper?.resolvedInputs) {
               def resolved = Utils.unbox(inputWrapper.resolvedInputs[0])
               log.info("Using non-default output due to input property reference: " + resolved)
               out = resolved +"." + this.stageName
               
               // Since we're resolving based on a different input than the default one,
               // the pipeline output wrapper should use a different one as a default too
               baseOutput = toOutputFolder(out)
           }
           else
               out = this.getDefaultOutput()
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
                                 { o -> onNewOutputReferenced(pipeline, o)}) 
       
       po.branchName = branchName
       return po
   }
   
   /**
    * Called by the embedded {@link PipelineOutput} object
    * that wraps the $output variable whenever a new output
    * is referenced in a pipeline.
    * 
    * @param pipeline
    */
   void onNewOutputReferenced(Pipeline pipeline, Object o) {
       if(!allInferredOutputs.contains(o)) 
           allInferredOutputs << o; 
       if(!inferredOutputs.contains(o)) 
           inferredOutputs << o;  
       if(applyName) { 
           pipeline.nameApplied=true
        } 
   }
   
   def getOutputs() {
       return Utils.box(getOutput())
   }
   
   def getOutputByIndex(int index) {
       log.info "Query for output $index"
       def o = getOutput()
       def result =Utils.box(o.output)[index]
       if(result == null) {
           result = Utils.box(o.output)[0].replaceAll("\\.([^.]*)\$",".${index+1}.\$1")
       }
       result = trackOutput(result)
       
       return result
   }
   
   def getOutput1() {
       return getOutputByIndex(0)
   }
   
   def getOutput2() {
       return getOutputByIndex(1)
   }
   
   def getOutput3() {
       return getOutputByIndex(2)
   }
   
   def getOutput4() {
       return getOutputByIndex(3)
   }
   
   def getOutput5() {
       return getOutputByIndex(4)
   }
    
   def getOutput6() {
       return getOutputByIndex(5)
   }
   
   def getOutput7() {
       return getOutputByIndex(6)
   }
    
   def getOutput8() {
       return getOutputByIndex(7)
   }
   
   def getOutput9() {
       return getOutputByIndex(8)
   }
     
   private trackOutput(def output) {
       log.info "Tracking output $output"
       referencedOutputs << output
       return output
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
    * Wrapper that intercepts calls to resolve input properties
    */
   PipelineInput inputWrapper
   
    /**
     * The inputs to be passed to the next stage of the pipeline.
     * Usually this is the same as context.output but it doesn't have
     * to be.
     */
   def nextInputs
   
   String getInputByIndex(int i) {
       if(!Utils.isContainer(input) || input.size()<i)
           throw new PipelineError("Expected $i or more inputs but fewer provided")
       this.allResolvedInputs << input[i-1]
       return input[i-1]
   }
   
   String getInput1() { return Utils.first(input) }
   String getInput2() { return getInputByIndex(2) }
   String getInput3() { return getInputByIndex(3) }
   String getInput4() { return getInputByIndex(4) }
   String getInput5() { return getInputByIndex(5) }
   String getInput6() { return getInputByIndex(6) }
   String getInput7() { return getInputByIndex(7) }
   String getInput8() { return getInputByIndex(8) }
   String getInput9() { return getInputByIndex(9) }
   String getInput10() { return getInputByIndex(10) }
   
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
       if(!inputWrapper) {
           inputWrapper = new PipelineInput(this.@input, pipelineStages)
       }
           
       return inputWrapper
   }
   
   def setInput(def inp) {
       this.@input = inp
   }
   
   def getInputs() {
       return new MultiPipelineInput(this.@input, pipelineStages)
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
       
       if(!this.trackedOutputs["filterRows"])
         this.trackedOutputs["filterRows"] = []
         
       this.trackedOutputs["filterRows"] += fileName 
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
       
       if(!this.trackedOutputs["<streamed>"])
         this.trackedOutputs["<streamed>"] = []
         
       this.trackedOutputs["<streamed>"] += fileName
       
       return outFile
   }
   
   private boolean applyName = false
   
   /**
    * Specifies an output that converts an input file to a different kind of file,
    * ("transforms" it). Convenience wrapper for {@link #produce(Closure, Object, Closure)}
    * for the case where the file extension is replaced with a new one.
    */
   Object transform(List<String> exts, Closure body) {
       
       def extensionCounts = [:]
       for(def e in exts) {
           extensionCounts[e] = 0
       }
       
//       def inp = Utils.first(this.@input)
       if(!Utils.first(this.@input)) 
           throw new PipelineError("Expected input but no input provided") 
       
       def pipeline = Pipeline.currentRuntimePipeline.get()
       
       def boxed = Utils.box(this.@input)
       
       def files = exts.collect { String extension ->
           def inp = boxed[extensionCounts[extension] % boxed.size()]
           extensionCounts[extension]++
           if(applyName)
               return inp.replaceAll('\\.[^\\.]*$','.'+pipeline.name+'.'+extension)
           else
               return inp.replaceAll('\\.[^\\.]*$','.'+extension)
       }
       
       log.info "Transform using $exts produces outputs $files"
       
       if(applyName)
           pipeline.nameApplied = true
           
       produce(files, body)
   }
 
    
    /**
     * Specifies an output that keeps the same type of file but modifies 
     * it ("filters" it). Convenience wrapper for {@link #produce(Closure, Object, Closure)}
     * for the case where the same file extension is kept but a transformation
     * type is added to the name.
     */
    Object filter(List<String> types, Closure body) {
        
        def pipeline = Pipeline.currentRuntimePipeline.get()
        def inp = Utils.first(this.@input)
        
        log.info "Filtering based on input $inp"
        
        if(!inp) 
           throw new PipelineError("Expected input but no input provided") 
        
        def files = types.collect { String type ->
            String oldExt = (inp =~ '\\.[^\\.]*$')[0]
            if(applyName) 
                return inp.replaceAll('\\.[^\\.]*$','.'+pipeline.name+'.'+type+oldExt)
            else
                return inp.replaceAll('(\\.[^\\.]*$)','.'+type+oldExt)
        }
        
        log.info "Filtering using $types produces outputs $files"
        
        if(applyName)
            pipeline.nameApplied = true
            
        produce(files, body)
    }
  
    Object filter(String type, Closure body) {
        filter([type],body)
    }
    
    Object filter(String type1, String type2, Closure body) { 
        filter([type1,type2],body)
    }
    
    Object filter(String type1, String type2, String type3, Closure body) { 
        filter([type1,type2,type3],body)
    }
    
    Object filter(String type1, String type2, String type3, String type4, Closure body) { 
        filter([type1,type2,type3,type4],body)
    }
    
    Object filter(String type1, String type2, String type3, String type4, String type5, Closure body) { 
        filter([type1,type2,type3,type4,type5],body)
    }
    
    Object transform(String ext, Closure body) {
        transform([ext],body)
    }
    
    Object transform(String ext1, String ext2, Closure body) { 
        transform([ext1,ext2],body)
    }
    
    Object transform(String ext1, String ext2, String ext3, Closure body) { 
        transform([ext1,ext2,ext3],body)
    }
    
    Object transform(String ext1, String ext2, String ext3, String ext4, Closure body) { 
        transform([ext1,ext2,ext3,ext4],body)
    }
    
    Object transform(String ext1, String ext2, String ext3, String ext4, String ext5, Closure body) { 
        transform([ext1,ext2,ext3,ext4,ext5],body)
    }
    
    Object produce(String out1, String out2, Closure body) { 
        produce([out1,out2],body)
    }
    
    Object produce(String out1, String out2, String out3, Closure body) { 
        produce([out1,out2,out3],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, Closure body) { 
        produce([out1,out2,out3,out4],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, String out5, Closure body) { 
        produce([out1,out2,out3,out4,out5],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, String out5, String out6, Closure body) { 
        produce([out1,out2,out3,out4,out5,out6],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, String out5, String out6, String out7, Closure body) { 
        produce([out1,out2,out3,out4,out5,out6,out7],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, String out5, String out6, String out7, String out8, Closure body) { 
        produce([out1,out2,out3,out4,out5,out6,out7,out8],body)
    }
    
    Object produce(String out1, String out2, String out3, String out4, String out5, String out6, String out7, String out8, String out9, Closure body) { 
        produce([out1,out2,out3,out4,out5,out6,out7,out8,out9],body)
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
     * 
     * @TODO above issue can possibly be mitigated if we do an 
     * 		 upwards hierachical search to check outputs from prior
     * 		 stages
     * @TODO the case where an output directory is set is not yet
     *       properly handled in the glob matching
     */
    Object produce(Object out, Closure body) { 
        log.info "Producing $out from $this"
        
		List globOutputs = Utils.box(out).grep { it.contains("*") }
        
        // Unwrap any wrapped inputs that may have been passed in the outputs
        // and coerce them to the correct output folder
        out = toOutputFolder(Utils.unwrap(out))
        
        def lastInputs = this.@input
        boolean doExecute = true
        
		List fixedOutputs = Utils.box(out).grep { !it.contains("*") }
		
		// Check for all existing files that match the globs
		List globExistingFiles = globOutputs.collect { Utils.glob(it) }.flatten()
        if((!globOutputs || globExistingFiles) && Dependencies.instance.checkUpToDate(fixedOutputs + globExistingFiles,lastInputs)) {
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
            
            if(!Config.config.enableCommandTracking || !checkForModifiedCommands()) {
                msg("Skipping steps to create ${Utils.box(out).unique()} because newer than $lastInputs ")
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
                this.output = Utils.box(fixedOutputs) + Utils.box(this.@output)
            }
            else
                this.output = fixedOutputs
            
            // Store the list of output files so that if we are killed 
            // they can be cleaned up
            this.uncleanFilePath.text += Utils.box(this.output)?.join("\n") 
            
            PipelineDelegate.setDelegateOn(this, body)
            log.info("Producing from inputs ${this.@input}")
            body()
        }
        
        if(this.@output) {
            log.info "Adding outputs " + this.@output + " as a result of produce"
            Utils.box(this.@output).each { o ->
                
                // If no inputs were resolved, we assume generically that all the inputs
                // to the stage were used. This is necessary to deal with
                // filterLines which doesn't trigger inferred inputs because
                // it does not execute at all
                if(!allResolvedInputs && !this.inputWrapper?.resolvedInputs) {
                    allResolvedInputs.addAll(Utils.box(this.@input))
                }
  
                // It's possible the user used produce() but did not actually reference
                // the output variable anywhere in the body. In that case, we
                // don't know which command used the output variable so we add an "anonymous" 
                // output
               
                trackOutputIfNotAlreadyTracked(o, "<produce>")
            }
        }
		
		if(globOutputs) {
			def normalizedInputs = Utils.box(this.@input).collect { new File(it).absolutePath }
			for(String pattern in globOutputs) {
				def result = Utils.glob(pattern).grep {  !normalizedInputs.contains( new File(it).absolutePath) }
				
				log.info "Found outputs for glob $pattern: [$result]"
                
                result.each { trackOutputIfNotAlreadyTracked(it, "<produce>") }
				
				if(Utils.box(this.@output))
					this.output = this.@output + result
				else
					this.output = result
			}
		}

        return out
    }
    
    void trackOutputIfNotAlreadyTracked(String o, String command) {
        if(!(o in this.referencedOutputs) && !(o in this.inferredOutputs) && !(o in this.allInferredOutputs)) {
           if(!this.trackedOutputs[command])
             this.trackedOutputs[command] = [o]
           else
             this.trackedOutputs[command] << o
        } 
    }
    
    /**
     * Cause output files created by the given closure, and which also match the 
     * given pattern to be preserved.
     * @param pattern
     */
    void preserve(String pattern, Closure c) {
        def oldFiles = trackedOutputs.values().flatten().unique()
        c()
        List<String> matchingOutputs = Utils.glob(pattern) - oldFiles
        for(def entry in trackedOutputs) {
            def preserved = entry.value.grep { matchingOutputs.contains(it) }
            log.info "Outputs $preserved marked as preserved from stage $stageName by pattern $pattern"
            this.preservedOutputs += preserved
        }
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
            }
                
            block()
        }
        finally {
            this.usedResources = oldResources
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
     * @deprecated		This functionality is migrated into {@link #produce(Object, Closure)}
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
      
      this.trackedOutputs[cmd] = this.referencedOutputs
        
     
      this.referencedOutputs = []
      
      CommandExecutor p = async(cmd, joinNewLines, config)
      int exitResult = p.waitFor()
      if(exitResult != 0) {
        // Output is still spooling from the process.  By waiting a bit we ensure
        // that we don't interleave the exception trace with the output
        Thread.sleep(200)
        
        if(!this.probeMode)
            this.commandManager.cleanup(p)
            
        throw new PipelineError("Command failed with exit status = $exitResult : \n$cmd")
      }
      
      if(!this.probeMode)
            this.commandManager.cleanup(p)
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
            String scr = c()
            exec("""Rscript - <<'!'
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
    
    CommandExecutor async(String cmd, String config) {
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
          List<CommandExecutor> execs = cmds.collect { async(it,true,null,true) }
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
         
          List<String> failed = [cmds,exitValues].transpose().grep { it[1] }
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
     * {@link CommandExecutor#waitFor()} to wait for the Job to finish.
     * @param deferred      If true, the command will not actually be started until
     *                      the waitFor() method is called
     */
    CommandExecutor async(String cmd, boolean joinNewLines=true, String config = null, boolean deferred=false) {
      def joined = ""
      if(joinNewLines) {
          def prev
          cmd.eachLine { 
              if(!it.trim().isEmpty() || joined.isEmpty()) { 
                  joined += " " + it
              }
              else {
                  if(!joined.trim().endsWith(";") && !joined.trim().endsWith("&"))
                      joined += ";"
                      
                  joined += " "
              }
          }
      }
      else
          joined = cmd
          
      // Inferred outputs are outputs that are picked up through the user's use of 
      // $ouput.<ext> form in their commands. These are intercepted at string evaluation time
      // (prior to the async or exec command entry) and set as inferredOutputs until
      // the command is executed, and then we wipe them out
      if(!probeMode && this.inferredOutputs && Dependencies.instance.checkUpToDate(this.inferredOutputs,this.@input)) {
          String message = "Skipping command " + Utils.truncnl(joined, 30).trim() + " due to inferred outputs $inferredOutputs newer than inputs ${this.@input}"
          log.info message
          msg message
          
          // Reset the inferred outputs - once they are used the user should have to refer to them
          // again to re-invoke them
          return new ProbeCommandExecutor()
      }
          
      this.inferredOutputs = []
      
      if(!probeMode) {
          CommandLog.cmdLog.write(cmd)
          
          // Check the command for versions of tools it uses
          def toolsDiscovered = ToolDatabase.instance.probe(cmd)
          
          // Add the tools to our documentation
          if(toolsDiscovered)
              this.doc(["tools" : toolsDiscovered])
      
          CommandExecutor cmdExec = 
              commandManager.start(stageName, joined, config, Utils.box(this.input), 
                                   new File(outputDirectory), this.usedResources, deferred)
              
          List outputFilter = cmdExec.ignorableOutputs
          if(outputFilter) {
              this.outputMask.addAll(outputFilter)
          }
          return cmdExec
      }
      else {
          return new ProbeCommandExecutor()
      }
    }
    
    
    /**
     * Write a message to the output of the current stage
     */
    void msg(def m) {
        def date = (new Date()).format("HH:mm:ss")
		if(branch)
	        println "$date MSG [$branch]:  $m"
		else
	        println "$date MSG:  $m"
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
       
       def resolvedInputs = Utils.box(exts).collect { String ext ->
           
           String normExt = ext.startsWith(".") ? ext : "." + ext
           
           for(s in reverseOutputs) {
               def o = s.grep { it?.endsWith(normExt) }.collect { it.toString() }
               if(o) {
                   log.info("Checking ${s} vs $normExt  Y")
                   return o
               }
//               log.info("Checking outputs ${s} vs $inp N")
           }
           
           // Not found - if the file exists as a path in its own right use that
           if(new File(ext).exists())
               return ext
       }
       
       if(resolvedInputs.any { it == null})
           throw new PipelineError("Unable to locate one or more inputs specified by 'from' ending with $orig")
           
       // resolvedInputs = Utils.unbox(resolvedInputs)
       resolvedInputs = resolvedInputs.flatten()
       
       log.info "Found inputs $resolvedInputs for spec $orig"
       
       def oldInputs = this.@input
       this.@input  = resolvedInputs
       
       this.getInput().resolvedInputs.addAll(resolvedInputs)
       
       this.nextInputs = body()
       
       allResolvedInputs.addAll(this.getInput().resolvedInputs)
       
       this.@input  = oldInputs
       this.@inputWrapper = null
       return this.nextInputs
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
    
    boolean isRelatedContext(PipelineContext ctx) {
        
        if(ctx.threadId == threadId)
            return true
            
        // This is a hack: we allow inputs from the root pipeline, 
        // which ensures child threads can see inputs that cascaded down
        // from the parent "root".  But this would not work in a multi-level
        // pipeline - threads launching threads.  
        return ctx.threadId == Pipeline.rootThreadId
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
     * @return true if one or more of the commands in the current
     *         {@link #trackedOutputs} is inconsistent with those
     *         stored in the file system
     */
    boolean checkForModifiedCommands() {
        boolean modified = false
        trackedOutputs.each { String cmd, List<String> outputs ->
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
}

