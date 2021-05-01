package bpipe

import bpipe.PipelineContext.OutputResolver
import bpipe.storage.UnknownStoragePipelineFile
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import java.util.regex.Pattern

@CompileStatic
@Log
class SelectFromPredefinedFileNameMapper implements FileNameMapper {

    static Pattern FILE_EXT_PATTERN = ~'\\.[^.]*$'

    public SelectFromPredefinedFileNameMapper(List<PipelineFile> overrideOutputs, PipelineContext ctx) {
        super();
        this.ctx = ctx;
        this.overrideOutputs = overrideOutputs
    }

    final PipelineContext ctx

    final List<PipelineFile> overrideOutputs 

    @Override
    public FileNameMappingResult mapFileName(List<String> segments) {

        final String name = segments[-1]
        final String dotName = '.' + name
        final endExt = segments.join('.')
        
        String result = this.overrideOutputs.find { 
            String.valueOf(it).endsWith(endExt) 
        }

        PipelineFile replaced = null
        if(!result) {
            if(name in ctx.currentFilter) {
                log.info "Allowing reference to output not produced by filter because it was available from a filtering of an alternative input"
                
                replaced = this.overrideOutputs[0]
                
                PipelineFile baseInput = this.ctx.resolvedInputs.find {it.path.endsWith(name)}
                if(!baseInput) {
                    baseInput = new UnknownStoragePipelineFile(String.valueOf(this.overrideOutputs[0]))
                    result = baseInput.newName(baseInput.path.replaceAll(FILE_EXT_PATTERN,dotName))
                }
                else {
                    log.info "Recomputing filter on base input $baseInput to achieve match with output extension $name"
                    result = this.ctx.currentFileNameTransform.transform([baseInput])[0]
                }
                result = Utils.toDir([result], this.ctx.getDir())[0]
            }
       }
        
      log.info "Selected output $result with extension $name from expected outputs $overrideOutputs"
       
       return new FileNameMappingResult(path:result, replaced:replaced?.path)
    }
}
