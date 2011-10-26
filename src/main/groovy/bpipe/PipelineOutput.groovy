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
class PipelineOutput {
    
    /**
     * Logger to use with this class
     */
    private static Logger log = Logger.getLogger("bpipe.PipelineInput");
    
    /**
     * Raw inputs
     */
    def input
    
    /**
     * The name of the stage that this output is being created for.
     * Used as part of the naming convention, depending on how the
     * inputs are accessed
     */
    String stageName
    
    def outputUsed
    
    PipelineOutput(def input, String stageName) {
        this.input = input
    }
    
    String toString() {
        return String.valueOf(Utils.first(input)) + "." + stageName;
    }
    
    /**
     * Support accessing outputs by index - allows the user to use the form
     *   exec "cp foo.txt ${output[0]}"
     */
    String getAt(int i) {
        def inputs = Utils.box(this.input)
        if(inputs.size() <= i)
            throw new PipelineError("Insufficient inputs:  output $i was referenced but there are only ${inputs.size()} inputs on which to base output file names")
        return this.input[i] + "."+stageName
    }
    
    /**
     * Return the first input with the file extension replaced with the 
     * one specified.
     */
    def propertyMissing(String name) {
        
        def inp = Utils.first(input)
        if(!inp)
           throw new PipelineError("Expected input but no input provided")
           
        this.outputUsed = inp.replaceAll('\\.[^\\.]*$','.'+extension)
        
        return this.outputUsed
    }
}