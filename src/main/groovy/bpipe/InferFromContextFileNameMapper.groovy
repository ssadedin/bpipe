package bpipe

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

import bpipe.storage.*

/**
 * @author Simon Sadedin
 */
@CompileStatic
@Log
class InferFromContextFileNameMapper implements FileNameMapper {

 
    private static Pattern DOT_NUMBER_PATTERN = ~'\\.[^\\.]*(\\.[0-9]*){0,1}$'
     
    final PipelineContext ctx
    
    final PipelineContext.OutputResolver resolver
    
    final String branchName
    
    final String stageName

    public InferFromContextFileNameMapper(PipelineContext.OutputResolver resolver, String branchPrefix) {
        super();
        this.ctx = resolver.context
        this.resolver = resolver
        this.stageName = this.ctx.stageName
        this.branchName = branchPrefix
    }

    @Override
    public FileNameMappingResult mapFileName(final List<String> extSegments) {
       
        String firstOutput = Utils.first(this.resolver.out)
        
        // If the extension of the output is the same as the extension of the 
        // input then this is more like a filter; remove the previous output extension from the path
        // eg: foo.csv.bar => foo.baz.csv
        List<String> branchSegment = (ctx.applyName && branchName) ? [branchName] : [] 
        
        String joinedSegments = (branchSegment + [stageName] + extSegments).collect { 
            FastUtils.strip(it,'.')
        }.join(".")
         
        File outputFile = new File(firstOutput)
        
        String fullExt = extSegments.join('.')
        
        String outputUsed = null
        
        String name = extSegments[-1]
         
        outputUsed = resolveFromPureStageName(outputFile, branchSegment, name)
        
        if(!outputUsed) {
            outputUsed = resolveAsFilterOnSameExt(firstOutput, fullExt, joinedSegments)
        }
        
        if(!outputUsed)
            outputUsed = resolveAsFilterOnCustomExt(firstOutput, name, fullExt, joinedSegments)

        if(!outputUsed) { 
            // like a transform: keep the old extension in there (foo.csv.bar => foo.csv.bar.xml)
            outputUsed = computeOutputUsedAsTransform(name, joinedSegments)
        }
        
        if(outputUsed.startsWith(".") && !outputUsed.startsWith("./")) // occurs when no inputs given to script and output extension used
            outputUsed = outputUsed.substring(1) 
            
        if(this.ctx.inboundBranches) {
            for(Branch branch in this.ctx.inboundBranches) {
                log.info "Excise inbound merging branch reference $branch due to merge point"
                outputUsed = outputUsed.replace('.' + branch.name + '.','.merge.')
            }
        }
        return new FileNameMappingResult(path:(String)Utils.fileToDir(outputUsed, ctx.getDir()))
    }
    
    String resolveFromPureStageName(final File outputFile, List<String> branchSegment, String name) {
        if(!stageName.equals(outputFile.name)) 
            return null
            
        return FastUtils.dotJoin((List<String>)[this.resolver.baseOutput, *branchSegment, name])
    }
    
    String resolveAsFilterOnSameExt(final String firstOutput, String fullExt, String segments) {
        if(!firstOutput.endsWith(fullExt+"."+stageName)) 
            return null
            
        log.info("Replacing " + fullExt+"\\."+stageName + " with " +  stageName+'.'+fullExt)
        Pattern extWithNumberPattern = getExtWithNumberPattern(fullExt)
        return this.resolver.baseOutput.replaceAll(extWithNumberPattern, '$1.' + segments)
    }
    
    String resolveAsFilterOnCustomExt(final String firstOutput, String name, String fullExt, String segments) {
        
        // Check each utilised custom type mapping. If the output matches one of them, 
        // treat the mappign as a single unit in creating a filter expression
        for(PipelineInput inp in ctx.allUsedInputWrappers*.value) {
            for(Map.Entry<String,String> e in inp.utilisedMappings) {
                
                String result = resolveAsFilterOnSameExt(firstOutput, e.value, segments)
                if(result)
                    return result
            }
        }
        return null
    }
    
    @Memoized
    Pattern getExtWithNumberPattern(final String fullExt) {
        Pattern.compile("[.]{0,1}"+fullExt+"([.][0-9]{1,}){0,1}\\."+stageName + '$')        
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
            
        // If last segment is purely numeric, switch its order
        // occurs when the use uses multiple outputs ($output1.csv, $output2.csv) and 
        // the same output file is generated for the outputs - such file names get 
        // a numeric index inserted. eg: test1.txt, test1.txt.2
        if(result[-1].isInteger() && result.size()>2) {
            result = result[0..-3] + [result[-1], result[-2] ]
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
