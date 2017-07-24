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

class Edge {
    
    Edge(Node from, Node to) {
        assert from != null
        assert to != null
        
        this.from = from
        this.to = to
    }
    
    Node from
    Node to
    
    Map toMap() {
        [
            from: from.attributes().id,
            to: to.attributes().id
        ]
    }
}

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
    
    /**
     * The list of branches that defines the path leading up to the current branch in the current recursive 
     * scan of the pipeline tree
     */
    static List<String> branchPrefix = ["0"]
    
    /**
     * In a set of sequential stages, each stage is numbered (0,1,2,...).
     * Here we track the number of the current child during the recursive scan of the pipeline 
     * tree. Each element of the list represents a the child count at a different depth within the pipeline.
     * Eg: [0,3,1] means the 2nd child of the fourth child of the first node in the pipeline
     */
    static List<Integer> childIndices = [0]
    
    static def joiners = []
    
    /**
     * Lookup of node by id
     * 
     * Since pipeline stages can be reused in different parts of a pipeline, 
     * we have to assign unique ids separate to the name of the pipeline stage.
     */
    static Map<String,List> nodes = [:]
    
    static List<Edge> edges = []
    
    static void reset() {
      inputStage = new Node(null, "input", []) 
      inputStage.attributes().id = "input"
      
      currentStage = [inputStage]
      joiners = []
      nodes = [ input: inputStage ]
      edges = []
    }
    
    static {
        reset()
    }
    
    static Object plus(List l, List other) {
        if(!l.empty && !other.empty && l[0] instanceof Closure && other[0] instanceof Closure) {
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
//        println "List + Closure"
        def j = {
            return it
        }
        joiners << j
        return plus(j, l) + other        
    }
    
    static Object plus(Closure c, Closure other) {
        String cName = PipelineCategory.closureNames[c]
        // println "Closure " + cName  +  " (${c.hashCode()}) + Closure " + PipelineCategory.closureNames[other]
        def result  = { 
            
            if(c in Pipeline.segmentBuilders) {
                Pipeline.segmentBuilders[c]()()
            }
            else
            if(PipelineCategory.closureNames.containsKey(c)) {
                
                boolean addInputConnection = (nodes.size() == 1)
                
                def newStage = createNode(c)
                if(addInputConnection) {
                     edges << new Edge(inputStage,newStage) 
                }
                
                currentStage.each { from ->
                     edges << new Edge(from,newStage) 
                     // println "Edge: ${from.attributes().id} (${from.name()}) => ${newStage.attributes().id} (name=${newStage.name()})"
                }
                
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
            }
            
            if(c in joiners) {
                c()
            }
            
            if(other in Pipeline.segmentBuilders) {
                // println "Descend into ${other.hashCode()} closure name=" + PipelineCategory.closureNames[other]
                Pipeline.segmentBuilders[other]()()
            }
            else
            if(PipelineCategory.closureNames.containsKey(other) 
                && !Config.noDiagram.contains(PipelineCategory.closureNames[other])) {
                def newStage = createNode(other)
                
                currentStage.each { edges << new Edge(it,newStage) }
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
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
        
//        println "Closure " + PipelineCategory.closureNames[other]  +  
//                " + List [" + segments.collect { PipelineCategory.closureNames[it] }.join(",") + "]"
        
        def mul = multiply("*", segments)
        
        def result = { inputs ->
            
            if(other in Pipeline.segmentBuilders) {
                Pipeline.segmentBuilders[other]()()
            }
            else
            if(PipelineCategory.closureNames.containsKey(other)) {
                def newStage = createNode(other)
                currentStage.each { 
//                    println "Edge from " + it.id + " to $newStage.id"
                    edges << new Edge(it,newStage) 
                }
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
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
    
    String branchSeparator = stageSeparator
        
    /**
     * Implements the syntax for splitting the pipeline into multiple
     * parallel paths.
     * <p>
     * <code>"sample_%_*.txt" * [stage1 + stage2 + stage3]</code>
     * 
     * To construct the diagram, the input nodes need to be connected
     * to each of the parallel segments with edges. Each parallel segment
     * is executed to create a sub-graph, and then 
     * 
     */
    static Object multiply(String pattern, List segments) {
        def multiplyImplementation = { input ->
            
//             println "multiply on input $input with pattern $pattern"
            
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
            
            // Process each parallel segment in turn (no need to do them in parallel here,
            // we are only defining the pipeline, not executing it)
           List<Node> oldStages = currentStage.clone()
           List<Node> newStages = []
           int branchIndex = 0
           for(Closure s in segments.grep { it instanceof Closure }) {
                // println  "Processing segment ${s.hashCode()}"
                
                // We want each child segment to see the original set of input segments
                // so reset it back at the start of each iteration 
                currentStage = oldStages
                
                // Before executing the child segment, update the branch prefix to reflect the path 
                // to its branch (note we'll pop this off afterwards below, so this gets reset for 
                // each iteration)
                branchPrefix.push(branchPrefix[-1] + stageSeparator + childIndices[-1] + branchSeparator + branchIndex)
                
                // The new segment will be executing its first child stage so put a 0 at the end of the childIndices list
                childIndices.push(0)
                
                // A joiner means that Bpipe created an intermediate closure to build a sub-portion of the pipeline
                // That means we shouldn't include it directly, but rather execute the joiner to build that sub-graph
                // and then attach to the result
                if(s in Pipeline.segmentBuilders) {
                    Pipeline.segmentBuilders[s]()()
                }
                else                
                if(s in joiners) {
                    
                    // The confusion comes because this recursion might be doing fundamentally different things:
                    // if it's forward looking (+) then 
                    s(input) // recursive call to handle child stages 
                    
                    // The child will have executd and boiled down to a single Node added to currentStage
                    // newStages << currentStage[0] // the problem! if we double nested then this is the last nest point, not the one we shuod join to
                }
                else {
                    Node stageNode = createNode(s)
//                    println "Execute: " + PipelineCategory.closureNames[s] + " created node " + stageNode.attributes().id + " (${stageNode.name()})"
                    currentStage*.append(stageNode)
                    newStages << stageNode
                }
                
                childIndices.pop()
                branchPrefix.pop()
                ++branchIndex
           }
           
           // Should this be reset here? Seems like it: we are using it below to join up to the created stages,
           // but children may have modified currentStage.
//           currentStage = oldStages
           
           // For a case where the parallel segment joins to a simple node
           // or serial segment after we can connect it directly
           if(newStages.size() <= 1) {
               // Followup - how do we get this ?! currentStage contains the same node as newStages
               // Edge: 1_0-1_0-0 (everyone) => 1_0-1_0-0 (name=everyone)
//               println "Joining " + currentStage.size() + " inbound nodes to a single child"
               oldStages.each {  Node from -> newStages.each { Node to ->
//                       println "Edge: ${from.attributes().id} (${from.name()}) => ${to.attributes().id} (name=${to.name()})"
                       edges << new Edge(from,to) 
                   }
               }
           }
           else { // When it joins to another parallel segment, need to join through 
                  // a "merge point" to reflect what the pipeline actually does
               
//               println "Joining to a merge point"
               
               // Make an intermediate dummy node to converge the outputs from 
               // the previous parallel stages
               Node mergePoint = createNamedNode(".")
               
               // Join the outgoing nodes to the merge point
               oldStages.each {  from -> 
                   edges << new Edge(from, mergePoint)
               }
               
               // Join the merge point to the next stages
               newStages.each { to ->
                   edges << new Edge(mergePoint, to)
               }
           }
           
           if(!newStages.isEmpty()) {
               currentStage = newStages
           }
           
           childIndices[-1]++
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
    
    static Node createNode(Closure c) {
        String name = PipelineCategory.closureNames[c]
        Node result = createNamedNode(name)
        //println "Created node ${result.attributes().id} for stage $name"
        return result
    }
    
    public static String branchSeparator="_"
    
    public static String stageSeparator="-"
    
    static Node createNamedNode(String name) {
        
        // Id is in the form branchPrefix + index of child in parent
        Node n = new Node(null, name)
        
//        println "Branch prefixes are " + branchPrefix
        
        String prefix = branchPrefix[-1].replaceAll('^0\\'+stageSeparator,'') + stageSeparator
        if(prefix == "0-")
            prefix = ""
            
        String id = prefix +  childIndices[-1]
        
        n.attributes().id = id
        nodes[id] = n
        return n
    }
    
    static Node createNamedNodeOld(String name) {
        
        
        
        String id = name
        int i = 0
        while(nodes.containsKey(id)) {
            ++i
            id = name + "." + i
        }
        Node n = new Node(null, name)
        n.attributes().id = id
        nodes[id] = n
        return n
    }
}
