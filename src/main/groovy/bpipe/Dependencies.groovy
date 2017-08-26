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

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.hazelcast.impl.base.FactoryAwareNamedProxy

/**
 * A node in the dependency graph representing a set of outputs
 * sharing a common set of child nodes (who depend on the outputs) and
 * inputs (parents nodes) upon which the children depend.  
 * 
 * @author ssadedin
 */
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
                    for(OutputMetaData p in e.values) {
                        indexTmp[(String)p.canonicalPath] = e
                    }
                }
            }
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
    GraphEntry entryForCanonicalPath(String canonicalPath) {
        GraphEntry entry = index?.get(canonicalPath)
        if(entry)
            return entry
        // In case of non-default output directory, the outputFile itself may be in a directory
        findBy { OutputMetaData p -> 
            canonicalPathFor(p) == canonicalPath  
        }
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
        synchronized(p) {
            if(p.canonicalPath != null)
                return p.canonicalPath
                
            p.canonicalPath = Utils.canonicalFileFor(String.valueOf(p.outputFile)).path
        }
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
    def groupOutputs(List<OutputMetaData> outputs = null) {
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
        return inputValue + dumpChildren(Math.min(20,inputs.collect {it.size()}.max()))
        
//        return dumpChildren(0)
    }
   
    /**
     * Render this graph entry as a tree
     */
    String dumpChildren(int indent = 0, List<OutputMetaData> filter = null) {
        
       
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
        int maxIndent = 20
        return me + filteredChildren*.dumpChildren(indent+Math.min((names.collect{it.size()}.max()?:0), maxIndent)).join('\n')
    }
    
    String toString() {
        "GraphEntry [outputs=" + values*.outputPath + ", inputs="+values*.inputs + " with ${children.size()} children]"
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
     * The file to which the output graph is saved between runs
     */
    final static File OUTPUT_GRAPH_CACHE_FILE = new File(".bpipe/outputs/outputGraph2.ser")
    
    ReentrantReadWriteLock outputGraphLock = new ReentrantReadWriteLock()
    
    GraphEntry outputGraph
    
    /**
     * List of output files created in *this run*
     */
    List<String> outputFilesGenerated = []
    
    /**
     * List of files whose timestamps are overridden, so that their real
     * timestamp is ignored until they are recreated. This is used by the
     * remake function.
     */
    Map<String,Long> overrideTimestamps = [:]
    
    /**
     * Return true iff all the outputs and downstream outputs for which
     * they are dependencies are present and up to date.
     * 
     * @param outputs
     * @param inputs
     * @return  true iff file is newer than all inputs, but older than all 
     *          non-up-to-date outputs
     */
    List<PipelineFile> getOutOfDate(List<PipelineFile> originalOutputs, def inputs) {
        
        List outputs = originalOutputs.unique(false)

        assert inputs.every { it instanceof PipelineFile }
        
        inputs = Utils.box(inputs)

        if(outputs.contains("align/NA12879.bam")) 
            println "debug output"
        
        List outOfDateOutputs = []
        
        // If there are no outputs then by definition, they are all up to date
        if(!outputs)
            return outOfDateOutputs
        
        // Outputs are forcibly out of date if specified by remake
        if(!overrideTimestamps.isEmpty()) { 
            List forcedOutOfDate = outputs.grep { o ->
                String path = o instanceof File ? o.path : o
                File cf = Utils.canonicalFileFor(path)
                Long ts = overrideTimestamps[cf.path]
                
                // If the timestamps are the same then we consider the timestamp overridden
                ts == cf.lastModified()
            }
            outOfDateOutputs.addAll(forcedOutOfDate)
        }
        
        // If the outputs are created from nothing (no inputs)
        // then they are up to date as long as they exist
        if(!inputs)  {
            outOfDateOutputs.addAll(outputs.collect { it instanceof PipelineFile ? it : new File(it) }.grep { !it.exists() })
            return outOfDateOutputs
        }
            
        // The most obvious case: all the outputs exist and are newer than 
        // the inputs. We can return straight away here
        List<Path> older = Utils.findOlder(outputs,inputs)
        if(!older) {
            log.info "No missing / older files from inputs: $outputs are up to date"
            outOfDateOutputs.addAll(older)
            return outOfDateOutputs
        }
        else
        if(older.any { Files.exists(it) }) { // If any of the older files exist then we have no choice but to rebuild them
            log.info "Not up to date because these files exist and are older than inputs: " + older.grep { Files.exists(it) }
            outOfDateOutputs.addAll(older.grep{ Files.exists(it)})
            return outOfDateOutputs
        }
        else {
             log.info "These missing / older files require check for cleanup: " + older
        }
        
        GraphEntry graph = this.getOutputGraph()
        
        def outDated 
        outputGraphLock.readLock().lock()
        try {
            outDated = older.collect { it.normalize().toString() }.grep { String out ->
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
        }
        finally {
            outputGraphLock.readLock().unlock()
        }
        
        outOfDateOutputs.addAll(outDated)
        if(!outOfDateOutputs) {
            log.info "All missing files are up to date"
        }
        else {
            log.info "Some files are outdated : $outOfDateOutputs"
        }
        
        return outOfDateOutputs
    }
    
    /**
     * Check either a single file passed as a string or a list
     * of files passed as a collection.  Throws an exception
     * if any of them are missing and cannot be accounted for
     * in an output property file.
     *
     * @param f     a String or collection of Strings representing file names
     */
    void checkFiles(List<PipelineFile> files, Aliases aliases, type="input") {
        
        log.info "Checking ${files.size()} $type (s) " 
        
        GraphEntry graph = this.getOutputGraph()
        
        List<PipelineFile> filesToCheck = files.collect { PipelineFile f -> f.path in aliases ? f.newName(aliases[f.path]) : f }
        
        outputGraphLock.readLock().lock()
        
        Closure checkInput = { PipelineFile f ->
            OutputMetaData p = graph.propertiesFor(f.path)
            f.isMissing(p, type)
        }
        
        try { 
            List missing
            if(filesToCheck.size()>40) {
                log.info "Using parallelized file check for checking ${filesToCheck.size()} inputs ..."
                int concurrency = (Config.userConfig?.outputScanConcurrency)?:5
                missing = GParsPool.withPool(concurrency) { 
                    filesToCheck.grepParallel(checkInput) 
                }
            }
            else {
                missing = filesToCheck.grep(checkInput)
            }
            
            if(missing)
                throw new PipelineError("Expected $type file ${missing[0]} could not be found")
        }
        finally {
            outputGraphLock.readLock().unlock()
        }
    }
    
    synchronized saveOutputGraphCache() {
		
		if(!OUTPUT_GRAPH_CACHE_FILE.parentFile.exists()) {
			OUTPUT_GRAPH_CACHE_FILE.parentFile.mkdirs()
		}
		
        OUTPUT_GRAPH_CACHE_FILE.withObjectOutputStream { oos ->
            oos << outputGraph
        }
    }
    
    /**
     * Attempt to load the output graph from previously saved serialized form, if it is available
     * @return
     */
    synchronized preloadOutputGraph() {
        if(OUTPUT_GRAPH_CACHE_FILE.exists()) {
            Utils.time("Read cached output graph") {
                outputGraph = OUTPUT_GRAPH_CACHE_FILE.withObjectInputStream { it.readObject() }
            }
        }
        else {
            log.info "No cached output graph ($OUTPUT_GRAPH_CACHE_FILE.name) available: will be computed from property files"
        }
    }
    
    synchronized GraphEntry getOutputGraph() {
        if(this.outputGraph == null) {
            List<OutputMetaData> outputMetaDataFiles = scanOutputFolder()
            if(outputMetaDataFiles.isEmpty()) {
                // Seed the output graph with a first stage of inputs that are
                // essentially dummy "outputs" created from the pipeline raw inputs
                def inps = Pipeline.rootPipeline?.stages?.getAt(0)?.context?.@input
                if(inps == null)
                    inps = []

                outputMetaDataFiles = Utils.box(inps).collect { PipelineFile inputFile ->
                    String inputFileValue = String.valueOf(inputFile)
                    OutputMetaData omd = new OutputMetaData(inputFile)
                    omd.timestamp = new File(inputFileValue).lastModified()
                    omd.outputFile = inputFile.toPath()
                    omd
                }
            }
            this.outputGraph = computeOutputGraph(outputMetaDataFiles)
            this.outputGraph.index(outputMetaDataFiles.size()*2)
        }
        return this.outputGraph
    }
    
    void reset() {
        this.outputGraph = null
    }
    
    void flushOutputGraphCache() {
        if(OUTPUT_GRAPH_CACHE_FILE.exists()) {
            log.info "Deleting output graph cache file $OUTPUT_GRAPH_CACHE_FILE.absolutePath"
            OUTPUT_GRAPH_CACHE_FILE.delete()
        }
    }
    
    /**
     * For each output file created in the context, save information
     * about it such that it can be reliably loaded by this same stage
     * if the pipeline is re-executed.
     */
    synchronized void saveOutputs(PipelineStage stage, Map<String,Long> timestamps, List<String> inputs) {

        PipelineContext context = stage.context
        
        GraphEntry root = getOutputGraph()
        
        // Get the full branch path of this pipeline stage
        def pipeline = Pipeline.currentRuntimePipeline.get()
        String branchPath = pipeline.branchPath.join("/")
        String outputDir = stage.context.outputDirectory
        OutputDirectoryWatcher odw = OutputDirectoryWatcher.getDirectoryWatcher(outputDir)
        
        context.trackedOutputs.each { String id, Command command ->
            
            String cmd = command.command
            List<PipelineFile> outputs = command.outputs
            for(def o in outputs) {
                o = Utils.first(o)
                if(!o)
                    continue
                    
                OutputMetaData p = new OutputMetaData(o)
                
                // Check if the output file existed before the stage ran. If so, we should not save meta data, as it will already be there
                // Note: saving two meta data files for the same output can produce a circular dependency (exception
                // when loading output graph).
                if(!timestamps.containsKey(o)) { 
                    if(p.exists() || this.outputFilesGenerated.contains(o) || Utils.box(context.@input).contains(o)) {
                        log.info "Not overwriting meta data for $o because it was not created or modified by stage ${context.stageName}"
                        continue
                    }
                }
                
                this.outputFilesGenerated << o
                
                if(p.exists()) {
                    p.read()
                }

                p.setPropertiesFromCommand(o, command, stage, branchPath)
                saveOutputMetaData(p)
            }
        }
    }
    
    int numberOfGeneratedOutputs() {
        return this.outputFilesGenerated.size();
    }
    
    void saveOutputMetaData(OutputMetaData p) {
        flushOutputGraphCache()
        p.save()
        
        // If there is a cached outputgraph, update it
        if(outputGraph != null) {
            outputGraphLock.writeLock().lock()
            try {
                log.info "Adding file $p.outputPath to output graph"
                computeOutputGraph([p], outputGraph, outputGraph, [],false)
            }
            finally {
                outputGraphLock.writeLock().unlock()
            }
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
        
        List<OutputMetaData> outputs = scanOutputFolder() 
        
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
        
        // log.info "\nOutput graph is: \n\n" + graph.dump()
        
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
            def accompanyingOutputs = g.values.grep { OutputMetaData p -> 
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
    
    long removeOutputFile(OutputMetaData outputFileOutputMetaData, boolean trash=false) {
        outputFileOutputMetaData.cleaned = true
        saveOutputMetaData(outputFileOutputMetaData)
        File outputFile = outputFileOutputMetaData.outputFile
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
            else {
                log.info "Moved output file ${outputFile.absolutePath} to trash folder" 
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
    
    void preserve(def args) {
        List<OutputMetaData> outputs = scanOutputFolder()
        int count = 0
        for(def arg in args) {
            OutputMetaData output = outputs.find { it.outputPath == arg }
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
     * @param outputs   a List of OutputMetaData objects, loaded from the OutputMetaData files
     *                  saved by Bpipe as each stage executed (see {@link #saveOutputs(PipelineContext)})
     *                  
     * @return          the root node in the graph of outputs
     */
    GraphEntry computeOutputGraph(List<OutputMetaData> outputs, GraphEntry rootTree = null, GraphEntry topRoot=null, List handledOutputs=[], boolean computeUpToDate=true) {
        
        // Special case: no outputs at all
        /*
        if(!outputs) {
            if(!rootTree)
                rootTree = new GraphEntry(values:[])
            return rootTree
        }
        */
        
        // Here we are using a recursive algorithm. First we find the "edge" outputs -
        // these are ones that are created inputs that are "external" - ie. not
        // outputs of anything else in the graph.  We take those and make them into a layer
        // of the graph, connecting them to the parents that they depend on.  We then remove them
        // from the list of outputs and recursively do the same for each output we discovered, building
        // the whole graph from the original inputs through to the final outputs
        
        List allInputs = Utils.time("Calculate unique inputs") { outputs*.inputs.flatten().unique() }
        List allOutputs = outputs*.outputPath
        
        // Find all entries with inputs that are not outputs of any other entry
        def outputsWithExternalInputs = outputs.grep { p -> ! p.inputs.any { allOutputs.contains(it) } }
        
        // NOTE: turning this log on can be expensive for large numbers of inputs and outputs
//        log.info "External inputs: " + outputsWithExternalInputs*.inputs + " for outputs " + outputsWithExternalInputs*.outputPath
        
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
            for(OutputMetaData out in outputsWithExternalInputs) {
                
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
        
        if(!outputs.isEmpty() && !outputsWithExternalInputs)
            throw new PipelineError("Unable to identify any outputs with only external inputs in: " + outputs*.outputFile.join('\n') + "\n\nThis may indicate a circular dependency in your pipeline")
        
//        log.info "There are ${outputGroups.size()} output groups: ${outputGroups.values()*.outputPath}"
        log.info "There are ${outputGroups.size()} output groups"
        log.info "Subtracting ${outputsWithExternalInputs.size()} remaining outputs from ${outputs.size()} total outputs"
        
        Set outputsWithExternalInputsSet = outputsWithExternalInputs as Set
        
        List remainingOutputs = outputs.grep { !outputsWithExternalInputsSet.contains(it) }
        // remainingOutputs.removeAll(outputsWithExternalInputs)
        
        List remainingWithoutExternal = new ArrayList(remainingOutputs.size())
        
        outputGroups.each { key, outputGroup ->
//          log.info "Remaining outputs: " + remainingOutputs*.outputPath
            computeOutputGraph(remainingOutputs, createdEntries[key], topRoot, handledOutputs)
//          log.info "Handled outputs: " + handledOutputs*.outputPath + " Hashcode  " + handledOutputs.hashCode()
            remainingOutputs.removeAll(handledOutputs)
            log.info "There are ${remainingOutputs.size()} remaining outputs"
        }
        
        if(!computeUpToDate)
            return rootTree
                
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
            
            for(OutputMetaData p in entry.values) {
                
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
                       (newerInputs.collect { it.outputFile.fileName + ' / ' + it.timestamp + ' / ' + it.maxTimestamp + ' vs ' + p.timestamp })
                    continue
                }
                
                log.info "$p.outputPath is newer than input files"

                // The entry may still not be up to date if it
                // does not exist and a downstream target needs to be updated
                if(Files.exists(p.outputFile)) {
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
    
    List<OutputMetaData> findNewerInputs(OutputMetaData p, List<OutputMetaData> inputValues) {
        inputValues.grep { OutputMetaData inputProps ->
                    
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
     * Read all the OutputMetaData files in the output folder
     * @return
     */
    List<OutputMetaData> scanOutputFolder() {
        int concurrency = (Config.userConfig?.outputScanConcurrency)?:5
        List result = []
        Utils.time("Output folder scan (concurrency=$concurrency)") {
            GParsPool.withPool(concurrency) { 
                List<File> files = 
                               new File(OutputMetaData.OUTPUT_METADATA_DIR).listFiles()
                               ?.toList()
                                .grep { !it.name.startsWith(".") && !it.isDirectory() && !it.name.equals("outputGraph.ser") && !it.name.equals("outputGraph2.ser") } // ignore files starting with ., 
                                                                   // added as a convenience because I occasionally
                                                                   // edit files in output folder when debugging and it causes
                                                                   // Bpipe to fail!
                                
                if(!files)
                    return []
                result.addAll(files.collectParallel { 
                    OutputMetaData.fromFile(it)
                }.grep { it != null }.sort { it.timestamp })
            }
        }
        return result
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
        graph.depthFirst { GraphEntry e ->
            if(e.children.isEmpty() && e.values*.outputPath.every { !outputs.contains(it) } ) {
              result << e
              outputs += e.values*.outputPath
            }
        }
        return result
    }
    
    public withOutputGraph(Closure c) {
        this.outputGraphLock.readLock().lock()
        try {
            c(this.outputGraph) 
        }
        finally {
            this.outputGraphLock.readLock().unlock()
        }
    }
    
    /**
     * List of files to rebuild
     * 
     * @param filePaths
     */
    @CompileStatic
    void remakeFiles(List<String> filePaths) {
        filePaths.collect { Utils.canonicalFileFor(it) }.each { File f ->
            overrideTimestamps[f.absolutePath] = f.lastModified()
        }
    }
}
