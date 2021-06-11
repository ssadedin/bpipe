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
import groovy.transform.CompileStatic
import groovy.util.logging.Log

import static Edge.*

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

@CompileStatic
class Edge {
    
    // Edge types
    final static String STAGE_TO_STAGE = "stage"
    final static String SEGMENT_IN = "in"
    final static String SEGMENT_OUT = "out"
    final static String RESOURCE_LINK = "resource"
    
    
    // Node types
    final static String RESOURCE = "resource"
    final static String MERGE = "merge"
    final static String STAGE = "stage"
    final static String SEGMENT = "segment"
    
    final static String[] NODE_TYPES = [STAGE_TO_STAGE,SEGMENT_IN, SEGMENT_OUT, RESOURCE,MERGE]
    
    Edge(Node from, Node to, String type) {
        assert from != null
        assert to != null
        
        setType(type)
        this.from = from
        this.to = to
    }
    
    Node from
    Node to
    String type
    
    void setType(String type) {
//        println "set type to $type"
        assert type in [STAGE_TO_STAGE,SEGMENT_IN,SEGMENT_OUT, RESOURCE_LINK]
        this.type = type
    }
    
    Map toMap() {
        [
            from: from.attributes().id,
            to: to.attributes().id,
            type: this.type
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
    
    /**
     * A list of nodes representing the current set of parallel executing stages. 
     * 
     * Why is it a list? Consider:
     * 
     *           ----- hello -----
     *         /                   \
     * hey ---o                      o---- there
     *         \                   /
     *           ----- hi --------
     *           
     * We need to track that both (hello,hi) need to be joined to (there)
     * So at any level of the pipeline there is a list of stages that represent the current
     * set of stages to fanning in / out of a point
     */
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
    
    static Closure when(Closure target, Closure condition) {
        return target
    }
    
    static Closure power(List list, Closure c) {
        sequentially(list,c)
    }
  
    
    static Closure sequentially(List list, Closure c) {
        list.collect { Object branch -> 
                c
        }.sum()
    }

    static Object rightShiftUnsigned(Closure l, Closure b) {
         return plus(l, b) 
    }
    
    static Object rightShiftUnsigned(Closure l, List b) {
        return plus(l, b) 
    }
    
    static Object plus(List l, Closure other) {
//        println "List + Closure"
        def j = {
            return it
        }
        joiners << j
        return plus(j, l) + other        
    }
    
    static executeSegmentBuilder(Closure c) {
        
        List<Node> oldCurrentStage = currentStage
        
        // The segment usually consists of a pipeline that is built - so calling its method results
        // in a builder which itself needs to be called to build the segment.
        //
        // However, the segment can return a raw pipeline stage which should NOT be invoked
        msg "Execute segment: " + PipelineCategory.closureNames[c]
        def segmentResult = Pipeline.segmentBuilders[c]()
        while(segmentResult in joiners) {
            msg "Segment builder returned joiner name=" + PipelineCategory.closureNames[segmentResult] 
            segmentResult = segmentResult()
        }
        
        List<Node> segmentEndStages = currentStage
        
        currentStage = oldCurrentStage
        
        return segmentEndStages
    }
    
    static List<String> nextLinkType = []
    
    static String popLinkType() {
        msg "Popping link type from " + nextLinkType
        if(nextLinkType) {
            return nextLinkType.pop()
        }
        else {
            return STAGE_TO_STAGE
        }
    }
    
    static debug_stage = "set_sample_info"
    
    static int level = 0
    
    static def indent (message, c) {
       msg ">>>>>>>> " + message
        ++level
        c()
        --level
       msg "<<<<<<<< " + message
    }
    
    static def msg(String message) { 
        if(false)
            println "    "*level + message
    }
    
    static Object plus(Closure c, Closure other) {
        String cName = PipelineCategory.closureNames[c]
        String otherName = PipelineCategory.closureNames[other]
        
        def result  = { 
            
            msg "plus(Closure,Closure): $cName + $otherName"
            
            List<Node> segmentEndStages = null
            if(PipelineCategory.closureNames.containsKey(c)) {
                
                boolean addInputConnection = (nodes.size() == 1)
                
                Node newStage = createNode(c)
                if(addInputConnection) {
                     edges << new Edge(inputStage,newStage, STAGE_TO_STAGE) 
                }
                
                String linkType = popLinkType()
                currentStage.each { from ->
                     edges << new Edge(from,newStage,linkType) 
                     msg "Edge: ${from.attributes().id} (${from.name()}) => ${newStage.attributes().id} (name=${newStage.name()}) type=$linkType "
                }
                
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
                
                
                if(c in Pipeline.segmentBuilders) {
                    msg "Segment: " + PipelineCategory.closureNames[c]
                    nextLinkType << SEGMENT_IN
                    segmentEndStages = executeSegmentBuilder(c)
                    
                    if(segmentEndStages) {
                        // The link from the last stage in the segment to the next stage 
                        msg "Adding links from segment body to next stage"
                        for(Node segmentEndStage in segmentEndStages)
                            edges << new Edge(segmentEndStage, newStage, SEGMENT_OUT)
                    }
                }
            }
            
            if(c in joiners) {
                indent("joiner " + c.hashCode()) {
                    c()
                }
            }
            
            if(PipelineCategory.closureNames.containsKey(other) && !Config.noDiagram.contains(otherName)) {
                
                msg "What to join $otherName to?"
                
                def newStage = createNode(other)
                
                
                // PROBLEM: at this point currentStage is what was produced coming out of the segment ... but why? 
                // wasn't it reset below? oh, no because segment was created in c() ... todo
                currentStage.each { edges << new Edge(it,newStage,STAGE_TO_STAGE) } // should consider link type?
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
                
                if(other in Pipeline.segmentBuilders) {
                    nextLinkType << SEGMENT_IN
                    // hmm, what if segment empty / single stage? no pop occurs
                    indent( "Segment: $otherName") {
                        segmentEndStages = executeSegmentBuilder(other)
                    }
                    
                    for(Node endStage in segmentEndStages) {
                        edges << new Edge(endStage, newStage, SEGMENT_OUT)
                    }
                }
            }
                
            if(other in joiners) {
                other()
            }
        }
        joiners << result
        return result
    }
    
    /**
     * Take the output from the given closure and forward
     * all of them to all the stages in the list.
     * This is a special case of multiply below. 
     */
    static Object plus(Closure c, List closureList) {
        
        
        String cName = PipelineCategory.closureNames[c] 
        
        def mul = multiply("*", closureList)
        
        def result = { inputs ->
            
            msg "Closure " + PipelineCategory.closureNames[c]  +  
                    " + List [" + closureList.collect { PipelineCategory.closureNames[it] }.join(",") + "]"
            
            List<Node> segmentEndStages
            if(c in Pipeline.segmentBuilders) {
                closureList.size().times { 
                    msg "Segment IN: $cName"
                    nextLinkType << SEGMENT_IN
                }
                List<Node> oldCurrentStage = currentStage
                
                
                indent("Segment $cName") {
                    segmentEndStages = executeSegmentBuilder(c)
                }
            }
            
            if(PipelineCategory.closureNames.containsKey(c)) {
                def newStage = createNode(c)
                currentStage.each { 
//                    println "Edge from " + it.id + " to $newStage.id"
                    edges << new Edge(it, newStage, popLinkType()) 
                }
                currentStage*.append(newStage)
                currentStage = [newStage]
                childIndices[-1]++
          }
          
          if(c in joiners)
            c()
          
            for(Node endStage in segmentEndStages) {
                for(nextSeg in closureList) {
//                    edges << new Edge(endStage, nextSeg, SEGMENT_OUT)
                    // TODO: nextSeg doesn't exist :-(
                    // This probably means that in the cases where we join a closure
                    // to a list (foo + [ seg ] + bar) the segment will not be joined
                    // to bar in the graph?
                }
            }
            
          
          return mul(inputs)
        }
        joiners << result
        return result
    }
    
    static Object multiply(java.util.regex.Pattern pattern, List segments) {
        multiply("*", segments)
    }
    
    static Object multiply(List objs, Map segments) {
        multiply(objs, segments*.value)
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
    
    static Object rightShift(Closure c, String channel) {
        return c
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
    static Object multiply(String pattern, List listStages) {
        
        listStages = listStages.collect {
            if(it instanceof List) {
                return multiply("*",it)
            }
            else
                return it
        }
            
        
        def multiplyImplementation = { input ->
            
            msg "multiply on input $input with pattern $pattern" 
            
            // Match the input
            InputSplitter splitter = new InputSplitter()
            Map samples = splitter.split(pattern, input)
            
            // Process each parallel segment in turn (no need to do them in parallel here,
            // we are only defining the pipeline, not executing it)
           List<Node> oldStages = currentStage.clone()
           List<Node> newStages = []
           int branchIndex = 0
           
           List<List<Node>> builtChildSegments = []
           
           for(Closure s in listStages.grep { it instanceof Closure }) {
                // println  "Processing segment ${s.hashCode()}"
               
                String sName = PipelineCategory.closureNames[s]
                
                // We want each child stage to see the original set of input segments
                // so reset it back at the start of each iteration 
                currentStage = oldStages
                
                // Before executing the child stage, update the branch prefix to reflect the path 
                // to its branch (note we'll pop this off afterwards below, so this gets reset for 
                // each iteration)
                branchPrefix.push(branchPrefix[-1] + stageSeparator + childIndices[-1] + branchSeparator + branchIndex)
                
                // The new segment will be executing its first child stage so put a 0 at the end of the childIndices list
                childIndices.push(0)
                
                // A joiner means that Bpipe created an intermediate closure to build a sub-portion of the pipeline
                // That means we shouldn't include it directly, but rather execute the joiner to build that sub-graph
                // and then attach to the result
                if(s in joiners) {
                    
                    // The confusion comes because this recursion might be doing fundamentally different things:
                    // if it's forward looking (+) then 
                    indent("Joiner ${s.hashCode()}") {
                        s(input) // recursive call to handle child stages 
                    }
                    
                    // The child will have executd and boiled down to a single Node added to currentStage
                    // newStages << currentStage[0] // the problem! if we double nested then this is the last nest point, not the one we shuod join to
                }
                else {
                    Node stageNode = createNode(s)
//                    println "Execute: " + PipelineCategory.closureNames[s] + " created node " + stageNode.attributes().id + " (${stageNode.name()})"
                    currentStage*.append(stageNode)
                    newStages << stageNode
                        
                    if(s in Pipeline.segmentBuilders) {
                        nextLinkType << SEGMENT_IN
                        indent("Segment $sName (multiply)") {
                            builtChildSegments << executeSegmentBuilder(s)
                        }
                    }
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
           Node mergePoint
           if(newStages.size() <= 1) {
               // Followup - how do we get this ?! currentStage contains the same node as newStages
               // Edge: 1_0-1_0-0 (everyone) => 1_0-1_0-0 (name=everyone)
//               println "Joining " + currentStage.size() + " inbound nodes to a single child"
               oldStages.each {  Node from -> newStages.each { Node to ->
//                       println "Edge: ${from.attributes().id} (${from.name()}) => ${to.attributes().id} (name=${to.name()})"
                       edges << new Edge(from,to,STAGE_TO_STAGE) 
                   }
               }
           }
           else { // When it joins to another parallel segment, need to join through 
                  // a "merge point" to reflect what the pipeline actually does
               
//               println "Joining to a merge point"
               
               // Make an intermediate dummy node to converge the outputs from 
               // the previous parallel stages
               mergePoint = createNamedNode(".", MERGE)
               
               // Join the outgoing nodes to the merge point
               oldStages.each {  from -> 
                   edges << new Edge(from, mergePoint,STAGE_TO_STAGE)
               }
               
               // Join the merge point to the next stages
               newStages.each { to ->
                   edges << new Edge(mergePoint, to,STAGE_TO_STAGE)
               }
           }
           
           if(!newStages.isEmpty()) {
               currentStage = newStages
           }
           
           childIndices[-1]++
           
           // Join up any child segments to the merge point
           for(List<Node> builtChildSegment in builtChildSegments) {
               if(mergePoint == null)
                   mergePoint = createNamedNode(".", MERGE)
                   
               for(Node n in builtChildSegment) {
                   edges << new Edge(n, mergePoint, SEGMENT_OUT)
               }
           }
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
    
    static Node createNode(Closure c, String type=null) {
        
        String name = PipelineCategory.closureNames[c]
        
        if(type == null) {
            if(c in Pipeline.segmentBuilders) {
                type = SEGMENT
            }
            else {
                type = STAGE
            }
        }
        
        Node result = createNamedNode(name,type)
        
        Pipeline.stageNodeIndex[c] = result
        
        //println "Created node ${result.attributes().id} for stage $name"
        return result
    }
    
    public static String branchSeparator="_"
    
    public static String stageSeparator="-"
    
    static Node createNamedNode(String name, String type) {
        
        assert name != null
        assert type in [RESOURCE, STAGE, SEGMENT, MERGE]
        
        // Id is in the form branchPrefix + index of child in parent
        Node n = new Node(null, name)
        
//        println "Branch prefixes are " + branchPrefix
        
        String prefix = branchPrefix[-1].replaceAll('^0\\'+stageSeparator,'') + stageSeparator
        if(prefix == "0-")
            prefix = ""
            
        String id = prefix +  childIndices[-1]
        
        if(type == SEGMENT)
            id = 's-' + id
        
        msg "Create node: " + id + " name=$name type: " + type
        
        n.attributes().id = id
        n.attributes().type = type
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
