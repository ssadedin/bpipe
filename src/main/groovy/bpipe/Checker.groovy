/*
 * Copyright (c) 2014 MCRI, authors
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

@Log
class Checker {
    
    // Directory in which checks will be stored
    final static CHECK_DIR = new File(".bpipe/checks")
    
    static {
        CHECK_DIR.mkdirs()
    }
    
    Closure check
    
    PipelineContext ctx

    public Checker(PipelineContext ctx, Closure check) {
        this.check = check
        this.ctx = ctx
    }
    
    void otherwise(Closure otherwiseClause) {
        log.info("Evaluating otherwise clause")
        def checkName = ctx.branch + "." + ctx.stageName
        File checkFile = new File(CHECK_DIR, checkName) 
        boolean passed = false
        
        // If the check is up-to-date then simply read its result from the check file
        def inputs = Utils.box(ctx.@input) + ctx.resolvedInputs
        if(checkFile.exists() && Dependencies.instance.checkUpToDate(checkFile.absolutePath, inputs)) {
            log.info "Check $checkName was already executed and is up to date with inputs $inputs"
            passed = Boolean.parseBoolean(checkFile.text)
            log.info "Cached result of $checkName was $passed"
        }
        else
        try { // Check either had not executed, or was not up-to-date
            log.info "Executing check: checkFile=$checkFile.absolutePath, exists=${checkFile.exists()}"
            
            List oldOutputs = ctx.@output
            
            check()
            
            // A check can modify the outputs even if it doesn't reference any
            ctx.setRawOutput(oldOutputs)
            
            passed = true
        }
        catch(CommandFailedException e) {
            log.info "Check $checkName was executed and failed ($e)"
            passed = false
        }
        
        // Store the result of the check in the check file so we can read it back and not keep
        // re-checking it each time
        // Don't store it in test mode - in that case the command didn't really run
        if(!Runner.opts.t && !ctx.probeMode) {
            checkFile.text = String.valueOf(passed)
        }
        
        // Execute result of check
        if(!passed)
          otherwiseClause()
    }
}
