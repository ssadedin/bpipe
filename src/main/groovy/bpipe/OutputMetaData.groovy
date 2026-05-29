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

import groovy.transform.CompileStatic
import groovy.util.logging.Log

import bpipe.storage.StorageLayer



/**
 * Captures metadata about a single pipeline output file: timing, provenance,
 * dependency inputs, and user flags such as {@code preserve} and {@code cleaned}.
 * <p>
 * This is a pure data class.  All persistence (property files or SQLite) is
 * handled by {@link OutputMetaDataStore} implementations; see
 * {@link PropertyFileOutputMetaDataStore} and {@code SQLiteOutputMetaDataStore}.
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

    String command
    
    String basePath
    
    String fingerprint
    
    String accompanies
    
    /**
     * Whether this output is a stub (placeholder) created in dev mode
     * rather than by actual command execution
     */
    boolean stub = false
    
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
    
    OutputMetaData() {
    }
    
    OutputMetaData(PipelineFile outputFile) {
        this.outputPath = outputFile.path
    }
    
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
    
    @CompileStatic
    String getBranchPath() {
        return branchPath == null ? "" : branchPath
    }
    
    String toString() {
//        def minTimestamp = [this.timestamp, *this.inputs*.timestamp]

        outputPath +  " ($timestamp) <= " + this.inputs.join(',')
    }
    
    @CompileStatic
    boolean hasInput(String inp) {
        this.canonicalInputs.contains(inp)
    }
    
    /**
     * We use this set because it preserves ordering on iteration
     */
    LinkedHashSet<String> canonicalInputs = null
    
    @CompileStatic
    LinkedHashSet<String> getCanonicalInputs() {
        if(this.canonicalInputs == null) {
            this.canonicalInputs = new LinkedHashSet()
            for(inp in this.inputs) { 
                 this.canonicalInputs.add(Utils.canonicalFileFor(inp).path)
            }
        }
        return this.canonicalInputs
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

        log.fine "Checking timestamp of $outputFile vs input $inputProps.outputPath"
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
