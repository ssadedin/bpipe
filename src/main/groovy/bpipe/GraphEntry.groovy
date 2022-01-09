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

import java.util.concurrent.locks.ReentrantReadWriteLock

import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * The core element modeling dependencies in Bpipe.
 * <p>
 * A <code>GraphEntry</code> is a node in the dependency graph representing 
 * a set of outputs sharing a common set of child nodes (who depend on the 
 * outputs) and inputs (parents nodes). 
 * 
 * @author ssadedin
 */
@Log
class GraphEntry implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    /**
     * Optional lock - if lock is non-null, this class will lock read operations
     * using the read lock for the lock provided. The idea is that you can set
     * a lock on the top level (root) entry, while leaving lower entries unlocked
     * 
     * NOT CURRENTLY USED - because I'm worried about the overhead of the 
     * locking occuring at fine grained level that will happen if locks are acquired
     * and released for each method call. Trying out locking at higher level to see
     * how easy it is to contain things there.
     */
    ReentrantReadWriteLock lock = null
    
    /**
     * The outputs that this node represents. In practise, each graph entry corresponds to 
     * a single output
     */
    List<OutputMetaData> values
    
    List<GraphEntry> parents = []
    
    List<GraphEntry> children = []
    
    
    /**
     * An optional index to speed up lookups by canonical path - not populated by default,
     * but can be populated by using index()
     */
    transient Map<String, GraphEntry> index = null
    
    public static int GRAPH_ENTRY_COUNT = 0
    
    /*
    GraphEntry() {
        ++GRAPH_ENTRY_COUNT
    }
    
    GraphEntry(Map props) {
        super(props)
        ++GRAPH_ENTRY_COUNT
    }
    */
    
    @CompileStatic
    GraphEntry findBy(Closure<Boolean> c) {
        if(this.values != null) { 
            for(OutputMetaData value in this.values)
                if(c(value) != false)
                    return this
        }
            
        for(GraphEntry child in children) {
           def result = child.findBy(c)
           if(result)
               return result;
        }
    }
    
    @CompileStatic
    void index(int sizeHint) {
        Utils.time("Index output graph") {
            Map<String, GraphEntry> indexTmp = new HashMap(sizeHint)
            depthFirst { GraphEntry e ->
                if(!e.values.is(null) && !e.values.isEmpty()) {
                    for(OutputMetaData p in e.values) {
                        indexTmp[(String)p.canonicalPath] = e
                    }
                }
            }
            this.index = indexTmp
        }
    }
    
    List<OutputMetaData> findAllOutputsBy(Closure c) {
        
        
        List<OutputMetaData> results = []
        
        if(this.values != null)
            results.addAll(this.values.grep { c(it)})
            
        for(GraphEntry child in children) {
           results.addAll(child.findAllOutputsBy(c))
        }
        
        return results
    }
    
    @CompileStatic
    void addAsChildInGraph(OutputMetaData out, GraphEntry topRoot) {
        for(String inp in out.canonicalInputs) {
//          GraphEntry parentEntry = topRoot.findBy { OutputMetaData o -> o.canonicalPath == inp } 
            GraphEntry parentEntry = topRoot.entryForCanonicalPath(inp)
            if(parentEntry) {
                if(!(this in parentEntry.children))
                    parentEntry.children << this
                this.parents << parentEntry
            }
            else
                log.info "Dependency $inp for $out.outputPath is external root input"
        }
    }
    
    /**
     * Search the graph for entry with given outputfile
     */
    @CompileStatic
    GraphEntry entryFor(File outputFile) {
        // In case of non-default output directory, the outputFile itself may be in a directory
        final String outputFilePath = Utils.canonicalFileFor(outputFile.path).path
        return entryForCanonicalPath(outputFilePath)
    }
    
    @CompileStatic
    GraphEntry entryFor(PipelineFile pipelineFile) {
        // In case of non-default output directory, the outputFile itself may be in a directory
        final String outputFilePath = Utils.canonicalFileFor(pipelineFile.toPath().normalize().toString()).path
        return entryForCanonicalPath(outputFilePath)
    } 
    
    static int cacheMisses = 0
    
    @CompileStatic
    GraphEntry entryForCanonicalPath(String canonicalPath) {
        
        if(index?.containsKey(canonicalPath)) {
            return index[canonicalPath]
        }
        
        // In case of non-default output directory, the outputFile itself may be in a directory
        GraphEntry result = findBy { OutputMetaData p ->
           !p.outputFile.is(null) && (canonicalPathFor(p) == canonicalPath)
        }
        
        if(index != null)
            index.put(canonicalPath, result)
            
        return result
    }
    
     /**
     * getCanonicalPath can be very slow on some systems, so 
     * this method caches them in the property file itself.
     * 
     * @param p
     * @return
     */
    @CompileStatic
    static String canonicalPathFor(OutputMetaData p) {
        // Note: used to synchronize on p here, but the syhronization seems unnecessary
        // because references are always atomically assigned, and the cost of computing twice
        // is probably lower than the general overhead of continuously locking to access this value
        if(p.canonicalPath != null)
            return p.canonicalPath
                
        assert p.outputFile

        p.canonicalPath = Utils.canonicalFileFor(p.outputFile.path).path
    }
    
    @CompileStatic
    OutputMetaData propertiesFor(PipelineFile outputFile) { 
        propertiesFor(outputFile.path)
    }
    
    /**
     * Search the graph for OutputMetaData for the given output file
     */
    @CompileStatic
    OutputMetaData propertiesFor(String outputFile) { 
       // In case of non-default output directory, the outputFile itself may be in a directory
       String outputFilePath = Utils.canonicalFileFor(outputFile).path
       
       List<OutputMetaData> values 
       if(this.lock != null) {
           this.lock.readLock().lock()
           try {
               values = entryForCanonicalPath(outputFilePath)?.values
           }
           finally{
               this.lock.readLock().unlock()
           }
       }
       else {
           values = entryForCanonicalPath(outputFilePath)?.values
       }
       
       if(!values)
           return null
           
       for(def o in values) {
           if(canonicalPathFor(o) == outputFilePath) {
               return o
           }
       }
       return null
    }
    
    /**
     * Create a copy of the subtree for this GraphEntry and its children (but NOT parents!)
     */
    GraphEntry clone() {
        new GraphEntry(values: values.clone(), parents: parents.clone(), children: children.collect { it.clone() })
    }
    
    @CompileStatic
    void depthFirst(Closure c) {
        
        c(this)
        
        this.children*.depthFirst(c)
    }
    
    /**
     * Filter the graph so that only paths containing the specified output remain
     */
    GraphEntry filter(String output) {
        GraphEntry outputEntry = this.entryFor(new File(output))
        
        if(!outputEntry) {
            return null
        }
        
        GraphEntry result = outputEntry.clone()
        
        GraphEntry root = null
        
        // Note: need the declaration separate to assignment because the closure is referenced
        // in its own body(!)
        def trimParent 
        trimParent = { GraphEntry p, GraphEntry c, int index ->
            // Remove all parents that don't contain the child
            
            p = new GraphEntry(values: p.values, parents: p.parents, children: [c]) 
            c.parents[index] = p
            
            if(p in p.parents)
                throw new RuntimeException("Internal error: an output appears to be a dependency of itself")
            
            // Recurse upwards
            if(p.parents)
                p.parents.eachWithIndex { gp, pindex -> trimParent(gp, p, pindex) }
            else
                root = p
        }
        
        if(outputEntry.parents)
            outputEntry.parents.eachWithIndex { p,index -> trimParent(p, result, index) }
        else
            root = outputEntry
        
        return root
    }
    
    /**
     * Divide the outputs into groups depending on their inputs.
     * 
     * @return  Map keyed on sorted inputs separated by commas (as string), 
     *          with values being the list of outputs having those inputs
     */
    @CompileStatic
    Map<String,List> groupOutputs(List<OutputMetaData> outputs = null) {
        Map<String,List> outputGroups = [:]
        if(outputs == null)
            outputs = this.values
            
        for(OutputMetaData o in outputs) {
            def key = o.inputs.sort().join(',')
            if(!outputGroups.containsKey(key)) {
                outputGroups[key] = [o]
            }
            else {
                outputGroups[key] << o
            }
        }
        return outputGroups
    }
    
    String dump() {
        
        def inputs = children*.values*.inputs.flatten().unique()
        if(!inputs)
            inputs = ["<no inputs>"]
            
        String inputValue = inputs.join('\n') + ' => \n'
        return inputValue + dumpChildren(Math.min(20,inputs.collect {it.size()}.max()))
        
//        return dumpChildren(0)
    }
   
    /**
     * Render this graph entry as a tree
     */
    String dumpChildren(int indent = 0, List<OutputMetaData> filter = null) {
        
       
        def valuesToDump = filter != null ? filter : values
        
        def names = values*.outputFile*.name*.toString()
        
        String me = names?names.collect { " " * indent + it }.join("\n") + (children?" => \n":"") :""
        
        StringBuilder result = new StringBuilder()
        def filteredChildren = children
        if(filter) {
          def filterOutputs = filter*.outputFile*.name.flatten()
          
          filteredChildren = children.grep { child ->
              // Only output children who have one of the output files in the filter
              // as an input
              child.values*.inputs.flatten().find { childInput ->  
//                  if(filterOutputs.contains(childInput))
//                    println "Compare $filterOutputs with $childInput YES"
//                  else
//                    println "Compare $filterOutputs with $childInput NO"
//                    
                  return filterOutputs.contains(childInput)  
              }
          }
        }
        int maxIndent = 20
        return me + filteredChildren*.dumpChildren(indent+Math.min((names.collect{it.size()}.max()?:0), maxIndent)).join('\n')
    }
    
    /**
     * Return the set of dependencies (inputs) to this graph entry that the given output
     * depends on
     * 
     * @param out
     * @return
     */
    @CompileStatic
    List<OutputMetaData> getParentDependencies(OutputMetaData out) {
       (List<OutputMetaData>)parents*.values.flatten().grep { OutputMetaData parentOutput -> 
           out.hasInput(parentOutput.canonicalPath) 
       } 
    }
    
    String toString() {
        "GraphEntry [outputs=" + values*.outputPath + ", inputs="+values*.inputs + " with ${children.size()} children]"
    }
}
