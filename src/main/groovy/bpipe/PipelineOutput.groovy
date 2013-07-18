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

import groovy.util.logging.Log;

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
     * Raw inputs
     */
    def output
    
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
     * If a filter is executing, a list of the file extensions that were available
     * to the filter 
     */
    List<String> currentFilter = []
    
    /**
     * If the output should add an extra segment to generated output files then that
     * is specified here.
     */
    String branchName = null
    
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
    PipelineOutput(def output, String stageName, String defaultOutput, List<String> overrideOutputs, Closure listener) {
        this.output = output
        this.outputChangeListener = listener
        this.stageName = stageName
        this.defaultOutput = defaultOutput
        this.overrideOutputs = overrideOutputs
    }
    
    String toString() {
        if(this.outputChangeListener)
          this.outputChangeListener(Utils.first(output),null)
        return String.valueOf(Utils.first(output)) 
    }
   
    /**
     * Support accessing outputs by index - allows the user to use the form
     *   exec "cp foo.txt ${output[0]}"
     */
    String getAt(int i) {
        def inputs = Utils.box(this.output)
        if(inputs.size() <= i)
            throw new PipelineError("Insufficient outputs:  output $i was referenced but there are only ${output.size()} outputs to choose from")
        return inputs[i]
    }
    
    /**
     * Return the parent directory of the current (default) output.
     * <p>
     * All outputs that have been specified to be produced in the directory
     * are considered referenced when this property is accessed.
     * 
     * @return  canonical absolute path to parent directory of default (first) output
     */
    Object getDir() {
        List boxed = Utils.box(this.output)
        
        String baseOutput = boxed[0]

        // Since a "naked" relative file path ("foo") doesn't
        // have a parent file, we get null for the parent file in that case
        File parentDir = new File(baseOutput).parentFile
        if(parentDir) {

            // We consider ANY output that is generated in the output directory as
            // being referneced by this
            if(this.outputChangeListener) {
                boxed.grep { new File(it).parentFile?.canonicalPath == parentDir.canonicalPath }.each {
                    this.outputChangeListener(it,null)
                }
            }
        }
        else {
            parentDir = new File(".")
            if(this.outputChangeListener)
                this.outputChangeListener(baseOutput,null)
        }

        return parentDir.absoluteFile.canonicalPath
    }
    
    void setDir(Object value) {
        if(this.outputDirChangeListener)
            this.outputDirChangeListener(value.toString())
    }
    
    /**
     * Return the first input with the file extension replaced with the 
     * one specified.
     */
    def propertyMissing(String name) {
        
        // When "produce", "transform" or "filter" is used, they specify
        // the outputs,  so the output extension acts as a selector from
        // those rather than a synthesis of a new name
        if(this.overrideOutputs) {
           return selectFromOverrides(name)  
        }
        else 
           return synthesiseFromName(name)
    }
    
    def selectFromOverrides(String name) {
        String result = this.overrideOutputs.find { it.toString().endsWith('.' + name) }
        def replaced = null
        if(!result) {
            if(name in currentFilter) {
                log.info "Allowing reference to output not produced by filter because it was available from a filtering of an alternative input"
                replaced = this.overrideOutputs[0]
                result = this.overrideOutputs[0].replaceAll('\\.[^.]*$',"." + name)
            }
        }
        
        if(!result)
            throw new PipelineError("An output of type ." + name + " was referenced, however such an output was not in the outputs specified by an outer transform / filter / produce statement.\n\n" + "Valid outputs are: ${overrideOutputs.join('\n')}")
          else
            log.info "Selected output $result with extension $name from expected outputs $overrideOutputs"
          
        this.outputUsed = result
        if(this.outputChangeListener != null) {
            this.outputChangeListener(result,replaced)
        }
        
        return result
    }
    
    def synthesiseFromName(String name) {
        
        // If the extension of the output is the same as the extension of the 
        // input then this is more like a filter; remove the previous output extension from the path
        // eg: foo.csv.bar => foo.baz.csv
        List branchSegment = branchName ? ['.' + branchName] : [] 
        String segments = (branchSegment + [stageName,name]).collect { it.replaceAll("^\\.","").replaceAll("\\.\$","") }.join(".")
        if(stageName.equals(this.output)) {
           this.outputUsed = this.defaultOutput + '.' + name 
        }
        else
        if(this.output.endsWith(name+"."+stageName)) {
            log.info("Replacing " + name+"\\."+stageName + " with " +  stageName+'.'+name)
            this.outputUsed = this.defaultOutput.replaceAll("[.]{0,1}"+name+"\\."+stageName, '.' + segments)
        }
        else { // more like a transform: keep the old extension in there (foo.csv.bar => foo.csv.bar.xml)
            this.outputUsed = this.defaultOutput.replaceAll('\\.'+stageName+'$', '')
            if(outputUsed.contains("."))
                outputUsed = outputUsed.replaceAll('\\.[^\\.]*$', '.' + segments)
            else
                outputUsed = outputUsed + "." + segments
        }
        
        if(this.outputUsed.startsWith(".") && !this.outputUsed.startsWith("./")) // occurs when no inputs given to script and output extension used
            this.outputUsed = this.outputUsed.substring(1) 
            
        if(this.outputChangeListener != null) {
            this.outputChangeListener(this.outputUsed,null)
        }
        return this.outputUsed
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
    
}
