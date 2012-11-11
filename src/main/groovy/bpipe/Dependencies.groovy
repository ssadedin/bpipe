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

import groovy.util.logging.Log;

class GraphEntry {
    
    List<Properties> values
    List<GraphEntry> parents = []
    List<GraphEntry> children = []
    
    GraphEntry findBy(Closure c) {
        if(this.values != null && values.any { c(it) })
            return this
            
        for(GraphEntry child in children) {
           def result = child.findBy(c)
           if(result)
               return result;
        }
    }
    
    /**
     * Search the graph for entry with given outputfile
     */
    GraphEntry entryFor(String outputFile) {
        findBy { it.outputFile.name == outputFile }
    }
    
    /**
     * Create a copy of the subtree for this GraphEntry and its children (but NOT parents!)
     */
    GraphEntry clone() {
        new GraphEntry(values: values.clone(), parents: parents.clone(), children: children.collect { it.clone() })
    }
    
    void depthFirst(Closure c) {
        
        c(this)
        
        this.children*.depthFirst(c)
    }
    
    /**
     * Filter the graph so that only paths containing the specified output remain
     */
    GraphEntry filter(String output) {
        GraphEntry outputEntry = this.entryFor(output)
        
        GraphEntry result = outputEntry.clone()
        
        GraphEntry root = null
        
        def trimParent 
        trimParent = { GraphEntry p, GraphEntry c, int index ->
            // Remove all parents that don't contain the child
            p = new GraphEntry(values: p.values, parents: p.parents, children: [c]) 
            c.parents[index] = p
            
            // Recurse upwards
            if(p.parents)
                p.parents.eachWithIndex { gp, pindex -> trimParent(gp, p, pindex) }
            else
                root = p
        }
        
        outputEntry.parents.eachWithIndex { p,index -> trimParent(p, result, index) }
        
        return root
    }
    
    String dump() {
        String inputs = this.values*.inputs.flatten().join(',') + ' => \n'
        inputs + dumpChildren(inputs.size())
    }
   
    /**
     * Render this graph entry as a tree
     */
    String dumpChildren(int indent = 0) {
        String me = values*.outputFile*.name.join(",") + (children?" => \n":"")  
        return (" " * indent) + me + children*.dumpChildren(indent+me.size()).join('\n')
    }
}

/**
 * Manages dependency tracking functions
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Singleton
@Log
class Dependencies {
    
    GraphEntry outputGraph
    
    /**
     * Return true iff all the outputs and downstream outputs for which
     * they are dependencies are present and up to date.
     * 
     * @param outputs
     * @param inputs
     * @return
     */
    boolean checkUpToDate(def outputs, def inputs) {
        
        inputs = Utils.box(inputs)
        
        // If there are no outputs then by definition, they are all up to date
        if(!outputs)
            return true
        
        // If the outputs are created from nothing (no inputs)
        // then they are up to date as long as they exist
        if(!inputs) 
            return outputs.collect { new File(it) }.every { it.exists() }
            
        // The most obvious case: all the outputs exist and are newer than 
        // the inputs. We can return straight away here
        List<File> older = Utils.findOlder(outputs,inputs)
        if(!older)
            return true
        else
            return false
        
        synchronized(this) {
          if(this.outputGraph == null)
              this.outputGraph = computeOutputGraph(scanOutputFolder())
        }
        
        // TODO:
        // 1. find all leaf nodes that are children of each older file
        //    in the above graph
        // 
        // 2. if the file exists and is older, return false
        //
        // 3. if the file does not exist, find all leaf nodes
        //    connected to the file.  For each leaf node,
        //    verify the path to the leaf node is populated with
        //    either existing files or missing files marked as cleaned
        //    and having newer timestamps than their inputs
    }
    
    /**
     * For each output file created in the context, save information
     * about it such that it can be reliably loaded by this same stage
     * if the pipeline is re-executed.
     */
    void saveOutputs(PipelineContext context) {
        context.trackedOutputs.each { String cmd, List<String> outputs ->
            for(def o in outputs) {
                o = Utils.first(o)
                if(!o)
                    continue
                    
                File file = context.getOutputMetaData(o)
                String hash = Utils.sha1(cmd+"_"+o)

                Properties p = new Properties()
                p.command = cmd
                
                def allInputs = ((context.@inputWrapper?.resolvedInputs?:[]) + context.allResolvedInputs).flatten().unique()
                
                p.propertyFile = file.name
                p.inputs = allInputs.join(',')?:''
                p.outputFile = o
                p.timestamp = String.valueOf(new File(o).lastModified())
                p.fingerprint = hash
                p.cleaned = false
                
                saveOutputMetaData(p)
            }
        }
    }
    
    /**
     * Store the given properties file as an output meta data file
     */
    void saveOutputMetaData(Properties p) {
        
        p = p.clone()
        
        File file = new File(PipelineContext.OUTPUT_METADATA_DIR, p.propertyFile)
        
        // Undo possible format conversions so everything is strings
        if(p.inputs instanceof List)
            p.inputs = p.inputs.join(",")
        
        p.cleaned = String.valueOf(p.cleaned)
        p.timestamp = String.valueOf(p.timestamp)
        
        if(p.outputFile instanceof File)
            p.outputFile = p.outputFile.name
        
        log.info "Saving output file details to file $file for command " + Utils.truncnl(p.command, 20)
        file.withOutputStream { ofs ->
            p.save(ofs, "Bpipe Output File Meta Data")
        }
    }
    
    
    /**
     * Computes the files that are created as non-final products of the pipeline and 
     * shows them to the user, offering to delete them.
     */
    void cleanup() {
        
        List<Properties> outputs = scanOutputFolder()
        
        // Start by scanning the output folder for dependency files
        def graph = computeOutputGraph(outputs)
        
        println "\nOutput graph is: \n\n" + graph.dump()
        
        // Identify the leaf nodes
        List leaves = findLeaves(graph)
        
        List internalNodes = outputs - leaves*.values.flatten()
        
        if(!internalNodes) {
            println """
                No files were found as eligible outputs to clean up.
                
                You may mark a file as disposable by using @Intermediate annotations
                in your Bpipe script.
            """.stripIndent()
        }
        else {
            // Print out each leaf
            println "\nThe following intermediate files will be removed:\n"
            println '\t' + internalNodes*.outputFile*.name.join('\n\t')
            println "\nTo retain files, cancel this command and use 'bpipe preserve' to preserve the files you wish to keep"
            print "\nRemove these files? (y/n): "
            def answer = System.in.withReader { it.readLine() }
            
            if(answer == "y") {
                internalNodes.each { removeOutputFile(it) }
            }
        }
    }
    
    void removeOutputFile(Properties outputFileProperties) {
        outputFileProperties.cleaned = true
        saveOutputMetaData(outputFileProperties)
        File outputFile = outputFileProperties.outputFile
        if(!outputFile.delete()) {
            log.warn("Failed to delete output file ${outputFileProperties.absolutePath}")
            System.err.println "Failed to delete file ${outputFileProperties.absolutePath}"
        }
    }
    
    void queryOutputs(def args) {
        // Start by scanning the output folder for dependency files
        List<Properties> outputs = scanOutputFolder()
        def graph = computeOutputGraph(outputs)
        
        if(args) {
            for(String arg in args) {
                println "\n" + " $arg ".center(Config.config.columns, "=")  + "\n"
                println "\n" + graph.filter(arg).dump()
            }
            println("\n" + ("=" * Config.config.columns))
        }
        else {
               println "\nDependency graph is: \n\n" + graph.dump()
        }
    }
   
    /**
     * Scan the given list of output file properties and reconstruct the 
     * dependency graph of the input / output structure.
     * 
     * @param outputs   a List of properties objects, loaded from the properties files
     *                  saved by Bpipe as each stage executed (see {@link #saveOutputs(PipelineContext)})
     *                  
     * @return          the root node in the graph of outputs
     */
    GraphEntry computeOutputGraph(List<Properties> outputs, GraphEntry rootTree = null) {
        
        if(!outputs) {
            if(!rootTree)
                rootTree = new GraphEntry(values:[])
            return rootTree
        }
        
        // Here we are using a recursive algorithm. First we find the "edge" outputs -
        // these are ones that are created inputs that are "external" - ie. not
        // outputs of anything else in the graph.  We take those and make them into a layer
        // of the graph, connecting them to the parents that they depend on.  We then remove them
        // from the list of outputs and recursively do the same for each output we discovered, building
        // the whole graph from the original inputs through to the final outputs
        
        List allInputs = outputs*.inputs.flatten().unique()
        List allOutputs = outputs*.outputFile*.name
        
        // Find all entries with inputs that are not outputs of any other entry
        def outputsWithExternalInputs = outputs.grep { p -> ! p.inputs.any { allOutputs.contains(it) } }
        
        // If there is no tree to attach to, 
        def entries = []
        if(rootTree == null) {
            rootTree = new GraphEntry(values: outputsWithExternalInputs)
            rootTree.values.each { it.maxTimestamp = it.timestamp }
            entries << rootTree
        }
        else {
            
            // Attach each input as a child of the respective input nodes
            // in existing tree
            outputsWithExternalInputs.each { Properties out ->
                
               
                GraphEntry entry = new GraphEntry(values: [out])
                entries << entry
                
                // find all nodes in the tree which this output depends on 
                out.inputs.each { inp ->
                    GraphEntry parentEntry = rootTree.findBy { it.outputFile.name == inp } 
                    
                    if(!(entry in parentEntry.children))
                        parentEntry.children << entry
                        
                    entry.parents << parentEntry
                    
                    println "Output $out.outputFile has max timestamp = " + (entry.parents*.values*.maxTimestamp.flatten() + out.timestamp).max()
                    
                    out.maxTimestamp = (entry.parents*.values*.maxTimestamp.flatten() + out.timestamp).max()
                 }
            }
        }
        
        def result =  computeOutputGraph(outputs - outputsWithExternalInputs, rootTree)
                
        // After computing the child tree, use child information to mark this output as
        // "up to date" or "not up to date"
        // an output is "up to date" if it is 
        // a) a leaf node and it exists and newer than its inputs
        // b) a non-leaf node and ALL its children are up to date
        for(GraphEntry entry in entries) {
            List inputTimestamps = entry.parents*.values*.maxTimestamp.flatten()
            
            for(Properties p in entry.values) {
                
                // No entry is up to date if one of its inputs is newer
                println " $p.outputFile ".center(20,"-")
                if(inputTimestamps.any { println it; it  >= p.timestamp }) {
                    p.upToDate = false
                    println "$p.outputFile is older than at least 1 input"
                    continue
                }
                println "Newer than input files"

                // The entry may still not be up to date if it
                // does not exist and a downstream target needs to be updated
                if(p.outputFile.exists()) {
                    p.upToDate = true
                    continue
                }
                    
                if(entry.children) {
                    p.upToDate = entry.children.every { it.values*.upToDate.every() }
                    println "Output $p.outputFile up to date ? : $p.upToDate"
                }
                else {
                    p.upToDate = false
                    println "$p.outputFile is a leaf node and does not exist"
                }
            }
        }
        
        return result;
    }
    
    /**
     * Read all the properties files in the output folder
     * @return
     */
    List<Properties> scanOutputFolder() {
        new File(PipelineContext.OUTPUT_METADATA_DIR).listFiles().collect { 
                def p = new Properties(); 
                new FileInputStream(it).withStream { p.load(it) } 
                p.inputs = p.inputs.split(",") as List
                p.cleaned = Boolean.parseBoolean(p.cleaned)
                p.outputFile == new File(p.outputFile)
                
                // If the file exists then we should get the timestamp from there
                // Otherwise just use the timestamp recorded
                if(p.outputFile.exists()) 
                    p.timestamp = p.outputFile.lastModified()
                else 
                    p.timestamp = Long.parseLong(p.timestamp)
                
                return p
        }.sort { it.timestamp }
    }
    
    /**
     * Return a list of GraphEntry objects whose outputs
     * do not appear as inputs for any other entries. These
     * are effectively the "leaves" or final outputs of the 
     * dependency tree.
     * 
     * @param graph
     * @return
     */
    List<GraphEntry> findLeaves(GraphEntry graph) {
        def result = []
        graph.depthFirst {
            if(it.children.isEmpty())
              result << it
        }
        return result
    }
}
