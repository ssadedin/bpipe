package bpipe

import java.util.logging.Logger;

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
 * Represents a "magic" input object that automatically 
 * understands property references as file extensions. 
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class PipelineInput {
    
    /**
     * Logger to use with this class
     */
    private static Logger log = Logger.getLogger("bpipe.PipelineInput");
    
    /**
     * Raw inputs
     */
    def input
    
   List<PipelineStage> stages 
    
    PipelineInput(def input, List<PipelineStage> stages) {
        this.stages = stages;
        this.input = input
    }
    
    String toString() {
        return String.valueOf(Utils.first(input));
    }
    
    /**
     * Support accessing inputs by index - allows the user to use the form
     *   exec "cp ${input[0]} $output"
     */
    String getAt(int i) {
        def inputs = Utils.box(this.input)
        if(inputs.size() <= i)
            throw new PipelineError("Insufficient inputs:  at least $i inputs are expected but only ${inputs.size()} are available")
        return inputs[i]
    }
    
    /**
     * Search for the most recent input or output of any stage
     * that has the given file extension
     */
    def propertyMissing(String name) {
        def exts = [name]
        def inputs = resolveInputsWithExtensions(exts, PipelineCategory.currentStage)
        return String.valueOf(inputs[0])
    }
        
    /**
     * Search backwards through the inputs to the current stage and the outputs of
     * previous stages to find the first output that ends with the extension specified
     * for each of the given exts.
     */
    def resolveInputsWithExtensions(def exts, PipelineStage currentStage) {    
        
        def orig = exts
        
        def reverseOutputs = stages.reverse().collect { Utils.box(it.context.output) }
        
        // Add a final stage that represents the original inputs (bit of a hack)
        // You can think of it as the initial inputs being the output of some previous stage
        // that we know nothing about
        reverseOutputs.add(Utils.box(stages[0].context.@input))
        
        // Add an initial stage that represents the current input to this stage.  This way
        // if the from() spec is used and matches the actual inputs then it will go with those
        // rather than searching backwards for a previous match
        // TODO: get rid of reference to PipelineCategory here
        // how to model "current stage" when pipeline has parallel parts?
        reverseOutputs.add(0,Utils.box(currentStage.context.@input))
        
        def filesWithExts = Utils.box(exts).collect { String inp ->
            
            if(!inp.startsWith("."))
                inp = "." + inp
            
            for(s in reverseOutputs) {
                log.info("Checking outputs ${s}")
                def o = s.find { it?.endsWith(inp) }
                if(o)
                    return o
            }
        }
        
        if(filesWithExts.any { it == null})
            throw new PipelineError("Unable to locate one or more specified inputs from pipeline with extension(s) $orig")
            
        return filesWithExts
    }
}