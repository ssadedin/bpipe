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

import groovy.util.logging.Log;

/**
 * Implements logic for "transform" operations in a Bpipe script.
 * <p>
 * A "transform" means that the input(s) are processed to output a new kind
 * of file (eg: .bam file in, .csv out). By modeling the idea of transforms as a 
 * first calss concept, Bpipe can enforce a convention for how such 
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
    
    /**
     * Change this transform operation from simple mode to the advanced mode where
     * both a "from" pattern and a "to" pattern are provided, and execute the 
     * transform.
     */
    void to(String toPattern, Closure c) {
        this.body = c
        String pattern = '.*'+exts[0]
        if(!pattern.endsWith('$'))
            pattern += '$'
        PipelineInput input = new PipelineInput(this.files, this.ctx.pipelineStages)
//        this.files = this.files.grep { it.matches(pattern) }
        this.files = input.resolveInputsEndingWithPatterns(exts.collect { it + '$'})
        execute(exts[0],toPattern)
    }
    
    /**
     * Executes this transform operation.
     * <p>
     * The default (simple) mode occurs when no parameters are provided. In that case,
     * outputs are computed by taking their inputs, and replacing their extensions
     * with those set in the constructor by {@link #exts}.
     */
    void execute(String fromPattern = '\\.[^\\.]*$', String providedToPattern = null) {
        def extensionCounts = [:]
        for(def e in exts) {
            extensionCounts[e] = 0
        }
        
        if(!Utils.first(files)) {
            if(!providedToPattern)
              throw new PipelineError("Expected input but no input provided")
            else
              throw new PipelineError("Expected input but no input could be resolved matching pattern $fromPattern")
        }
        
        def pipeline = Pipeline.currentRuntimePipeline.get()
        
        // If the pipeline branched, we need to add a segment to the new files name
        // to differentiate it from other parallel branches
        String additionalSegment = ctx.applyName ? '.'+pipeline.name : ''
        
        def outFiles = exts.collect { String extension ->
            String inp = this.files[extensionCounts[extension] % files.size()]
            extensionCounts[extension]++
            String toPattern = providedToPattern
            if(toPattern == null) {
               toPattern = extension 
               if(!toPattern.startsWith('.'))
                   toPattern = '.' + toPattern
            }
            String txed = inp.contains(".") ?
                    inp.replaceAll(fromPattern, FastUtils.dotJoin(additionalSegment,toPattern))
                :
                    FastUtils.dotJoin(inp,additionalSegment,extension)
                    
            if(txed in files) {
                txed = txed.replaceAll('\\.'+extension+'$', '.'+FastUtils.dotJoin(ctx.stageName,extension))
            }
            return txed
        }
        
        log.info "Transform using $exts produces outputs $outFiles"
        
        if(ctx.applyName)
            pipeline.nameApplied = true
            
        // Coerce any inputs coming from different folders to the correct output folder
        outFiles = ctx.toOutputFolder(outFiles)
        
        ctx.produceImpl(outFiles, body)
    }
}
