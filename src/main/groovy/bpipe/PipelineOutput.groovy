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
     * Create a pipeline output wrapper
     * 
     * @param output            the output to be returned if this object is directly converted to a string
     * @param stageName         the pipeline stage for which this wrapper is going to create derived output names
     * @param defaultOutput     the default pre-computed stage name (used to created drived names)
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
        
        if(this.overrideOutputs) {
           return selectFromOverrides(name)  
        }
        else 
           return synthesiseFromName(name)
    }
    
    def selectFromOverrides(String name) {
        String result = this.overrideOutputs.find { it.endsWith('.' + name) }
        if(!result)
            throw new PipelineError("An output of type ." + name + " was referenced, however such an output was not specified to occur by an outer transform / filter / produce statement")
        return result
    }
    
    def synthesiseFromName(String name) {
        
        // If the file extension of the output 
        if(this.output.endsWith(name+"."+stageName)) {
            log.info("Replacing " + name+"\\."+stageName + " with " +  stageName+'.'+name)
            this.outputUsed = this.defaultOutput.replaceAll(name+"\\."+stageName, stageName+'.'+name)
        }
        else {
            this.outputUsed = this.defaultOutput.replaceAll('\\.'+stageName+'$', '').replaceAll('\\.[^\\.]*$','.'+stageName + '.'+name)
        }
            
		if(this.outputChangeListener != null) {
			this.outputChangeListener(this.outputUsed)
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
        return PipelineCategory.getPrefix(String.valueOf(Utils.first(input)));
	} 
}
