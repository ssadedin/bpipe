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

import bpipe.storage.UnknownStoragePipelineFile
import groovy.transform.CompileStatic;
import groovy.util.logging.Log

import java.util.regex.Pattern

/**
 * Represents a "magic" output object that automatically 
 * understands property references as file extensions. 
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class PipelineOutput {
    
    /**
     * Support implicit cast to String when creating File objects
     */
    static {
        File.metaClass.constructor << {  PipelineOutput o -> new File(o.toString()) }
    }
    
    /**
     * Raw outputs
     */
    List<String> output
    
    /**
     * The name of the stage that this output is being created for.
     * Used as part of the naming convention, depending on how the
     * inputs are accessed
     */
    String stageName
    
    /**
     * The name from which output file names created by ".<ext>" extensions will be derived
     */
    String defaultOutput
    
    def outputUsed
    
    Closure outputChangeListener
    
    Closure outputDirChangeListener
    
    List<String> overrideOutputs
    
    /**
     * A list of inputs that were resolved prior to this output being referenced
     * Used when a choice between two possible output references is possible,
     * to use the one best associated with actual resolved input
     */
    List<PipelineFile> resolvedInputs = []
    
    /**
     * If a filter is executing, a list of the file extensions that were available
     * to the filter 
     */
    FilterFileNameTransformer currentFilter = null
    
    /**
     * If the output should add an extra segment to generated output files then that
     * is specified here.
     */
    String branchName = null
    
    /**
     * Whether output file extensions should replace the existing extension from an 
     * input file, or add to the end.
     */
    String transformMode = "replace"
    
    /**
     * The value to return from toString() when this value is predetermined
     * by propertyMissing()
     */
    String stringValue = null
    
    /**
     * If this output is created as part of a chain of outputs then the
     * segments implied by the parents will be in here
     */
    List<String> extraSegments = []
    
    /**
     * If there are inbound branches to be merged, they are listed here
     */
    List<Branch> inboundBranches
    
    /**
     * Parent of this output, used when output extensions are chained together
     */
    PipelineOutput parentOutput = null
    
    boolean multiple = false
    
    /**
     * Create a pipeline output wrapper
     * 
     * @param output            the output to be returned if this object is directly converted to a string
     * @param stageName         the pipeline stage for which this wrapper is going to create derived output names
     * @param defaultOutput     the default pre-computed stage name (used to created derived names)
     * @param overrideOutputs   a set of outputs that, if provided, become a mandatory set from which outputs
     *                          must be selected. If provided, it will be an error to request an output extension
     *                          that is not in this set.
     * 
     * @param listener          a closure that will be called whenever a property is requested from this object 
     *                          and provided with the computed property value
     */
    PipelineOutput(List<String> output, String stageName, String defaultOutput, List<Object> overrideOutputs, List<Branch> inboundBranches, Closure listener) {
        assert output instanceof List
        assert output.isEmpty() || output[0] instanceof String
        this.output = output
        this.outputChangeListener = listener
        this.stageName = stageName
        this.defaultOutput = defaultOutput
        this.inboundBranches = inboundBranches
        this.overrideOutputs = overrideOutputs.collect { 
             String.valueOf(it) 
        }
    }
    
    String toString() {
        
        if(multiple) {
            this.outputUsed = String.valueOf(Utils.first(output))
            List boxed = Utils.box(output).unique()
            for(String o in boxed) {
                
                String replaceOutput = null

                // If $output is referenced without extension, we may have to reset the outputs
                // if the output is based on an alternative input to the default one that was set
                // when the filter() was executed
                if(this.overrideOutputs && this.currentFilter != null && !(o in overrideOutputs)) {
                    if(Utils.ext(o) in currentFilter.exts) {
                        replaceOutput = this.overrideOutputs[0]
                    }
                }

                if(this.outputChangeListener && o != null)
                    this.outputChangeListener(o,replaceOutput)
            }

            return boxed.collect { Utils.cleanPath(it) }.join(" ")
        }
        else {
            
            // Value set by parent, and we have not resolved
            // any different value ourselves
            if(stringValue != null) {
                this.outputChangeListener(stringValue, null)
                return Utils.cleanPath(stringValue)
            }
            
            String firstOutput = Utils.first(output)
            String replaceOutput = null
            
            if(firstOutput == null && overrideOutputs) 
                throw new PipelineError(
                    """
                    A filter, transform or produce was specified, but an output was referenced using 
                    extension ${extraSegments.join(',')} that is not compatible with those outputs.
                    
                    The files available for reference are $overrideOutputs
                    
                    """.stripIndent())
            
            // If $output is referenced without extension, we may have to reset the outputs 
            // if the output is based on an alternative input to the default one that was set
            // when the filter() was executed
            if(this.overrideOutputs && this.currentFilter != null && !(firstOutput in overrideOutputs)) {
                assert firstOutput != null
                if(Utils.ext(firstOutput) in currentFilter.exts) {
                    replaceOutput = this.overrideOutputs[0]
                }
            }
            
            if(this.outputChangeListener && firstOutput != null)
              this.outputChangeListener(firstOutput,replaceOutput)
              
            return Utils.cleanPath(firstOutput)
        }
    }
   
    /**
     * Support accessing outputs by index - allows the user to use the form
     *   exec "cp foo.txt ${output[0]}"
     */
    String getAt(int i) {
        def outs = Utils.box(this.output)
        if(outs.size() <= i)
            throw new PipelineError("Insufficient outputs:  output ${i+1} was referenced but there are only ${outs.size()} outputs to choose from")
        return outs[i]
    }
    
    /**
     * Return the parent directory of the current (default) output.
     * <p>
     * All outputs that have been specified to be produced in the directory
     * are considered referenced when this property is accessed.
     * 
     * @return  canonical absolute path to parent directory of default (first) output
     */
    String getDir() {
        
        // Consider in order: 
        //   - outputs enforced by transform, etc ("override outputs")
        //   - outputs referenced by output file name extensions (output.txt)
        //   - the default output set for this stage
        List boxed = Utils.box(this.overrideOutputs) + Utils.box(this.outputUsed) + [defaultOutput]
        
        String baseOutput = boxed[0]

        // Since a "naked" relative file path ("foo") doesn't
        // have a parent file, we get null for the parent file in that case
        File parentDir = new File(baseOutput).parentFile
        if(parentDir) {

            // We consider ANY output that is generated in the output directory as
            // being referneced by this
            if(this.outputChangeListener) {
                boxed.grep { new File(it).parentFile?.canonicalPath == parentDir.canonicalPath }.each {
                    // Need to exclude default output here. The problem is, the default output 
                    // is just one made up by Bpipe - there's no indication from the user this output
                    // will ever be created. If we treat it as a referenced output here then it leads to
                    // spurious "output missing" errors or attempts to recreate the output because Bpipe
                    // thinks it should exist when it doesn't
                    // see produce_to_dir_no_output_ref test
                    if(it != defaultOutput && it != null)
                        this.outputChangeListener(it,null)
                }
            }
        }
        else {
            parentDir = new File(Config.config.defaultOutputDirectory)
            if(baseOutput) {
                if(this.outputChangeListener && (baseOutput != defaultOutput))
                    this.outputChangeListener(baseOutput,null)
            }
        }
        
        String result = parentDir.absoluteFile.canonicalPath
        if(Utils.isWindows())
            result  = result.replace('\\', '/')

        if(result.startsWith(Runner.canonicalRunDirectory) && (result.size()>Runner.canonicalRunDirectory.size())) {
            result = result.substring(Runner.canonicalRunDirectory.size()+1)
        }

        return result
    }
    
    void setDir(Object value) {
        if(this.outputDirChangeListener && value != null)
            this.outputDirChangeListener(value.toString())
    }
    
    /**
     * Return the first input with the file extension replaced with the 
     * one specified.
     */
    def propertyMissing(String name) {
        
        String result = null
        
        // When "produce", "transform" or "filter" is used, they specify
        // the outputs,  so the output extension acts as a selector from
        // those rather than a synthesis of a new name
        if(this.overrideOutputs) {
           return selectFromOverrides(name)  
        }
        else 
           result = synthesiseFromName(name)
        
        return this.createChildOutput(result, name)
    }
    
    PipelineOutput createChildOutput(String result, String extraSegment) {
        def po = new PipelineOutput(result ? [result] : [],
            this.stageName,
            this.defaultOutput,
            this.overrideOutputs,
            this.inboundBranches,
            this.outputChangeListener)

        po.branchName = this.branchName
        po.currentFilter = this.currentFilter
            
        po.resolvedInputs = this.resolvedInputs
        po.outputDirChangeListener = this.outputDirChangeListener
        po.transformMode = this.transformMode
        po.stringValue = result
        po.extraSegments = this.extraSegments + [extraSegment]
        po.parentOutput = this
        return po
    }
    
    static Pattern FILE_EXT_PATTERN = ~'\\.[^.]*$'
    
    PipelineOutput selectFromOverrides(String name) {
        String result = this.overrideOutputs.find { it.toString().endsWith('.' + name) }
        def replaced = null
        if(!result) {
            if(currentFilter && (name in currentFilter.exts)) {
                log.info "Allowing reference to output not produced by filter because it was available from a filtering of an alternative input"
                
                replaced = this.overrideOutputs[0]
                
                PipelineFile baseInput = this.resolvedInputs.find {it.path.endsWith(name)}
                if(!baseInput) {
                    baseInput = new UnknownStoragePipelineFile(this.overrideOutputs[0])
                    result = baseInput.newName(baseInput.path.replaceAll(FILE_EXT_PATTERN,"." + name))
                }
                else {
                    log.info "Recomputing filter on base input $baseInput to achieve match with output extension $name"
                    result = this.currentFilter.transform([baseInput], this.currentFilter.nameApplied)[0]
                }
                result = Utils.toDir([result], this.getDir())[0]
            }
       }
        
       PipelineOutput childOutput = this.createChildOutput(result, name) 
       
        if(!result) {
            String dottedName = '.' + name + '.'
            
            List<String> filteredResults = overrideOutputs.grep { it.contains(dottedName) }
            
            if(filteredResults.isEmpty()) {
                throw new PipelineError("An output containing or ending with '." + name + "' was referenced.\n\n"+
                                        "However such an output was not in the outputs specified by an enclosing transform / filter / produce statement.\n\n" +
                                        "Valid outputs according to the enclosing block are: ${overrideOutputs.join('\n')}")
            }
            else {
                childOutput.overrideOutputs = filteredResults
            }
        }
        
        log.info "Selected output $result with extension $name from expected outputs $overrideOutputs"
          
        this.outputUsed = result
        if(this.outputChangeListener != null && result != null) {
            this.outputChangeListener(result,replaced)
        }
        
        return childOutput
    }
    
    def synthesiseFromName(String name) {
        
        String firstOutput = Utils.first(this.output)
        
        // If the extension of the output is the same as the extension of the 
        // input then this is more like a filter; remove the previous output extension from the path
        // eg: foo.csv.bar => foo.baz.csv
        List branchSegment = branchName ? ['.' + branchName] : [] 
        
        String segments = (branchSegment + [stageName] + extraSegments + [name]).collect { 
            FastUtils.strip(it,'.')
        }.join(".")
         
        File outputFile = new File(firstOutput)
         
        if(stageName.equals(outputFile.name)) {
           this.outputUsed = FastUtils.dotJoin(([this.defaultOutput] + branchSegment + [name]) as String[])
        }
        else
        if(firstOutput.endsWith(name+"."+stageName)) {
            log.info("Replacing " + name+"\\."+stageName + " with " +  stageName+'.'+name)
            this.outputUsed = this.defaultOutput.replaceAll("[.]{0,1}"+name+"\\."+stageName, '.' + segments)
        }
        else { // more like a transform: keep the old extension in there (foo.csv.bar => foo.csv.bar.xml)
            this.outputUsed = computeOutputUsedAsTransform(name, segments)
        }
        
        if(this.outputUsed.startsWith(".") && !this.outputUsed.startsWith("./")) // occurs when no inputs given to script and output extension used
            this.outputUsed = this.outputUsed.substring(1) 
            
        if(this.inboundBranches) {
            for(Branch branch in this.inboundBranches) {
                log.info "Excise inbound merging branch reference $branch due to merge point"
                this.outputUsed = this.outputUsed.replace('.' + branch.name + '.','.merge.')
            }
        }
        return this.outputUsed
    }
    
    private static Pattern DOT_NUMBER_PATTERN = ~'\\.[^\\.]*(\\.[0-9]*){0,1}$'
    
    /**
     * Compute a name for the output based on the assumption it is 
     * doing something like a 'transform' operation.
     * 
     * ie: the new extension is appended to the output.
     * 
     * @return  the transformed output name
     */
    String computeOutputUsedAsTransform(String extension, String segments) {
        
        // First remove the stage name, if it is at the end
        String result = this.defaultOutput
        if(result.endsWith(stageName) && result.endsWith('.'+stageName))
            result = defaultOutput.substring(0,result.size() - stageName.size()-1)
            
        // If the branch name is now at the end and there is a suffix we can remove the suffix
        // eg: from test.chr1.txt produce test.chr1.hello.csv rather than test.txt.chr1.hello.csv
        // (see param_chr_override test)
        
        result = result.replaceAll(/(\.[^.]*)\./+branchName,'.'+branchName)
            
        // Then replace the extension on the file with the requested one
        if(this.transformMode == "replace") {
            if(result.contains("."))
                // Here we allow a potential match on a number in the base output, since that can 
                // occur when the use uses multiple outputs ($output1.csv, $output2.csv) and 
                // the same output file is generated for the outputs - such file names get 
                // a numeric index inserted. eg: test1.txt, test1.txt.2
                result = result.replaceAll(DOT_NUMBER_PATTERN, '$1.'+segments)
            else
                result = result + "." + segments
        }
        else {
            result = result + "." + extension
        } 
        return result
    }
    
    def methodMissing(String name, args) {
        // faux inheritance from String class
        if(name in String.metaClass.methods*.name)
            return String.metaClass.invokeMethod(this.toString(), name, args)
        else {
            throw new MissingMethodException(name, PipelineOutput, args)
        }
    }
    
    String getPrefix() {
        this.outputUsed = String.valueOf(Utils.first(output))
        if(this.outputChangeListener != null) 
            this.outputChangeListener(this.outputUsed,null)
        return PipelineCategory.getPrefix(this.outputUsed);
    } 
    
   /**
     * Return the outputs with each one prefixed by the specified flag
     * <p>
     * If the flag ends with "=" then no space is included between the flag
     * and the option. Otherwise, a space is included.
     * 
     * @param flag name of flag, including dashes (eg: "-I" or "--input")
     * @return  string containing each matching input prefixed by the flag and a space
     */
    public String withFlag(String flag) {
        
       this.outputUsed = String.valueOf(Utils.first(output))
        
       List boxed = Utils.box(output).unique()
       if(!multiple)
           boxed = [boxed[0]]
       
       for(String o in boxed) {
           if(this.outputChangeListener != null) 
               this.outputChangeListener(o,null)
       }
            
       if(flag.endsWith("=")) {
           return boxed.collect { "${flag}${it}" }.join(" ") 
       }
       else
           return boxed.collect { "$flag $it" }.join(" ")
    } 
    
}
