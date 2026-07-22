/*
 * Copyright (c) 2012 MCRI, authors
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

import java.util.logging.Level

import groovy.transform.CompileStatic
import groovy.util.logging.Log

@CompileStatic
@Log
class BranchRunner implements Runnable {
    
    final Pipeline parent
    
    final Pipeline child
    
    final List<PipelineStage> priorStages
    
    final String childName
    
    final Closure segmentClosure
    
    final boolean applyName
    
    public BranchRunner(Pipeline parent, Pipeline child, List<PipelineStage> priorStages, String childName, Closure segmentClosure, boolean applyName) {
        super();
        this.parent = parent;
        this.child = child;
        this.priorStages = priorStages;
        this.childName = childName;
        this.segmentClosure = segmentClosure;
        this.applyName = applyName;
    }

    @Override
    public void run() {
        try {
            for(PipelineStage stage in priorStages) {
                child.addStage(stage)
            }
            child.initBranch(childName, !applyName)
            child.branch.setParent(parent.branch)
            
            List<PipelineFile> files = priorStages.isEmpty() ? [] : (List<PipelineFile>)Utils.box(priorStages[-1].context.@output)
            child.runSegment(files, segmentClosure)
            child.finished = true
        }
        catch(Exception e) {
            log.log(Level.SEVERE,"Pipeline segment in thread " + Thread.currentThread().name + " failed with internal error: " + e.message, e)
            println(Utils.prettyStackTrace(e))
            child.failExceptions << e
            child.failed = true
        }        
    }
}
