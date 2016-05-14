package bpipe

class FlattenedPipelineStage extends PipelineStage {
    
    List<PipelineStage> merged 

    FlattenedPipelineStage(PipelineContext ctx, Closure body, List<PipelineStage> merged) {
        super(ctx,body)
        this.merged = Utils.removeRuns(merged, merged*.stageName)
        this.stageName = merged*.stageName.join("_")+"_bpipe_merge"
    }
}
