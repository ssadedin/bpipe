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
package bpipe

import static bpipe.Utils.*
import groovy.lang.Closure

import java.util.logging.Logger

/**
 * A category that adds a plus operator for closures that 
 * allows us to inspect the chain that is produced by adding 
 * them together without actually executing them.
 */
class DefinePipelineCategory {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineCategory");
    
    /**
     * List stages found
     */
    static def stages = []
    
    static def joiners = []
    
    static Object plus(Closure c, Closure other) {
        def result  = { 
            
            if(PipelineCategory.closureNames.containsKey(c))
	            stages << PipelineCategory.closureNames[c]
                
            if(c in joiners)
                c()
            
            if(PipelineCategory.closureNames.containsKey(other))
	            stages << PipelineCategory.closureNames[other]
                
            if(other in joiners)
	            other()
        }
        joiners << result
        return result
    }
}
