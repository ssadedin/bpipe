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

import groovy.lang.Closure

import java.util.logging.Logger

/*
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
class DefinePipelineCategory {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineCategory");
    
    /**
     * List stages found
     */
    static Node inputStage = new Node(null, "input", []) 
    
    static List<Node> currentStage = [inputStage]
	
    static def joiners = []
    
    static Object plus(Closure c, Closure other) {
        def result  = { 
            
            if(PipelineCategory.closureNames.containsKey(c)) {
	            def newStage = new Node(null, PipelineCategory.closureNames[c])
	            currentStage*.append(newStage)
                currentStage = [newStage]
            }
                
            if(c in joiners)
                c()
            
            if(PipelineCategory.closureNames.containsKey(other)) {
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
//    static Object plus(Closure other, List segments) {
//        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
//        Closure mul = multiply("*", segments)
//        def plusImplementation =  { input1 ->
//            
//            def currentStage = new PipelineStage(pipeline.createContext(), other)
//            pipeline.stages << currentStage
//            currentStage.context.setInput(input1)
//            currentStage.run()
//            Utils.checkFiles(currentStage.context.output)
//                    
//            // If the stage did not return any outputs then we assume
//            // that the inputs to the next stage are the same as the inputs
//            // to the previous stage
//            def nextInputs = currentStage.context.nextInputs
//            if(nextInputs == null)
//                nextInputs = currentStage.context.@input
//                
//            Utils.checkFiles(nextInputs)
//            return mul(nextInputs)
//		}
//        pipeline.joiners << plusImplementation
//        return plusImplementation
//	}
    
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
            
			// Match the input
            InputSplitter splitter = new InputSplitter()
            Map samples = splitter.split(pattern, input)
			
            // Now we have all our samples, make a 
			// separate pipeline for each one, and for each parallel stage
           def oldStages = currentStage
           def newStages = []
           for(Closure s in segments) {
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
	
}
