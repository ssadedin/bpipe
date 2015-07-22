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

import static bpipe.Utils.*

import java.util.List;
import java.util.Map;

import groovy.lang.Closure
import groovy.util.logging.Log

/*
@Log
@Log
class PipelineNode {
    
	String name
    
	PipelineNode next
	
    PipelineNode parent 
	
	List<PipelineNode> children = []
}
*/

/**
 * A category that adds a plus operator for closures that 
 * allows us to inspect the chain that is produced by adding 
 * them together without actually executing them.
 */
@Log
class DefinePipelineCategory {
    
    /**
     * List stages found
     */
    static Node inputStage = null
    
    static List<Node> currentStage = null
	
    static def joiners = []
    
    
    static void reset() {
      inputStage = new Node(null, "input", []) 
      currentStage = [inputStage]
      joiners = []
    }
    
    static {
        reset()
    }
    
    static Object plus(List l, List other) {
        if(!l.empty && !other.empty && l[0] instanceof Closure && other[1] instanceof Closure) {
            def j = {
                return it
            }
            joiners << j
            return plus(j, l) + other
        }
        else
            return org.codehaus.groovy.runtime.DefaultGroovyMethods.plus(l,other)
    }

    static Object plus(List l, Closure other) {
        def j = {
            return it
        }
        joiners << j
        return plus(j, l) + other        
    }
    
    static Object plus(Closure c, Closure other) {
        def result  = { 
            
            if(PipelineCategory.closureNames.containsKey(c)) {
	            def newStage = new Node(null, PipelineCategory.closureNames[c])
	            currentStage*.append(newStage)
                currentStage = [newStage]
            }
                
            if(c in joiners)
                c()
            
            if(PipelineCategory.closureNames.containsKey(other) && !Config.noDiagram.contains(PipelineCategory.closureNames[other])) {
	            def newStage = new Node(null, PipelineCategory.closureNames[other])
	            currentStage*.append(newStage)
                currentStage = [newStage]
            }
                
            if(other in joiners)
	            other()
        }
        joiners << result
        return result
    }
    
    /**
     * Take the output from the given closure and forward
     * all of them to all the stages in the list.
     * This is a special case of multiply below. 
     */
    static Object plus(Closure other, List segments) {
        
        def mul = multiply("*", segments)
        
        def result = { inputs ->
          if(PipelineCategory.closureNames.containsKey(other)) {
	            def newStage = new Node(null, PipelineCategory.closureNames[other])
	            currentStage*.append(newStage)
                currentStage = [newStage]
          }
          
          if(other in joiners)
            other()
          
          return mul(inputs)
        }
        joiners << result
        return result
	}
    
    static Object multiply(java.util.regex.Pattern pattern, List segments) {
        multiply("*", segments)
    }
    
	static Object multiply(List chrs, List segments) {
        multiply("*", segments)
    }
    
	static Object multiply(Set chrs, List segments) {
        multiply("*", segments)
    }
    
	static Object multiply(Map chrs, List segments) {
        multiply("*", segments)
    }
    
    static Object multiply(String pattern, Closure c) {
        throw new PipelineError("Multiply syntax requires a list of stages")
    }
        
    /**
     * Implements the syntax that allows an input filter to 
     * break inputs into samples and pass to multiple parallel 
     * stages in the form
     * <p>
     * <code>"sample_%_*.txt" * [stage1 + stage2 + stage3]</code>
     */
	static Object multiply(String pattern, List segments) {
		def multiplyImplementation = { input ->
            
			log.info "multiply on input $input with pattern $pattern"
            
            segments = segments.collect {
                if(it instanceof List) {
                    return multiply("*",it)
                }
                else
                    return it
            }
            
			// Match the input
            InputSplitter splitter = new InputSplitter()
            Map samples = splitter.split(pattern, input)
			
            // Now we have all our samples, make a 
			// separate pipeline for each one, and for each parallel stage
           def oldStages = currentStage
           def newStages = []
           
           for(Closure s in segments.grep { it instanceof Closure }) {
                log.info "Processing segment ${s.hashCode()}"
                currentStage = oldStages
                if(s in joiners) {
                    s(input)
                    newStages << currentStage[0]
                }
                else {
				    def stageNode = new Node(null,PipelineCategory.closureNames[s])
				    currentStage*.append(stageNode)
                    newStages << stageNode
                }
           }
           currentStage = newStages
		}
        
        joiners << multiplyImplementation
        
        return multiplyImplementation
        
	}
    
    static Closure using(Closure c, Map params) {
        return c
    }
	
    static Closure using(Closure c, Object... args) {
        return c
    }
}
