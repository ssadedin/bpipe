package bpipe

import groovy.util.logging.Log;

@Log
class TransformOperation {
    
    List exts
    
    Closure body
    
    List files
    
    PipelineContext ctx

    public TransformOperation(PipelineContext ctx, List exts, Closure c) {
        this.ctx = ctx
        this.exts = exts
        this.body = c
        this.files = Utils.box(ctx.@input)
    }
    
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
    
    void execute(String fromPattern = '\\.[^\\.]*$', String toPattern = null) {
        def extensionCounts = [:]
        for(def e in exts) {
            extensionCounts[e] = 0
        }
        
        if(!Utils.first(files)) {
            if(!toPattern)
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
            if(toPattern == null) {
               toPattern = extension 
               if(!toPattern.startsWith('.'))
                   toPattern = '.' + toPattern
            }
            String txed = inp.contains(".") ?
                    inp.replaceAll(fromPattern, Utils.dotJoin(additionalSegment,toPattern))
                :
                    Utils.dotJoin(inp,additionalSegment,extension)
                    
            if(txed in files) {
                txed = txed.replaceAll('\\.'+extension+'$', '.'+Utils.dotJoin(ctx.stageName,extension))
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
