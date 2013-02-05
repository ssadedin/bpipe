package bpipe

import groovy.util.logging.Log;

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
        return this.output[i]
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
        String branchSegment = branchName ? '.' + branchName : ''
        if(stageName.equals(this.output)) {
           this.outputUsed = this.defaultOutput + '.' + name 
        }
        else
        if(this.output.endsWith(name+"."+stageName)) {
            log.info("Replacing " + name+"\\."+stageName + " with " +  stageName+'.'+name)
            this.outputUsed = this.defaultOutput.replaceAll(name+"\\."+stageName, branchSegment + stageName+'.'+name)
        }
        else { // more like a transform: keep the old extension in there (foo.csv.bar => foo.csv.bar.xml)
            this.outputUsed = this.defaultOutput.replaceAll('\\.'+stageName+'$', '')
                                                .replaceAll('\\.[^\\.]*$',branchSegment+'.'+stageName + '.'+name)
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
        return PipelineCategory.getPrefix(String.valueOf(Utils.first(output)));
    } 
    
}
