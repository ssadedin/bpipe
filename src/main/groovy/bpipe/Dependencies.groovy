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

import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.LocalPipelineFile

/**
 * Manages dependency tracking functions in Bpipe.
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
    List<PipelineFile> outputFilesGenerated = []
    
    /**
     * List of files whose timestamps are overridden, so that their real
     * timestamp is ignored until they are recreated. This is used by the
     * remake function.
     */
    Map<String,Long> overrideTimestamps = [:]
    
    
    @CompileStatic
    static Dependencies getTheInstance() {
        return Dependencies.instance
    }
    
    /**
     * Return true iff all the outputs and downstream outputs for which
     * they are dependencies are present and up to date.
     * 
     * @param outputs
     * @param inputs
     * @return  true iff file is newer than all inputs, but older than all 
     *          non-up-to-date outputs
     */
    List<Path> getOutOfDate(List<PipelineFile> originalOutputs, List<PipelineFile> inputs) {
        
        List<PipelineFile> outputs = originalOutputs.unique(false)

        assert inputs.every { it instanceof PipelineFile }
        
        inputs = Utils.box(inputs)

        List<Path> outOfDateOutputs = []
        
        // If there are no outputs then by definition, they are all up to date
        if(!outputs)
            return outOfDateOutputs
        
        // Outputs are forcibly out of date if specified by remake
        if(!overrideTimestamps.isEmpty()) { 
            // TODO - CLOUD - convert to use nio Path
            List forcedOutOfDate = outputs.grep { o ->
                String path = o instanceof File ? o.path : o
                File cf = Utils.canonicalFileFor(path)
                Long ts = overrideTimestamps[cf.path]
                
                // If the timestamps are the same then we consider the timestamp overridden
                ts == cf.lastModified()
            }
            outOfDateOutputs.addAll(forcedOutOfDate*.toPath())
        }
        
        // If the outputs are created from nothing (no inputs)
        // then they are up to date as long as they exist
        if(!inputs)  {
            outOfDateOutputs.addAll(outputs.collect { it instanceof PipelineFile ? it : new LocalPipelineFile(it) }.grep { !it.exists() }*.toPath())
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
            outDated = older.collect { it.normalize() }.grep { Path out ->
                 def p = graph.propertiesFor(out.toString()); 
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
    @CompileStatic
    void checkFiles(List<PipelineFile> files, Aliases aliases, String type="input", String extraDescription="") {
        
        if(files == null) {
            log.info "$type files to check are null, skipping."
            return
        }

        log.info "Checking ${files.size()} $type (s) " 
        
        GraphEntry graph = this.getOutputGraph()
        
        if(files.any { !(it instanceof PipelineFile) }) {
            assert false : "One or more files is not a PipelineFile! : " + files.grep { !(it instanceof PipelineFile) }.join(',')
        }
        
        List<PipelineFile> filesToCheck = files.collect { PipelineFile f -> aliases[f] }
        
        outputGraphLock.readLock().lock()
        
        Closure checkInput = { PipelineFile f ->
            OutputMetaData p = graph.propertiesFor(f.path)
            f.isMissing(p, type)
        }
        
        try { 
            List missing
            if(filesToCheck.size()>40) {
                missing = checkParallel(filesToCheck, missing, checkInput)
            }
            else {
                missing = filesToCheck.grep(checkInput)
            }
            
            if(missing) {
                throwMissingError(missing, type, extraDescription)
            }
        }
        finally {
            outputGraphLock.readLock().unlock()
        }
    }

    private void throwMissingError(List missing, String type, String extraDescription) {
        PipelineFile firstMissing = missing[0]

        String storageDescription = ""
        if(!(firstMissing?.storage instanceof LocalFileSystemStorageLayer)) {
            storageDescription = " (storage type " + firstMissing.storage + ")"
        }

        throw new PipelineError("Expected $type file ${firstMissing} ${storageDescription} $extraDescription could not be found")
    }

    private List checkParallel(List filesToCheck, List missing, Closure checkInput) {
        log.info "Using parallelized file check for checking ${filesToCheck.size()} inputs ..."
        int concurrency = Config.userConfig.get('outputScanConcurrency',5)
        missing = (List)GParsPool.withPool(concurrency) {
            filesToCheck.grepParallel(checkInput)
        }
        return missing
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
                outputGraph.index(5000)
            }
        }
        else {
            log.info "No cached output graph ($OUTPUT_GRAPH_CACHE_FILE.name) available: will be computed from property files"
        }
    }
    
    @CompileStatic
    synchronized GraphEntry getOutputGraph() {
        if(this.outputGraph == null) {
            List<OutputMetaData> outputMetaDataFiles = scanOutputFolder()
            if(outputMetaDataFiles.isEmpty()) {
                // Seed the output graph with a first stage of inputs that are
                // essentially dummy "outputs" created from the pipeline raw inputs
                def inps = Pipeline.rootPipeline?.stages?.getAt(0)?.context?.@input
                if(inps == null)
                    inps = []

                outputMetaDataFiles = ((List<PipelineFile>)Utils.box(inps)).collect { OutputMetaData.fromInputFile(it) }
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
            for(PipelineFile o in outputs) {
                if(!o)
                    continue
                    
                // TODO - CLOUD - move stageName into constructor since it is needed to have valid path
                OutputMetaData p = new OutputMetaData(o)
                p.stageName = stage.stageName // this needs to be set because the file name depends on it
                
                // Check if the output file existed before the stage ran. If so, we should not save meta data, as it will already be there
                // Note: saving two meta data files for the same output can produce a circular dependency (exception
                // when loading output graph).
                if(!timestamps.containsKey(o.path)) { 
                    // TODO - CLOUD - comparing just the paths will get confused if the same path exists on two
                    // storage providers
                    if(p.exists() || this.outputFilesGenerated.contains(o) || context.@input.any { it.path == o.path}) {
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
//                println "Adding file $p.outputPath to output graph"
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
        
        CliBuilder cli = new CliBuilder(usage: 'cleanup [-v] <files...>')
        cli.with {
            v 'Use verbose logging'
        }
        
        OptionAccessor opts = cli.parse(arguments)
        if(opts.v) {
            Utils.configureVerboseLogging()
        }
        arguments = opts.arguments()
        
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
        List<GraphEntry> leaves = findLeaves(graph)
        
        //  Filter out leaf nodes from this list if they are explicitly specified as intermediate files
        leaves.removeAll { it.values.every { it.intermediate } }
        
        log.info "Leaf nodes are: " + leaves.collect { GraphEntry e -> e.values*.outputFile.join(',') }.join(',')
        
        // Find all the nodes that exist and match the users specs (or, if no specs, treat as wildcard)
        List<OutputMetaData> internalNodes = (outputs - leaves*.values.flatten()).grep { p ->
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
                
            if(arguments.contains(p.outputFile.path))  
                return true
            else {
                log.info "File $p.outputFile doesn't match the arguments $arguments, so can't be cleaned up"
                return false
            }
        }
        
        List<String> internalNodeFileNames = internalNodes*.outputFile*.name*.toString()
        List<String> accompanyingOutputFileNames = []
        
        // Add any "accompanying" outputs for the outputs that would be cleaned up
        graph.depthFirst { GraphEntry g ->
            def accompanyingOutputs = g.values.grep { OutputMetaData p -> 
                p.accompanies && p.accompanies in internalNodeFileNames 
            }
            internalNodes += accompanyingOutputs
            accompanyingOutputFileNames += accompanyingOutputs*.outputFile*.name*.toString()
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
        Path outputFile = outputFileOutputMetaData.outputFile.toPath()
        long outputSize = Files.size(outputFile)
        if(trash) {
            Path trashDir = outputFile.resolveSibling('.bpipe/trash')
            if(!Files.exists(trashDir))
                Files.createDirectories(trashDir)
                
            try {
                Files.move(outputFile, trashDir.resolve(outputFile.fileName.toString()))
                log.info "Moved output file ${outputFile.toAbsolutePath()} to trash folder" 
            }
            catch(Exception e) {
              log.warning("Failed to move output file ${outputFile.toAbsolutePath()} to trash folder")
              System.err.println "Failed to move file ${outputFile.toAbsolutePath()} to trash folder"
              return 0
            }
        }
        else {
            try {
                Files.deleteIfExists(outputFile) 
            }
            catch(Exception e) {
                log.warning("Failed to delete output file ${outputFile.toAbsolutePath()}")
                System.err.println "Failed to delete file ${outputFile.toAbsolutePath()}"
                return 0
            }
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
        Set allOutputs = outputs*.canonicalPath as Set
        
        // Find outputs with "external" inputs - these are the top layer of outputs which
        // are fed purely by external inputs.
        //
        // That is: find all entries with inputs that are not outputs of any other entry
        def outputsWithExternalInputs = outputs.grep { OutputMetaData p -> 
             ! p.canonicalInputs.any { ip -> allOutputs.contains(ip) }
        }
        
        // NOTE: turning this log on can be expensive for large numbers of inputs and outputs
//        log.info "External inputs: " + outputsWithExternalInputs*.inputs + " for outputs " + outputsWithExternalInputs*.outputPath
        
        handledOutputs.addAll(outputsWithExternalInputs)
        
        // find groups of outputs (ie. ones that belong in the same branch of the tree)
        // If there is no tree to attach to, make one
        List entries = []
        Map<String,List> outputGroups = [:]
        Map createdEntries = [:]
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
//                log.info "New entry for ${out.outputPath}"
                entries << entry
                createdEntries[out.outputPath] = entry
                outputGroups[out.outputPath] = entry.values
                if(topRoot.index != null && out.canonicalPath) {
                    topRoot.index[out.canonicalPath] = entry
                }
                
                // find all nodes in the tree which this output depends on 
                entry.addAsChildInGraph(out, topRoot)
                
                List<OutputMetaData> dependenciesOnParents = entry.getParentDependencies(out)
                out.maxTimestamp = (dependenciesOnParents*.maxTimestamp.flatten() + out.timestamp).max()
                    
                log.info "Maxtimestamp for $out.outputFile = $out.maxTimestamp"
                
            }
        }
        
        if(topRoot == null)
            topRoot = rootTree
        
        if(!outputs.isEmpty() && !outputsWithExternalInputs)
            throw new PipelineError("Unable to identify any outputs with only external inputs in: " + 
                                     outputs*.outputFile.join('\n') + 
                                     "\n\nThis may indicate a circular dependency in your pipeline")
        
//        log.info "There are ${outputGroups.size()} output groups: ${outputGroups.values()*.outputPath}"
        log.info "There are ${outputGroups.size()} output groups"
        log.info "Subtracting ${outputsWithExternalInputs.size()} remaining outputs from ${outputs.size()} total outputs"
        
        Set outputsWithExternalInputsSet = outputsWithExternalInputs as Set
        
        List remainingOutputs = outputs.grep { !outputsWithExternalInputsSet.contains(it) }
        
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
                List newerInputs = findNewerInputs(p, inputValues)
                
                if(newerInputs) {
                    p.upToDate = false
                    log.info "$p.outputFile is older than ${newerInputs.size()} inputs " +
                       (newerInputs.collect { it.outputFile.name + ' / ' + it.timestamp + ' / ' + it.maxTimestamp + ' vs ' + p.timestamp })
                    continue
                }
                
//                log.info "$p.outputPath is newer than ${inputValues?.size()?:0} input files"

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
    
    @CompileStatic
    List<OutputMetaData> findNewerInputs(OutputMetaData p, List<OutputMetaData> inputValues) {
        inputValues.grep { OutputMetaData inputProps -> p.isNewer(inputProps) } 
    }
   
    /**
     * Read all the OutputMetaData files in the output folder
     * @return
     */
    List<OutputMetaData> scanOutputFolder() {
        int concurrency = Config.userConfig.get('outputScanConcurrency',5)
        List result = []
        Utils.time("Output folder scan (concurrency=$concurrency)") {
            
            List<File> files = 
               new File(OutputMetaData.OUTPUT_METADATA_DIR)
                   .listFiles()
                   .grep { isOutputMetaFile(it)  } 
                                
            if(files.isEmpty())
                return files
                    
            GParsPool.withPool(concurrency) { 
                result.addAll(files.collectParallel { 
                    OutputMetaData.fromFile(it)
                }.grep { it != null }.sort { it.timestamp })
            }
        }
        return result
    }
    
    /**
     * Return true if a file could be a valid output property file
     * 
     * Ignores files starting with ., added as a convenience because I occasionally
     * edit files in output folder when debugging, and known files in the output folder that
     * are not meta files.
     */
    @CompileStatic
    boolean isOutputMetaFile(File file) {
        !file.name.startsWith(".") && !file.isDirectory() && !file.name.equals("outputGraph.ser") && !file.name.equals("outputGraph2.ser")        
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
//            log.info "$e.values has children? " + !e.children.isEmpty()
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
    
    /**
     * This exists because I found compile static can't recognise the default provided
     * instance variable from other classes as being the right type.
     * 
     * @return  the Dependencies instance
     */
    @CompileStatic
    static Dependencies get() {
        return getInstance()
    }
}
