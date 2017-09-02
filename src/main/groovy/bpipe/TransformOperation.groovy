/*
 * Copyright (c) 2013 MCRI, authors
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

import java.security.cert.CertPath

import groovy.util.logging.Log;

/**
 * Implements logic for "transform" operations in a Bpipe script.
 * <p>
 * A "transform" means that the input(s) are processed to output a new kind
 * of file (eg: .bam file in, .csv out). By modeling the idea of transforms as a 
 * first class concept, Bpipe can enforce a convention for how such 
 * operations name their file: the output file has the same name as the 
 * input file. The "execute()" method models this logic as the default
 * behavior. However a more advanced behavior is offered that allows any
 * regex to be executed on the input files to transform them into the output file
 * name. This form of the logic uses both and input pattern and an output pattern,
 * and appears in the Bpipe script in the form:
 * <code>transform("csv") to("xml") { ... }
 * <p>
 * The {@link #execute(String)} method implements both behaviors. The form used
 * depends on whether a "toPattern" is provided to the method. The toPattern 
 * is provided when the user calls the {@link #to(String, Closure)} method
 * on this class, which happens in the syntax above.
 * 
 * @author Simon
 */
@Log
class TransformOperation {
    
    /**
     * The list of file extensions that this transform operation is set to work on
     */
    List exts
    
    /**
     * The body closure to execute when the transform is executed
     */
    Closure body
    
    /**
     * The list of files used as inputs for the transformation. In simple mode,
     * this list is set in the constructor and is used directly. In advanced mode,
     * this list is treated as a pattern that is used to match on files
     * upstream in the pipeline tree to find files matching the given pattern.
     */
    List files
    
    /**
     * The pipeline context for which this transform operation is occurring.
     */
    PipelineContext ctx

    public TransformOperation(PipelineContext ctx, List exts, Closure c) {
        this.ctx = ctx
        this.exts = exts
        this.body = c
        this.files = Utils.box(ctx.@input)
    }
    
    void to(String toPattern, Closure c) {
        to([toPattern],c)
    }
    
    void to(String toPattern1, String toPattern2, Closure c) {
        to([toPattern1,toPattern2],c)
    }
     
    void to(String toPattern1, String toPattern2, String toPattern3, Closure c) {
        to([toPattern1,toPattern2,toPattern3],c)
    }
    
    /**
     * Change this transform operation from simple mode to the advanced mode where
     * both a "from" pattern and a "to" pattern are provided, and execute the 
     * transform.
     */
    void to(List<String> toPatterns, Closure c) {
        
        this.body = c
        
        // Save original files: we need them later in case they were different
        // from those implied by previous stages (eg: modified by 'from')
        List originalFiles = this.files
        
        // We are going to re-resolve the files to use based on the 
        // from patterns (which are stored in exts)
        this.files = []
        
        // Expanded list of from patterns : there will be one for each input resolved
        // (so if one pattern matches 2 inputs, it appears twice).
        List fromPatterns = []
        
        // Expanded list of to patterns : there will be one for each input resolved
        // (so if one pattern matches 2 inputs, it appears twice).
        List expandedToPatterns = []
        
        // If there are not enough exts to match all the patterns
        // they map to, we repeat the last one to fill up the missing ones
        if(exts.size() < toPatterns.size()) {
            exts = exts.clone()
            while(exts.size() < toPatterns.size()) {
              exts.add( exts[-1] )
            }
        }
        
        // Similarly, if there are not enough to patterns, fill them up from
        // the first to pattern
        if(toPatterns.size() < exts.size()) {
            toPatterns = toPatterns.clone()
            while(toPatterns.size() < exts.size())
                toPatterns.add(toPatterns[-1])
        }
        
        // In the advanced case, the "file extensions" are not file extensions, but
        // regular expressions for matching files to transform from
        for(def toFromPair in [toPatterns,exts].transpose()) {
            
            String toPattern = toFromPair[0]
            String fromPattern = toFromPair[1]
            
            String pattern = '.*'+fromPattern
            if(!pattern.endsWith('$'))
                pattern += '$'
                
            PipelineInput input = new PipelineInput(originalFiles, this.ctx.pipelineStages, this.ctx.aliases)
            List<String> filesResolved = input.resolveInputsEndingWithPatterns([fromPattern + '$'], [fromPattern])
            if(isGlobPattern(fromPattern)) {
                
                this.files.addAll(filesResolved)
                
                // Add a from pattern for every file that was resolved
                fromPatterns.addAll([fromPattern[1..-1]] * filesResolved.size())
                expandedToPatterns.addAll([toPattern] * filesResolved.size())
            }
            else {
                if(filesResolved) {
                    this.files.add(filesResolved[0])
                    fromPatterns.add(fromPattern)
                    expandedToPatterns.add(toPattern)
                }
            }
        }
        
        // Replace the original list of from patterns with our expanded list
        this.exts = fromPatterns
        
        execute(fromPatterns,expandedToPatterns)
    }
    
    /**
     * Executes this transform operation.
     * <p>
     * The default (simple) mode occurs when no parameters are provided. In that case,
     * outputs are computed by taking their inputs, and replacing their extensions
     * with those set in the constructor by {@link #exts}.
     */
    void execute(List<String> fromPatterns = ['\\.[^\\.]*$'], List<String> providedToPatterns = null) {
        
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
        
        def outFiles = computeOutputFiles(ctx.applyName ? pipeline.name : null, ctx.stageName, fromPatterns, providedToPatterns)
        
        log.info "Transform using $exts produces outputs $outFiles"
        
        if(ctx.applyName)
            pipeline.nameApplied = true
           
        ctx.checkAccompaniedOutputs(files)
            
        // Coerce any inputs coming from different folders to the correct output folder
        outFiles = ctx.toOutputFolder(outFiles)
        
        if(providedToPatterns) {
            ctx.withInputs(this.files) {
                ctx.produceImpl(outFiles, body)
            }
        }
        else 
            ctx.produceImpl(outFiles, body)
    }
    
    /**
     * Compute a list of expected output file names from this transform's input files (files attribute)
     */
    List<PipelineFile> computeOutputFiles(String applyBranchName, String stageName, List<String> fromPatterns = ['\\.[^\\.]*$'], List<String> providedToPatterns = null) {
        Map extensionCounts = [:]
        for(def e in exts) {
            extensionCounts[e] = 0
        }
        
        if(!Utils.first(files)) {
            if(!providedToPatterns) // Basic form: no pattern used, only file extensions
              throw new PipelineError("Expected input but no input provided")
            else
              throw new PipelineError("Expected input but no input could be resolved matching pattern ${fromPatterns[0]}")
        }
        
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
        
        // If the pipeline branched, we need to add a segment to the new files name
        // to differentiate it from other parallel branches
        String additionalSegment = ""
        if(applyBranchName != null) {
            additionalSegment = '.'+applyBranchName
            log.info "Applying branch name $applyBranchName to pipeline segment because name is yet to be applied"
        }
        
        int count = 0
        def outFiles = exts.collect { String extension ->
            
            // In the simple case, only 1 from pattern exists - we always replace the file extension
            // In advanced case we expect 1 from pattern and 1 toPattern per file
            int fromPatternIndex = count%fromPatterns.size()
            int fileIndex = (providedToPatterns == null) ? extensionCounts[extension] % files.size() : fromPatternIndex
            
            PipelineFile inp = this.files[fileIndex]
            String fromPattern = fromPatterns[fromPatternIndex]
            
            extensionCounts[extension]++
            String toPattern = providedToPatterns?providedToPatterns[count]:null
            if(toPattern == null) {
               toPattern = extension 
               if(!toPattern.startsWith('.'))
                   toPattern = '.' + toPattern
            }
            
            PipelineFile txed = null
            if(inp.name.contains(".")) {
                String dot = fromPattern.startsWith(".") ?"":"."
                txed = inp.newName(inp.path.replaceAll(fromPattern,dot+FastUtils.dotJoin(additionalSegment,toPattern)))
            }
            else {
                txed = inp.newName(FastUtils.dotJoin(inp.path,additionalSegment,extension))
            }
            
            // There are still some situations that can result in consecutive periods appearing in
            // a file name (when the branch name is inserted automatically). So it's a bit of a hack,
            // but we simply remove them
            txed = inp.newName(txed.path.replaceAll(/\.\.*/,/\./))
            
            // A small hack that is designed to avoid a situation where an output 
            // receives the same name as an input file
            if(files.any { it.path == txed.path }) {
                txed = txed.newName(txed.path.replaceAll('\\.'+extension+'$', '.'+FastUtils.dotJoin(stageName,extension)))
            }
            
            if(txed.path.startsWith("."))
                txed = txed.newName(txed.path.substring(1))
                
            ++count
            return txed
        }
        return outFiles
    }
    
    /**
     * Return true if the extension given is a wildcard type expression that could
     * match multiple files.
     * @param ext
     * @return
     */
    boolean isGlobPattern(ext) {
        boolean result = ext instanceof String && ext.startsWith('*.')
        return result
    }
}
