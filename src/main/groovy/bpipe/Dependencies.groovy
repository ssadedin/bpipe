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
        findBy { it.outputFile == outputFile }
    }
    
    void depthFirst(Closure c) {
        
        c(this)
        
        this.children*.depthFirst(c)
    }
   
    String dump(int indent = 0) {
        String me = values*.outputFile.join(",") + (children?" => \n":"")  
        return (" " * indent) + me + children*.dump(indent+me.size()).join('\n')
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
                p.inputs = context.@inputWrapper?.resolvedInputs?.join(',')?:''
                p.outputFile = o
                p.timestamp = String.valueOf(new File(o).lastModified())
                p.fingerprint = hash
                
                log.info "Saving output file details to file $file for command " + Utils.truncnl(cmd, 20)
                file.withOutputStream { ofs ->
                    p.save(ofs, "Bpipe File Creation Meta Data")
                }
            }
        }
    }
    
    
    void cleanup() {
        
        List<Properties> outputs = scanOutputFolder()
        
//        println "Found outputs: " + outputs.collect { it.outputFile }.join('\n')
        
        // Start by scanning the output folder for dependency files
        def graph = computeOutputGraph(outputs)
        
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
            println "\nThe following intermediate files will be removed: "
            println '\t' + internalNodes*.outputFile.join('\t\n')
            println "To retain files, cancel this command and use 'bpipe preserve' to preserve the files you wish to keep\n"
        }
    }
    
   
    GraphEntry computeOutputGraph(List<Properties> outputs, GraphEntry rootTree = null) {
        
        if(!outputs) {
            if(!rootTree)
                rootTree = new GraphEntry(values:[])
            return rootTree
        }
        
        List allInputs = outputs*.inputs.flatten().unique()
        List allOutputs = outputs*.outputFile
        
        // Find all entries with inputs that are not outputs of any other entry
        def outputsWithExternalInputs = outputs.grep { p -> ! p.inputs.any { allOutputs.contains(it) } }
        
        // If there is no tree to attach to, 
        if(rootTree == null)
            rootTree = new GraphEntry(values: outputsWithExternalInputs)
        else {
            
            // Attach each input as a child of the respective input nodes
            // in existing tree
            outputsWithExternalInputs.each { out ->
                
                GraphEntry entry = new GraphEntry(values: [out])
                
                // find all nodes in the tree which this output depends on 
                out.inputs.each { inp ->
                    GraphEntry parentEntry = rootTree.findBy { it.outputFile == inp } 
                    
                    if(!(entry in parentEntry.children))
                        parentEntry.children << entry
                        
                    entry.parents << parentEntry
                }
            }
        }
        
        return computeOutputGraph(outputs - outputsWithExternalInputs, rootTree)
    }
    
    /**
     * Read all the properties files in the output folder
     * @return
     */
    List<Properties> scanOutputFolder() {
        new File(PipelineContext.OUTPUT_METADATA_DIR).listFiles().collect { 
                def p = new Properties(); 
                new FileInputStream(it).withStream { p.load(it) } 
                p.timestamp = Long.parseLong(p.timestamp)
                p.inputs = p.inputs.split(",")
                return p
        }.sort { it.timestamp }
    }
    
    List<GraphEntry> findLeaves(GraphEntry graph) {
        def result = []
        graph.depthFirst {
            if(it.children.isEmpty())
              result << it
        }
        return result
    }
}
