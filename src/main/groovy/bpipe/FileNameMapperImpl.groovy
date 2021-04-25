package bpipe

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Log

import bpipe.storage.*

/**
 * @author Simon Sadedin
 */
@CompileStatic
@Log
class FileNameMapperImpl implements FileNameMapper {

    static Pattern FILE_EXT_PATTERN = ~'\\.[^.]*$'
  
    private static Pattern DOT_NUMBER_PATTERN = ~'\\.[^\\.]*(\\.[0-9]*){0,1}$'
     
    final PipelineContext ctx
    
    final PipelineContext.OutputResolver resolver
    
    final String branchName
    
    final String stageName

    public FileNameMapperImpl(PipelineContext.OutputResolver resolver, String branchPrefix) {
        super();
        this.ctx = resolver.context
        this.resolver = resolver
        this.stageName = this.ctx.stageName
        this.branchName = branchPrefix
    }

    @Override
    public FileNameMappingResult mapFileName(final List<String> segments) {
        
        if(this.resolver.overrideOutputs) {
           return selectFromOverrides(segments)  
        }
        else 
           return synthesiseFromName(segments)
    }
    
    FileNameMappingResult selectFromOverrides(List<String> segments) {

        final String name = segments[-1]
        final String dotName = '.' + name
        final endExt = segments.join('.')
        
        String result = this.resolver.overrideOutputs.find { 
            String.valueOf(it).endsWith(endExt) 
        }

        PipelineFile replaced = null
        if(!result) {
            if(name in ctx.currentFilter) {
                log.info "Allowing reference to output not produced by filter because it was available from a filtering of an alternative input"
                
                replaced = this.resolver.overrideOutputs[0]
                
                PipelineFile baseInput = this.ctx.resolvedInputs.find {it.path.endsWith(name)}
                if(!baseInput) {
                    baseInput = new UnknownStoragePipelineFile(String.valueOf(this.resolver.overrideOutputs[0]))
                    result = baseInput.newName(baseInput.path.replaceAll(FILE_EXT_PATTERN,dotName))
                }
                else {
                    log.info "Recomputing filter on base input $baseInput to achieve match with output extension $name"
                    result = this.ctx.currentFileNameTransform.transform([baseInput])[0]
                }
                result = Utils.toDir([result], this.ctx.getDir())[0]
            }
       }
        
      log.info "Selected output $result with extension $name from expected outputs $resolver.overrideOutputs"
       
       return new FileNameMappingResult(path:result, replaced:replaced?.path)
    }
    
    FileNameMappingResult synthesiseFromName(List<String> extSegments) {
        
        String firstOutput = Utils.first(this.resolver.out)
        
        // If the extension of the output is the same as the extension of the 
        // input then this is more like a filter; remove the previous output extension from the path
        // eg: foo.csv.bar => foo.baz.csv
        List branchSegment = (ctx.applyName && branchName) ? [branchName] : [] 
        
        String segments = (branchSegment + [stageName] + extSegments).collect { 
            FastUtils.strip(it,'.')
        }.join(".")
         
        File outputFile = new File(firstOutput)
        
        String fullExt = extSegments.join('.')
        
        String outputUsed = null
        
        String name = extSegments[-1]
         
        if(stageName.equals(outputFile.name)) {
           outputUsed = FastUtils.dotJoin((List<String>)[this.resolver.baseOutput, *branchSegment, name])
        }
        else
        if(firstOutput.endsWith(fullExt+"."+stageName)) {
            log.info("Replacing " + fullExt+"\\."+stageName + " with " +  stageName+'.'+name)
            outputUsed = this.resolver.baseOutput.replaceAll("[.]{0,1}"+fullExt+"\\."+stageName, '.' + segments)
        }
        else { // more like a transform: keep the old extension in there (foo.csv.bar => foo.csv.bar.xml)
            outputUsed = computeOutputUsedAsTransform(name, segments)
        }
        
        if(outputUsed.startsWith(".") && !outputUsed.startsWith("./")) // occurs when no inputs given to script and output extension used
            outputUsed = outputUsed.substring(1) 
            
        if(this.ctx.inboundBranches) {
            for(Branch branch in this.ctx.inboundBranches) {
                log.info "Excise inbound merging branch reference $branch due to merge point"
                outputUsed = outputUsed.replace('.' + branch.name + '.','.merge.')
            }
        }
        return new FileNameMappingResult(path:Utils.fileToDir(outputUsed, ctx.getDir()))
    }

    /**
     * Compute a name for the output based on the assumption it is 
     * doing something like a 'transform' operation.
     * 
     * ie: the new extension is appended to the output.
     * 
     * @return  the transformed output name
     */
    @CompileStatic
    String computeOutputUsedAsTransform(String extension, String segments) {
        
        // First remove the stage name, if it is at the end
        List<String> result = new File(this.resolver.baseOutput).name.tokenize('.')
        if(result[-1] == stageName)
            result = result[0..-2]
            
        if(result[-1] == branchName && segments.startsWith(branchName+'.')) {
            result = result[0..-2]
        }

        // If the branch name is now at the end and there is a suffix we can remove the suffix
        // eg: from test.chr1.txt produce test.chr1.hello.csv rather than test.txt.chr1.hello.csv
        // (see param_chr_override test)
            
        if(branchName && (result[-1] == branchName)) {
            // Swap order of branch name and last extension - this way below
            // the branch name will get preserved and the extension replaced
            result = (List<String>)[*result[0..-3], branchName, result[-2]]
        }
            
        // If last segment is purely numeric, remove it
        // occurs when the use uses multiple outputs ($output1.csv, $output2.csv) and 
        // the same output file is generated for the outputs - such file names get 
        // a numeric index inserted. eg: test1.txt, test1.txt.2
        if(result[-1].isInteger()) {
            result = result[0..-2]
        }

        // Then replace the extension on the file with the requested one
        if(!this.ctx.activeAccompanierPattern) {
            
//            if(result.size()>1)
//                // Here we allow a potential match on a number in the base output, since that can 
//                // occur when the use uses multiple outputs ($output1.csv, $output2.csv) and 
//                // the same output file is generated for the outputs - such file names get 
//                // a numeric index inserted. eg: test1.txt, test1.txt.2
//                result = result.replaceAll(DOT_NUMBER_PATTERN, '$1.'+segments)
//            else
            result[-1] = segments
        }
        else {
            result = result + [extension]
        } 

        return FastUtils.dotJoin(result)
    }
}
