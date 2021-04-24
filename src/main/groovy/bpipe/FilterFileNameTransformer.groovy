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

import java.util.List;

/**
 * A FileNameTransformer that preserves the file extension while
 * adding a middle segment to indicate the output file is derived
 * from the  input file.
 * 
 * @author Simon Sadedin
 */
@Log
class FilterFileNameTransformer implements FileNameTransformer {
    
    /**
     * Names of filters (prepended prior to extension)
     */
    List<String> types
    
    /**
     * Extensions of files on which this filter was applied
     */
    List<String> exts = []
    
    
    List<Branch> inboundBranches
    
    /**
     * Whether the name of the current pipeline branch has been incorporated into the 
     * output files of the branch yet.
     */
    boolean nameApplied = false

    @Override
    public List<PipelineFile> transform(List<PipelineFile> inputs) {
        
        log.info("Inputs to transform: " + inputs.join(','))
       
        Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
        
        if(!inputs)
            throw new PipelineError("A pipeline stage was specified as a filter, but the stage did not receive any inputs")
            
        def typeCounts = [:]
        for(def e in types)
            typeCounts[e] = 0
      
        def files = types.collect { String type ->
            PipelineFile inp = inputs[typeCounts[type] % inputs.size()]
            if(!inp)
               throw new PipelineError("Expected input but no input provided")
               
            log.info "Filtering based on input of type $type (instance ${typeCounts[type]}) $inp"
            
            typeCounts[type]++
            String oldExt = (inp =~ '\\.[^\\.]*$')[0]
            PipelineFile result 
            String inputPath = inp.name
            boolean branchInName = inputPath.contains('.' + pipeline.name + '.') || inputPath.startsWith(pipeline.name + '.')
            
//            println "Branch $pipeline.name is in file name $inputPath? " + branchInName
            if(!nameApplied && !branchInName) {
                // Arguably, we should add the filter type to the name here as well.
                // However we're already adding the branch name, so the filename is already
                // unique at this point, and we'd like to keep it short
                result = inp.newName(inputPath.replaceAll('\\.[^\\.]*$','.'+pipeline.name+/*'.'+ type+*/oldExt))
            }
            else
            // When the user has specified this stage as a merge point AND the filter is designated as 'merge',
            // then we do NOT add in a redundant 'merge' because it will get added below due to there being inbound
            // branches. 
            if(!inboundBranches || (type != 'merge'))
                result = inp.newName(inputPath.replaceAll('(\\.[^\\.]*$)','.'+type+oldExt))
            else
                result = inp // guaranteed to enter the block below and reassign, so we will not actualy end up with the input as the output(!)
                
            // If the pipeline merged, we need to excise the old branch names
            if(inboundBranches) {
                for(Branch branch in inboundBranches) {
                    log.info "Excise inbound merging branch reference $branch.name due to merge point in parent branch $pipeline.name"
//                    println "Excise inbound merging branch reference $branch.name due to merge point in parent branch $pipeline.name"
                    result = result.newName(result.path.replace('.' + branch.name + '.','.merge.'))
                }
            }
            else {
                log.info "No inbound branches for filter"
            }
  
            return result
        }
        
        log.info "Filtering using $types produces outputs $files"
        
        if(nameApplied)
            pipeline.nameApplied = true

        return files
        
    }
}
