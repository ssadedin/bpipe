package bpipe

class FlattenedPipelineStage extends PipelineStage {
    
    List<PipelineStage> merged 
    
    List<Branch> branches

    FlattenedPipelineStage(PipelineContext ctx, Closure body, List<PipelineStage> merged, List<Branch> branches) {
        super(ctx,body)
        this.merged = Utils.removeRuns(merged, merged*.stageName)
        this.stageName = merged*.stageName.join("_")+"_bpipe_merge"
        this.branches = branches
    }
}
