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
    
    /**
     * Whether the name of the current pipeline branch has been incorporated into the 
     * output files of the branch yet.
     */
    boolean nameApplied = false

    @Override
    public List<PipelineFile> transform(List<PipelineFile> inputs,  boolean applyName) {
        
        this.nameApplied = applyName
        
        def pipeline = Pipeline.currentRuntimePipeline.get()
        
        if(!inputs)
            throw new PipelineError("A pipeline stage was specified as a filter, but the stage did not receive any inputs")
            
        def typeCounts = [:]
        for(def e in types)
            typeCounts[e] = 0
      
        def files = types.collect { String type ->
            def inp = inputs[typeCounts[type] % inputs.size()]
            if(!inp)
               throw new PipelineError("Expected input but no input provided")
               
            log.info "Filtering based on input $inp"
            
            typeCounts[type]++
            String oldExt = (inp =~ '\\.[^\\.]*$')[0]
            if(applyName) {
                // Arguably, we should add the filter type to the name here as well.
                // However we're already adding the branch name, so the filename is already
                // unique at this point, and we'd like to keep it short
                // TODO: perhaps only leave out filter when it's chromosome ? not sure
                return inp.newName(inp.path.replaceAll('\\.[^\\.]*$','.'+pipeline.name+/*'.'+ type+*/oldExt))
            }
            else
                return inp.newName(inp.path.replaceAll('(\\.[^\\.]*$)','.'+type+oldExt))
        }
        
        log.info "Filtering using $types produces outputs $files"
        
        if(applyName)
            pipeline.nameApplied = true

        return files
        
    }
}
