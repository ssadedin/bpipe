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

import groovy.transform.CompileStatic;
import groovy.transform.Memoized
import groovy.util.logging.Log

import java.nio.file.Files;
import java.nio.file.Path
import java.util.regex.Pattern

import bpipe.storage.StorageLayer



/**
 * Captures meta data about outputs from the pipeline. This includes
 * accurate timestamps of how long different aspects of processing took
 * (for example, how long it was queued for running, etc), as well as
 * accurate timestamps for the file modified time (since some file systems
 * only give coarse values for this, eg 1 second resolution. Also included
 * are user-modifiable attributes such as whether the file is indicated
 * as to be preserved from cleanup, or whether it was actually cleaned up
 * as opposed to hard removed.
 * <p>
 * Currently these objects are serialized as property files in the .bpipe/outputs
 * directory. This design, however, is not very scalable as people are using 
 * Bpipe in pipelines that create tens of thousands of output files which can 
 * lead to it taking a long time to scan and read this directory. 
 * A more database-like format such as SQLite may be more suitable. As an 
 * intermediate step, the whole graph of these files is currently serialized
 * using Java serialization so that for simple operations the graph can be
 * read back into memory without having to scan each property file 
 * (see Dependencies#saveOutputGraph()).
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class OutputMetaData implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    /**
     * Directory where metadata for pipeline stage outputs will be written
     */
    public final static String OUTPUT_METADATA_DIR = ".bpipe/outputs/"
    
    List<String> inputs = []
    
    boolean cleaned = false
    
    transient PipelineFile outputFile
    
    String outputPath
    
    String canonicalPath
    
    long timestamp
    
    boolean preserve
    
    boolean intermediate
    
    String commandId
    
    long startTimeMs
    
    long createTimeMs
    
    long stopTimeMs
    
    String tools = ""
    
    String branchPath
    
    String stageName
    
    String stageId
    
    File propertyFile
    
    Path propertyFilePath
    
    String command
    
    String basePath
    
    String fingerprint
    
    String accompanies
    
    boolean knownExists = false
    
    /**
     * Virtual property - not stored
     */
    boolean upToDate = true
    
    /**
     * Virtual property - not stored
     */
    long maxTimestamp
    
    /**
     * The type of storage - if null, assume local
     */
    StorageLayer storage
    
    private OutputMetaData() {
    }
    
    OutputMetaData(PipelineFile outputFile) {
        this.outputPath = outputFile.path
    }
    
    /**
     * All properties are serialized to the output property file, except
     * those specified below.
     */
    private final static Set<String> EXCLUDE_SAVE_PROPERTIES = [
        "upToDate", 
        "maxTimeStamp",
        "class"
    ] as Set
    
    @CompileStatic
    void setPropertiesFromCommand(PipelineFile o, Command command, PipelineStage stage, String branchPath) {
        
        this.command = command.command
        this.commandId = command.id
        this.branchPath = branchPath
        this.stageId = stage.id
        
        PipelineContext context = stage.context
        
        this.stageName = context.stageName
        
        List<PipelineFile> allInputs = stage.context.getResolvedInputs()
        log.info "Context " + context.hashCode() + " for stage " + context.stageName + " has resolved inputs " + allInputs
        
        this.inputs = allInputs*.toString()?:(List<String>)[]
        this.outputFile = o
        this.basePath = Runner.runDirectory
        this.canonicalPath = o.toPath().toAbsolutePath().normalize()
        this.fingerprint = Utils.sha1(command.command+"_"+o)
        
        
        List<Tool> resolvedTools = (List<Tool>)context.documentation["tools"].collect { Map.Entry<String,Tool> toolEntry -> toolEntry.value }
        this.tools = resolvedTools.collect { Tool tool -> tool.fullName + ":" +tool.version }.join(",")
               
        if(this.cleaned == null)
            this.cleaned = false
                
        this.preserve = (o.path in context.preservedOutputs*.path)
                
        this.intermediate = context.intermediateOutputs.contains(o)
        if(context.accompanyingOutputs.containsKey(o))
            this.accompanies = context.accompanyingOutputs[o]
                    
        this.startTimeMs = command.startTimeMs
        this.createTimeMs = command.createTimeMs
        this.stopTimeMs = command.stopTimeMs
    }
    
    @CompileStatic
    private void readObject(java.io.ObjectInputStream ins) throws IOException, ClassNotFoundException {
        ins.defaultReadObject()
        if(storage == null)
            storage = StorageLayer.getDefaultStorage()
            
        this.outputFile = new PipelineFile(this.outputPath, this.storage) 
    }
    
    /**
     * Store the given OutputMetaData file as an output meta data file
     */
    void save() {
        
        Properties p = new Properties()
        
        for(k in this.properties.keySet()) {
            if(k in EXCLUDE_SAVE_PROPERTIES)
                continue
            p[k] = String.valueOf(this.getProperty(k))
        }
  
        // Undo possible format conversions so everything is strings
        p.inputs = inputs.join(",")
        
        p.cleaned = String.valueOf(cleaned)
        
        p.outputFile = String.valueOf(outputFile)
        p.storage = outputFile.storage.name
            
        // TODO: the whole point of storing the timestamp is to get better resolution than
        // is offered by the file system. On the other hand, if the file exists and has a timestamp,
		// shouldn't we believe that over what we recorded? Perhaps it should be the latest of either?
        if(outputFile.exists())
            p.timestamp = String.valueOf(outputFile.lastModified())
        else
        if(!timestamp)
            p.timestamp = "0"
        else
            p.timestamp = String.valueOf(timestamp)
            
        p.createTimeMs = createTimeMs ? String.valueOf(createTimeMs) : "0"
        p.stopTimeMs = stopTimeMs ? String.valueOf(stopTimeMs) : "0"
            
       
        log.info "Saving output file details to file $propertyFile for command " + Utils.truncnl(command, 20)
        propertyFile.withOutputStream { ofs ->
            p.save(ofs, "Bpipe Output File Meta Data")
        }
    }
    
    void read() {
        File f = getPropertyFile()
        read(f)
    }
    
    void read(File f) {
        log.info "Reading property file $f"
        
        Properties p = new Properties();
        new FileInputStream(f).withStream { p.load(it) }
        
        this.outputPath = p.outputPath
        
        if(p.inputs)
            this.inputs = p.inputs.split(",") as List
        
        this.cleaned = p.containsKey('cleaned')?Boolean.parseBoolean(p.cleaned) : false
        
        if(!p.outputFile)  {
            log.warning("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Error: output meta data property file $f is missing essential outputFile property")
            System.err.println ("Properties are: " + p)
            return 
        }
            
        this.storage = StorageLayer.create(p.storage?:'local')
        
        this.outputFile = new PipelineFile(outputPath, storage) 
        
        // Normalizing the slashes in the path is necessary for Cygwin compatibility
        this.outputPath = String.valueOf(outputFile).replace('\\',"/")
        
        // If the file exists then we should get the timestamp from there
        // Otherwise just use the timestamp recorded
        // todo: should use the LATER of these two!
        if(this.outputFile.exists())
            this.timestamp = outputFile.lastModified()
        else {
            if(p.timestamp != null) {
                this.timestamp = Long.parseLong(p.timestamp)
            }
        }

        // The properties file may have a cached version of the "canonical path" to the
        // output file. However this is an absolute path, so we can only use it if the
        // base directory is still the same as when this property file was saved.
        initializeCanonicalPath(p)
        
        basePath = p.basePath
        
        this.preserve = Boolean.parseBoolean(p.preserve)
        
        this.intermediate = Boolean.parseBoolean(p.intermediate)
        this.commandId = (p.commandId!=null)?p.commandId:"-1"
        this.startTimeMs = (p.startTimeMs?:0).toLong()
        this.createTimeMs = (p.createTimeMs?:0).toLong()
        this.stopTimeMs = (p.stopTimeMs?:0).toLong()
        
        if(p.containsKey("tools")) {
            this.tools = p.tools
        }
        
        if(p.containsKey("branchPath")) {
            this.branchPath = p.branchPath
        }
        
        if(p.containsKey("stageName")) {
            this.stageName = p.stageName
        }
        
        if(p.containsKey("stageId")) {
            this.stageId = p.stageId
        }
        
        fingerprint = p.fingerprint
        accompanies = p.accompanies
        command = p.command
    }
    

    @CompileStatic
    private void initializeCanonicalPath(Properties p) {
        if(!p.containsKey("basePath") || (p["basePath"] != Runner.runDirectory)) {
            this.canonicalPath = Utils.canonicalFileFor(this.outputFile.path)
        }
        else {
            this.canonicalPath = p.canonicalPath
        }
    }
    
    @CompileStatic
    String getBranchPath() {
        return branchPath == null ? "" : branchPath
    }
    
    @CompileStatic
    Path getPropertyFilePath() {
        /*
        if(this.@propertyFilePath == null) {
            this.@propertyFilePath = getPropertyFile().toPath()
        }
        return this.@propertyFilePath
        */
        return getPropertyFile().toPath()
    }
    
    @CompileStatic
    File getPropertyFile() {
        if(this.@propertyFile == null) {
            this.@propertyFile = getPropertyFileForOutputFile(this.outputPath)
        }
        return this.@propertyFile
    }
    
    /**
     * Return a {@link File} that indicates the path where
     * metadata for the specified output & file should be stored.
     * 
     * @param outputFile The name of the output file
     */
    File getPropertyFileForOutputFile(String outputFile) {
        
        File outputsDir = new File(OUTPUT_METADATA_DIR)
        if(!outputsDir.exists()) 
            outputsDir.mkdirs()
            
        String outputPath = new File(outputFile).path
        
        // If the output path starts with the run directory, trim it off
        if(outputPath.indexOf(Runner.canonicalRunDirectory)==0) {
            outputPath = outputPath.substring(Runner.canonicalRunDirectory.size()+1)
        }
        
        if(outputPath.startsWith("./"))
            outputPath = outputPath.substring(2)
        
        int maxFileNameLength = Config.userConfig.get('maxFileNameLength',2048)
        String pathElement = outputPath.replace('/', "_").replace('\\','_')
        String fileName = this.stageName + "." + pathElement + ".properties"
        if(fileName.size() > maxFileNameLength)
            fileName = this.stageName + "." + pathElement.substring(0, maxFileNameLength-this.stageName.size()-60) + '.' + Utils.sha1(pathElement) + ".properties"
            
        return  new File(outputsDir,fileName)
    }
    
    /**
     * Return true iff the property file for this meta data file already exists
     * @return
     */
    @CompileStatic
    boolean exists() {
       if(knownExists)
           return true
       return Files.exists(getPropertyFilePath())
    }
    
    String toString() {
        outputPath +  " <= " + this.inputs.join(",")
    }
    
    @CompileStatic
    boolean hasInput(String inp) {
        this.canonicalInputs.contains(inp)
    }
    
    List<String> canonicalInputs = null
    
    @CompileStatic
    List<String> getCanonicalInputs() {
        if(this.canonicalInputs == null)
            this.canonicalInputs = this.inputs.collect { Utils.canonicalFileFor(it).path }
        return this.canonicalInputs
    } 

    @CompileStatic
    static OutputMetaData fromFile(File file) {
       OutputMetaData omd = new OutputMetaData()
       omd.read(file) 
       return omd
    }
    
    /**
     * @param inputProps   
     * @return  true if the input supplied is used to create this output AND
     *          is newer than this output
     */
    @CompileStatic
    boolean isNewer(OutputMetaData inputProps) {
        if(!this.inputs.contains(inputProps.outputPath)) // Not an input used to produce this output
            return false

        log.info "Checking timestamp of $outputFile vs input $inputProps.outputPath"
        if(inputProps?.maxTimestamp < this.timestamp) { // inputs unambiguously older than output
            return false
        }

        if(inputProps?.maxTimestamp > this.timestamp) // inputs unambiguously newer than output
            return true

        // Problem: many file systems only record timestamps at a very coarse level.
        // 1 second resolution is common, but even 1 minute is possible. In these cases
        // commands that run fast enough produce output files that have equal timestamps
        // To differentiate these cases we check the start and stop times of the
        // actual commands that produced the file
        if(!inputProps.stopTimeMs)
            return true // we don't know when the command that produced the input finished
        // so have to assume the input could have come after

        if(!this.createTimeMs)
            return false // don't know when the command that produced this output started,
        // so have to assume the command that made the input might have
        // done it after

        // Return true if the command that made the input stopped after the command that
        // created the output. ie: that means the input is newer, even though it has the
        // same timestamp
        return inputProps.stopTimeMs >= this.createTimeMs
    }
    
    @CompileStatic
    static OutputMetaData fromInputFile(final PipelineFile inputFile) {
        String inputFileValue = String.valueOf(inputFile)
        OutputMetaData omd = new OutputMetaData(inputFile)
        omd.timestamp = inputFile.lastModified()
        omd.outputFile = inputFile
        omd.canonicalPath = Utils.canonicalFileFor(inputFileValue).absolutePath
        omd
    }
}
