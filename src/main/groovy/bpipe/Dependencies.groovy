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
import groovyx.gpars.GParsPool
import groovy.time.TimeCategory;
import groovy.transform.CompileStatic;

/**
 * A node in the dependency graph representing a set of outputs
 * sharing a common set of child nodes (who depend on the outputs) and
 * inputs (parents nodes) upon which the children depend.  
 * 
 * @author ssadedin
 */
class GraphEntry {
    
    List<Properties> values
    List<GraphEntry> parents = []
    List<GraphEntry> children = []
    
    /**
     * An optional index to speed up lookups by canonical path - not populated by default,
     * but can be populated by using index()
     */
    Map<String, GraphEntry> index = null
    
    @CompileStatic
    GraphEntry findBy(Closure c) {
        if(this.values != null && values.any { c(it) })
            return this
            
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
                if(e.values) {
                    for(Properties p in e.values) {
                        indexTmp[(String)p.canonicalPath] = e
                    }
                }
            }
        }
    }
    
    List<Properties> findAllOutputsBy(Closure c) {
        
        
        List<Properties> results = []
        
        if(this.values != null)
            results.addAll(this.values.grep { c(it)})
            
        for(GraphEntry child in children) {
           results.addAll(child.findAllOutputsBy(c))
        }
        
        return results
    }
  
    
    /**
     * Search the graph for entry with given outputfile
     */
    @CompileStatic
    GraphEntry entryFor(File outputFile) {
        // In case of non-default output directory, the outputFile itself may be in a directory
        final String outputFilePath = outputFile.canonicalPath
        return entryForCanonicalPath(outputFilePath)
    }
    
    @CompileStatic
    GraphEntry entryForCanonicalPath(String canonicalPath) {
        GraphEntry entry = index?.get(canonicalPath)
        if(entry)
            return entry
        // In case of non-default output directory, the outputFile itself may be in a directory
        findBy { Properties p -> canonicalPathFor(p) == canonicalPath  }
    }
    
     /**
     * getCanonicalPath can be very slow on some systems, so 
     * this method caches them in the property file itself.
     * 
     * @param p
     * @return
     */
    @CompileStatic
    static String canonicalPathFor(Properties p) {
        synchronized(p) {
            if(p.containsKey("canonicalPath"))
                return p["canonicalPath"]        
                
            p["canonicalPath"] = ((File)p["outputFile"]).canonicalPath
        }
    }
    
    /**
     * Search the graph for properties for the given output file
     */
    Properties propertiesFor(String outputFile) { 
       // In case of non-default output directory, the outputFile itself may be in a directory
       String outputFilePath = new File(outputFile).canonicalPath
       def values = entryForCanonicalPath(outputFilePath)?.values
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
    
    void depthFirst(Closure c) {
        
        c(this)
        
        this.children*.depthFirst(c)
    }
    
    /**
     * Filter the graph so that only paths containing the specified output remain
     */
    GraphEntry filter(String output) {
        GraphEntry outputEntry = this.entryFor(new File(output))
        
        if(!outputEntry)
            return null
        
        GraphEntry result = outputEntry.clone()
        
        GraphEntry root = null
        
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
     * Divide the outputs into groups depending on their 
     * inputs.
     * @return
     */
    def groupOutputs(List<Properties> outputs = null) {
        def outputGroups = [:]
        if(outputs == null)
            outputs = this.values
            
        for(def o in outputs) {
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
        
        def inputs = children*.values*.inputs.flatten()
        if(!inputs)
            inputs = ["<no inputs>"]
                  
        String inputValue = inputs.join('\n') + ' => \n'
        return inputValue + dumpChildren(inputs.collect {it.size()}.max())
        
//        return dumpChildren(0)
    }
   
    /**
     * Render this graph entry as a tree
     */
    String dumpChildren(int indent = 0, List<Properties> filter = null) {
        
       
        def valuesToDump = filter != null ? filter : values
        
        def names = values*.outputFile*.name
        
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
        return me + filteredChildren*.dumpChildren(indent+(names.collect{it.size()}.max()?:0)).join('\n')
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
     * List of output files created in *this run*
     */
    List<String> outputFilesGenerated = []
    
    /**
     * Return true iff all the outputs and downstream outputs for which
     * they are dependencies are present and up to date.
     * 
     * @param outputs
     * @param inputs
     * @return  true iff file is newer than all inputs, but older than all 
     *          non-up-to-date outputs
     */
    boolean checkUpToDate(def outputs, def inputs) {
        
        inputs = Utils.box(inputs)
        
        // If there are no outputs then by definition, they are all up to date
        if(!outputs)
            return true
        
        // If the outputs are created from nothing (no inputs)
        // then they are up to date as long as they exist
        if(!inputs)  {
            return outputs.collect { new File(it) }.every { it.exists() }
        }
            
        // The most obvious case: all the outputs exist and are newer than 
        // the inputs. We can return straight away here
        List<File> older = Utils.findOlder(outputs,inputs)
        if(!older) {
            log.info "No missing / older files from inputs: $outputs are up to date"
            return true
        }
        else
        if(older.any { it.exists() }) { // If any of the older files exist then we have no choice but to rebuild them
            log.info "Not up to date because these files exist and are older than inputs: " + older.grep { it.exists() }
            return false
        }
        else {
            log.info "Found these missing / older files: " + older
        }
        
        GraphEntry graph = this.getOutputGraph()
        
        def outDated = older.collect { it.canonicalPath }.grep { out ->
             def p = graph.propertiesFor(out); 
             if(!p || !p.cleaned)  {
                 if(!p)
                     log.info "Output properties file is not available for $out: assume NOT cleaned up"
                 else
                     log.info "Output properties are available, indicating file was NOT cleaned up"
                     
                 return true 
             }
             else {
                 log.info "File $out has output properties available: upToDate=$p.upToDate"
                 return !p.upToDate
             }
        }
        
        if(!outDated) {
            log.info "All missing files are up to date"
            return true
        }
        else {
            log.info "Some files are outdated : $outDated"
            return false
        }
    }
    
    /**
     * Check either a single file passed as a string or a list
     * of files passed as a collection.  Throws an exception
     * if any of them are missing and cannot be accounted for
     * in an output property file.
     *
     * @param f     a String or collection of Strings representing file names
     */
    void checkFiles(def fileNames, type="input") {
        
        log.info "Checking $type (s) " + fileNames
        
        GraphEntry graph = this.getOutputGraph()
        List missing = Utils.box(fileNames).grep { String.valueOf(it) != "null" }.collect { new File(it.toString()) }.grep { File f ->
            
            log.info " Checking file $f"
            if(f.exists())
                return false
                
            Properties p = graph.propertiesFor(f.path)
            if(!p) {
                log.info "There are no properties for $f.path and file is missing"
                return true
            }
            
            if(p.cleaned) {
                log.info "File $f.path [$type] does not exist but has a properties file indicating it was cleaned up"
                return false
            }
            else {
                log.info "File $f.path [$type] does not exist, has a properties file indicating it is not cleaned up"
                return true
            }
        }
       
        if(missing)
            throw new PipelineError("Expected $type file ${missing[0]} could not be found")
    }

    
    synchronized GraphEntry getOutputGraph() {
        if(this.outputGraph == null) {
            List<Properties> propertiesFiles = scanOutputFolder()
            this.outputGraph = computeOutputGraph(propertiesFiles)
            this.outputGraph.index(propertiesFiles.size()*2)
        }
        return this.outputGraph
    }
    
    void reset() {
        this.outputGraph = null
    }
    
    /**
     * For each output file created in the context, save information
     * about it such that it can be reliably loaded by this same stage
     * if the pipeline is re-executed.
     */
    synchronized void saveOutputs(PipelineContext context, List<File> oldFiles, Map<File,Long> timestamps, List<String> inputs) {
        GraphEntry root = getOutputGraph()
        
        // Get the full branch path of this pipeline stage
        def pipeline = Pipeline.currentRuntimePipeline.get()
        String branchPath = pipeline.branchPath.join("/")
        
        context.trackedOutputs.each { String id, Command command ->
            
            String cmd = command.command
            List<String> outputs = command.outputs
            for(def o in outputs) {
                o = Utils.first(o)
                if(!o)
                    continue
                    
                File file = context.getOutputMetaData(o)
                
                // Check if the output file existed before the stage ran. If so, we should not save meta data, as it will already be there
                if(timestamps[oldFiles.find { it.name == o }] == new File(o).lastModified()) { 
                    // There are a couple of reasons the file might have the same time stamp
                    // It might be that the outputs were up to date, so didn't need to be modified
                    // Or it could be that they weren't really produced at all - the user lied to us
                    // with a 'produce' statement. Even if they did, we don't want to save the 
                    // meta data file because it will cause a cyclic dependency.
                    
                    // An exception to the rule: if the met data file didn't exist at all then
                    // we DO create the meta data because it's probably missing (as in, user copied their files
                    // to new directory, upgraded Bpipe, something similar).
                    if(file.exists() || this.outputFilesGenerated.contains(o) || Utils.box(context.@input).contains(o)) {
                        log.info "Ignoring output $o because it was not created or modified by stage ${context.stageName}"
                        continue
                    }
                }
                
                this.outputFilesGenerated << o
                
                Properties p = new Properties()
                if(file.exists()) {
                    p = readOutputPropertyFile(file)?:p
                }
                
                String hash = Utils.sha1(cmd+"_"+o)

                p.command = cmd
                p.commandId = command.id
                p.branchPath = branchPath
                p.stageName = context.stageName
                
                def allInputs = context.getResolvedInputs()
                
                log.info "Context " + context.hashCode() + " for stage " + context.stageName + " has resolved inputs " + allInputs
                
                p.propertyFile = file.name
                p.inputs = allInputs.join(',')?:''
                p.outputFile = o
                p.basePath = Runner.runDirectory
                p.canonicalPath = new File(o).canonicalPath
                p.fingerprint = hash
                
                p.tools = context.documentation["tools"].collect { name, Tool tool -> tool.fullName + ":"+tool.version }.join(",")
                
                if(!p.containsKey("cleaned"))
                    p.cleaned = false
                
                p.preserve = String.valueOf(context.preservedOutputs.contains(o))
                p.intermediate = String.valueOf(context.intermediateOutputs.contains(o))
                if(context.accompanyingOutputs.containsKey(o))
                    p.accompanies = context.accompanyingOutputs[o]
                    
                p.startTimeMs = command.startTimeMs
                p.createTimeMs = command.createTimeMs
                p.stopTimeMs = command.stopTimeMs
                
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
        
        if(p.outputFile instanceof File)
            p.outputFile = p.outputFile.path
            
        File outputFile = new File(p.outputFile)
        
        if(outputFile.exists())    
            p.timestamp = String.valueOf(outputFile.lastModified())
        else
        if(!p.timestamp)
            p.timestamp = "0"
        else
            p.timestamp = String.valueOf(p.timestamp)
            
        p.createTimeMs = p.createTimeMs ? String.valueOf(p.createTimeMs) : "0"
        p.stopTimeMs = p.stopTimeMs ? String.valueOf(p.stopTimeMs) : "0"
            
        // upToDate and maxTimestamp are "virtual" properties, computed at load time
        // they should not be stored
        if(p.containsKey('upToDate'))
            p.remove('upToDate')
            
        if(p.containsKey('maxTimestamp'))
            p.remove('maxTimestamp')
            
        log.info "Saving output file details to file $file for command " + Utils.truncnl(p.command, 20)
        
        for(k in p.keySet()) {
            p[k] = String.valueOf(p[k])
        }
        
        file.withOutputStream { ofs ->
            p.save(ofs, "Bpipe Output File Meta Data")
        }
    }
    
    
    /**
     * Computes the files that are created as non-final products of the pipeline and 
     * shows them to the user, offering to delete them.
     * 
     * @param arguments     List of files to clean up. If empty, acts as wildcard
     */
    void cleanup(List arguments) {
        
        log.info "Executing cleanup with arguments $arguments"
        
        List<Properties> outputs = scanOutputFolder() 
        
        // Start by scanning the output folder for dependency files
        def graph
        Thread t = new Thread({
          graph = computeOutputGraph(outputs)
          graph.index(outputs.size()*2)
        })
        t.start()
        
        long startTimeMs = System.currentTimeMillis()
        while(graph == null) {
            Thread.sleep(500)
            if(System.currentTimeMillis() - startTimeMs > 5000) {
                println "Please wait ... computing dependences ..."
                break
            }
        }
        t.join()
        
        log.info "\nOutput graph is: \n\n" + graph.dump()
        
        // Identify the leaf nodes
        List leaves = findLeaves(graph)
        
        //  Filter out leaf nodes from this list if they are explicitly specified as intermediate files
        leaves.removeAll { it.values.every { it.intermediate } }
        
        // Find all the nodes that exist and match the users specs (or, if no specs, treat as wildcard)
        List internalNodes = (outputs - leaves*.values.flatten()).grep { p ->
            if(!p.outputFile.exists()) {
                log.info "File $p.outputFile doesn't exist so can't be cleaned up"
                return false
            }
             
            if(p.preserve) {
                log.info "File $p.outputFile is preserved, so can't be cleaned up"
                return false
            }
            
            if(arguments.isEmpty())
                return true 
                
            if(arguments.contains(p.outputFile.name) || arguments.contains(p.outputPath))  
                return true
            else {
                log.info "File $p.outputFile doesn't match the arguments $arguments, so can't be cleaned up"
                return false
            }
        }
        
        List<String> internalNodeFileNames = internalNodes*.outputFile*.name
        List<String> accompanyingOutputFileNames = []
        
        // Add any "accompanying" outputs for the outputs that would be cleaned up
        graph.depthFirst { GraphEntry g ->
            def accompanyingOutputs = g.values.grep { Properties p -> 
                p.accompanies && p.accompanies in internalNodeFileNames 
            }
            internalNodes += accompanyingOutputs
            accompanyingOutputFileNames += accompanyingOutputs*.outputFile*.name
        }
        
        if(!internalNodes) {
            println """
                No ${arguments?'matching':'existing'} files were found as eligible outputs to clean up.
                
                You may mark a file as disposable by using @intermediate annotations
                in your Bpipe script.
            """.stripIndent()
        }
        else {
            // Print out each leaf
            println "\nThe following intermediate files will be removed:\n"
            println '\t' + internalNodeFileNames.grep{it!=null}*.plus('\n\t').join("") + accompanyingOutputFileNames*.plus(' (*)\n\t').join("")
            
            if(accompanyingOutputFileNames)
                println "(*) These files were specified to accompany other files to be deleted\n"
                
            println "To retain files, cancel this command and use 'bpipe preserve' to preserve the files you wish to keep"
            print "\n"
            
            def answer 
            def msg = "Remove/trash these files? (y/t/n): "
            if(Config.userConfig.prompts.containsKey("handler")) {
                answer = Config.userConfig.prompts.handler(msg)
            }
            else {
                print(msg)
                answer = System.in.withReader { it.readLine() } 
            }
            int removedCount = 0
            long removedSize = 0
            if(answer == "y") {
                internalNodes.each { ++removedCount; removedSize += removeOutputFile(it) }
                println "Deleted $removedCount files, saving " + ((float)removedSize)/1024.0/1024.0 + " MB of space"
                
            }
            else
            if(answer == "t") {
                internalNodes.each { ++removedCount; removedSize +=removeOutputFile(it,true) }
                println "Moved $removedCount files consuming " + ((float)removedSize)/1024.0/1024.0 + " MB of space"
            }
        }
    }
    
    long removeOutputFile(Properties outputFileProperties, boolean trash=false) {
        outputFileProperties.cleaned = 'true'
        saveOutputMetaData(outputFileProperties)
        File outputFile = outputFileProperties.outputFile
        long outputSize = outputFile.size()
        if(trash) {
            File trashDir = new File(".bpipe/trash")
            if(!trashDir.exists())
                trashDir.mkdirs()
            
            if(!outputFile.renameTo(new File(".bpipe/trash", outputFile.name))) {
              log.warning("Failed to move output file ${outputFile.absolutePath} to trash folder")
              System.err.println "Failed to move file ${outputFile.absolutePath} to trash folder"
              return 0
            }
        }
        else
        if(!outputFile.delete()) {
            log.warning("Failed to delete output file ${outputFile.absolutePath}")
            System.err.println "Failed to delete file ${outputFile.absolutePath}"
            return 0
        }
        return outputSize
    }
    
    /**
     * Display a dump of the dependency graph for the given files
     * 
     * @param args  list of file names to display dependencies for
     */
    void queryOutputs(def args) {
        // Start by scanning the output folder for dependency files
        List<Properties> outputs = scanOutputFolder()
        def graph = computeOutputGraph(outputs)
        
        if(args) {
            for(String arg in args) {
                GraphEntry filtered = graph.filter(arg)
                if(!filtered) {
                    System.err.println "\nError: cannot locate output $arg in output dependency graph"
                    continue
                }
                println "\n" + " $arg ".center(Config.config.columns, "=")  + "\n"
                println "\n" + filtered.dump()
                
                
               Properties p = graph.propertiesFor(arg)
               
               
               String duration = "Unknown"
               String pendingDuration = "Unknown"
               
               if(p.stopTimeMs > 0) {
                   duration = TimeCategory.minus(new Date(p.stopTimeMs),new Date(p.startTimeMs)).toString()
                   pendingDuration = TimeCategory.minus(new Date(p.startTimeMs),new Date(p.createTimeMs)).toString()
               }
               
               println """
                   Created:             ${new Date(p.timestamp)}
                   Pipeline Stage:      ${p.stageName?:'Unknown'}
                   Pending Time:        ${pendingDuration}
                   Running Time:        ${duration}
                   Inputs used:         ${p.inputs.join(',')}
                   Command:             ${p.command}
                   Preserved:           ${p.preserve?'yes':'no'}
                   Intermediate output: ${p.intermediate?'yes':'no'}
               """.stripIndent()
               
               println("\n" + ("=" * Config.config.columns))
            }
        }
        else {
               println "\nDependency graph is: \n\n" + graph.dump()
        }
    }
    
    void preserve(def args) {
        List<Properties> outputs = scanOutputFolder()
        int count = 0
        for(def arg in args) {
            Properties output = outputs.find { it.outputPath == arg }
            if(!output) {
                System.err.println "\nERROR: Cannot locate file $arg as a tracked output"
                continue
            }
            
            if(!output.preserve) {
                output.preserve = true
                this.saveOutputMetaData(output)
                ++count
            }
            else {
                println "\nOutput $output.outputPath is already preserved"
            }
        }
        println "\n$count files were preserved"
    }
   
    /**
     * Scan the given list of output file properties and reconstruct the 
     * dependency graph of the input / output structure, including flags
     * indicating which output files are 'up to date' based in input file
     * timestamps, existence of the file and presence of child outputs that
     * need the files as dependencies.
     * <p>
     * The algorithm below operates recursively: each layer of the tree is figured out
     * by finding the nodes that have no inputs in the list and removing them, and then 
     * working out the child tree through a recursive call, and then adding the identified
     * input nodes as parents to that tree. So this works as a forward scan through
     * the dependency tree (inputs => final outputs).
     * <p>
     * However computing the up-to-date flags works as a backwards scan from the outputs
     * backwards up to the initial inputs. This is achieved in the same algorithm below
     * by placing it after the recursive call. So the overalls tructure is
     * <ol><li>Compute inputs in layer
     *     <li>Calculate tree below layer (recursive call)
     *     <li>Compute up-to-date flag for layer
     * 
     * @param outputs   a List of properties objects, loaded from the properties files
     *                  saved by Bpipe as each stage executed (see {@link #saveOutputs(PipelineContext)})
     *                  
     * @return          the root node in the graph of outputs
     */
    GraphEntry computeOutputGraph(List<Properties> outputs, GraphEntry rootTree = null, GraphEntry topRoot=null, List handledOutputs=[]) {
        
        // Special case: no outputs at all
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
        List allOutputs = outputs*.outputPath
        
        // Find all entries with inputs that are not outputs of any other entry
        def outputsWithExternalInputs = outputs.grep { p -> ! p.inputs.any { allOutputs.contains(it) } }
        
        log.info "External inputs: " + outputsWithExternalInputs*.inputs + " for outputs " + outputsWithExternalInputs*.outputPath
        
        handledOutputs.addAll(outputsWithExternalInputs)
        
        // find groups of outputs (ie. ones that belong in the same branch of the tree)
        // If there is no tree to attach to, make one
        def entries = []
        def outputGroups = [:]
        def createdEntries = [:]
        if(rootTree == null) {
            
            rootTree = new GraphEntry()
            outputGroups = rootTree.groupOutputs(outputsWithExternalInputs)
            outputGroups.each { key,outputGroup ->
                GraphEntry childEntry = new GraphEntry(values: outputGroup)
                
                
                childEntry.values.each { it.maxTimestamp = it.timestamp }
                createdEntries[key] = childEntry 
                childEntry.parents << rootTree
                rootTree.children << childEntry
                entries << childEntry
            }
            
            entries << rootTree
        }
        else {
               
            // Attach each output as a child of the respective input nodes
            // in existing tree
            outputsWithExternalInputs.each { Properties out ->
                
                GraphEntry entry = new GraphEntry(values: [out])
                log.info "New entry for ${out.outputPath}"
                entries << entry
                createdEntries[out.outputPath] = entry
                outputGroups[out.outputPath] = entry.values
                
                // find all nodes in the tree which this output depends on 
                out.inputs.each { inp ->
                    GraphEntry parentEntry = topRoot.findBy { it.outputPath == inp } 
                    if(parentEntry) {
                        if(!(entry in parentEntry.children))
                          parentEntry.children << entry
                        entry.parents << parentEntry
                    }
                    else
                        log.info "Dependency $inp for $out.outputPath is external root input"
                }
               
                def dependenciesOnParents = entry.parents*.values.flatten().grep { out.inputs.contains(it.outputPath) }
                out.maxTimestamp = (dependenciesOnParents*.maxTimestamp.flatten() + out.timestamp).max()
                    
                log.info "Maxtimestamp for $out.outputFile = $out.maxTimestamp"
                
            }
        }
        
        if(topRoot == null)
            topRoot = rootTree
        
        if(!outputsWithExternalInputs)
            throw new PipelineError("Unable to identify any outputs with only external inputs in: " + outputs*.outputFile.join('\n') + "\n\nThis may indicate a circular dependency in your pipeline")
        
//        log.info "There are ${outputGroups.size()} output groups: ${outputGroups.values()*.outputPath}"
        log.info "There are ${outputGroups.size()} output groups"
        log.info "Subtracting ${outputsWithExternalInputs.size()} remaining outputs from ${outputs.size()} total outputs"
        List remainingOutputs = outputs.clone()
        remainingOutputs.removeAll(outputsWithExternalInputs)
        outputGroups.each { key, outputGroup ->
//          log.info "Remaining outputs: " + remainingOutputs*.outputPath
            computeOutputGraph(remainingOutputs, createdEntries[key], topRoot, handledOutputs)
//          log.info "Handled outputs: " + handledOutputs*.outputPath + " Hashcode  " + handledOutputs.hashCode()
            remainingOutputs.removeAll(handledOutputs)
            log.info "There are ${remainingOutputs.size()} remaining outputs"
        }
                
        // After computing the child tree, use child information to mark this output as
        // "up to date" or "not up to date"
        // an output is "up to date" if it is 
        // a) it exists and newer than its inputs
        // or
        // b) it doesn't exist but is a non-leaf node and all its children are 'up to date'
        // Here 'leaf node' means a final output as opposed to something that is only used
        // as an intermediate step in calculating a final output.
        for(GraphEntry entry in entries) {
            
            List inputValues = entry.parents*.values.flatten().grep { it != null }
            
            for(Properties p in entry.values) {
                
                // No entry is up to date if one of its inputs is newer
                log.info " $p.outputFile / entry ${entry.hashCode()} ".center(40,"-")
//                log.info "Parents: " + entry.parents?.size()
//                
//                log.info "Values: " + entry.parents*.values
//                
                def newerInputs = findNewerInputs(p, inputValues)
                
                if(newerInputs) {
                    p.upToDate = false
                    log.info "$p.outputFile is older than inputs " +
                       (newerInputs.collect { it.outputFile.name + ' / ' + it.timestamp + ' / ' + it.maxTimestamp + ' vs ' + p.timestamp })
                    continue
                }
                
                log.info "$p.outputPath is newer than input files"

                // The entry may still not be up to date if it
                // does not exist and a downstream target needs to be updated
                if(p.outputFile.exists()) {
                    p.upToDate = true
                    continue
                }
                log.info "$p.outputFile does not exist"
                
                // If the file is missing but wasn't removed by us? Consider it not up to date
                if(!p.cleaned) {
                    log.info "$p.outputFile removed but not by bpipe"
                    p.upToDate = false
                    continue
                }
                    
                log.info "$p.outputFile was cleaned"
                log.info "Checking  " + entry.children*.values*.outputPath + " from " + entry.children.size() + " children"
                if(entry.children) {
                    
                    List<GraphEntry> outOfDateChildren = entry.children.grep { c -> c.values.grep { !it.upToDate }*.outputPath  }.flatten()
                    
//                    p.upToDate = entry.children.every { it.values*.upToDate.every() }
                    p.upToDate = outOfDateChildren.empty
                    
                    if(!p.upToDate)
                        log.info "Output $p.outputFile is not up to date because ${outOfDateChildren*.values*.outputFile} are not up to date"
                    else
                        log.info "Output $p.outputFile is up to date because all its children are"
                }
                else {
                    p.upToDate = false
                    log.info "$p.outputFile is not up to date because it is a leaf node and does not exist"
                }
            }
        }
        
        log.info "Finished Output Graph".center(30,"=")
        
        return rootTree
    }
    
    List<Properties> findNewerInputs(Properties p, List<Properties> inputValues) {
        inputValues.grep { Properties inputProps ->
                    
            if(!p.inputs.contains(inputProps.outputPath)) // Not an input used to produce this output
                return false
                        
            log.info "Checking timestamp of $p.outputFile vs input $inputProps.outputPath"
            if(inputProps?.maxTimestamp < p.timestamp) { // inputs unambiguously older than output 
                return false
            }
                    
            if(inputProps?.maxTimestamp > p.timestamp) // inputs unambiguously newer than output
                return true
            
            // Problem: many file systems only record timestamps at a very coarse level. 
            // 1 second resolution is common, but even 1 minute is possible. In these cases
            // commands that run fast enough produce output files that have equal timestamps
            // To differentiate these cases we check the start and stop times of the 
            // actual commands that produced the file
            if(!inputProps.stopTimeMs)
                return true // we don't know when the command that produced the input finished
                            // so have to assume the input could have come after
                
            if(!p.createTimeMs) 
                return false // don't know when the command that produced this output started,
                             // so have to assume the command that made the input might have
                             // done it after
                
            // Return true if the command that made the input stopped after the command that 
            // created the output. ie: that means the input is newer, even though it has the
            // same timestamp
            return inputProps.stopTimeMs >= p.createTimeMs
       } 
    }
    
    /**
     * Read all the properties files in the output folder
     * @return
     */
    List<Properties> scanOutputFolder() {
        int concurrency = (Config.userConfig?.outputScanConcurrency)?:5
        List result = []
        Utils.time("Output folder scan (concurrency=$concurrency)") {
            GParsPool.withPool(concurrency) { 
                List<File> files = 
                               new File(PipelineContext.OUTPUT_METADATA_DIR).listFiles()
                               ?.toList()
                                .grep { !it.name.startsWith(".") && !it.isDirectory() } // ignore files starting with ., 
                                                                   // added as a convenience because I occasionally
                                                                   // edit files in output folder when debugging and it causes
                                                                   // Bpipe to fail!
                                
                if(!files)
                    return []
                result.addAll(files.collectParallel { readOutputPropertyFile(it) }.grep { it != null }.sort { it.timestamp })
            }
        }
        return result
    }
    
    /**
     * Read the given file as an output meta data file, parsing
     * various expected properties to native form from string values.
     */
    Properties readOutputPropertyFile(File f) {
        log.info "Reading property file $f"
        def p = new Properties();
        new FileInputStream(f).withStream { p.load(it) }
        p.inputs = p.inputs?p.inputs.split(",") as List : []
        p.cleaned = p.containsKey('cleaned')?Boolean.parseBoolean(p.cleaned) : false
        if(!p.outputFile)  {
            log.warning("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Properties are: " + p)
            return null
        }
            
        p.outputFile = new File(p.outputFile)
        
        // Normalizing the slashes in the path is necessary for Cygwin compatibility
        p.outputPath = p.outputFile.path.replaceAll("\\\\","/")
        
        // If the file exists then we should get the timestamp from there
        // Otherwise just use the timestamp recorded
        if(p.outputFile.exists())
            p.timestamp = p.outputFile.lastModified()
        else
            p.timestamp = Long.parseLong(p.timestamp)

        // The properties file may have a cached version of the "canonical path" to the
        // output file. However this is an absolute path, so we can only use it if the
        // base directory is still the same as when this property file was saved.
        if(!p.containsKey("basePath") || (p["basePath"] != Runner.runDirectory)) {
            p.remove("canonicalPath")
        }
        
        if(!p.containsKey('preserve'))
            p.preserve = 'false'
            
        if(!p.containsKey('intermediate'))
            p.intermediate = 'false'
            
        p.preserve = Boolean.parseBoolean(p.preserve)
        p.intermediate = Boolean.parseBoolean(p.intermediate)
        p.commandId = (p.commandId!=null)?p.commandId:"-1"
        p.startTimeMs = (p.startTimeMs?:0).toLong()
        p.createTimeMs = (p.createTimeMs?:0).toLong()
        p.stopTimeMs = (p.stopTimeMs?:0).toLong()
        
        if(!p.containsKey("tools")) {
            p.tools = ""
        }
        
        if(!p.containsKey("branchPath")) {
            p.branchPath = ""
        }
        
        if(!p.containsKey("stageName")) {
            p.stageName = ""
        }
        return p
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
        def outputs = [] as Set
        graph.depthFirst {
            if(it.children.isEmpty() && it.values*.outputPath.every { !outputs.contains(it) } ) {
              result << it
              outputs += it.values*.outputPath
            }
        }
        return result
    }
}
