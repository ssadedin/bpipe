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
    
    Closure checkClosure
    
    Check check = null
    
    PipelineContext ctx
    
    String name

    public Checker(PipelineContext ctx, Closure check) {
        this.checkClosure = check
        this.ctx = ctx
    }
    
    public Checker(PipelineContext ctx, String name, Closure check) {
        this.checkClosure = check
        this.ctx = ctx
        this.name = name
    }
    
    void otherwise(Closure otherwiseClause) {
        log.info("Evaluating otherwise clause for Check $ctx.stageName / $name")
        
        Check check = Check.getCheck(ctx.stageName, this.name, ctx.branch.toString())
        
        log.info "Check name = $check.name"
        
        // If the check is up-to-date then simply read its result from the check file
        def inputs = Utils.box(ctx.@input) + ctx.resolvedInputs
        if(check.isUpToDate(inputs)) {
            log.info "Check ${check.toString()} was already executed and is up to date with inputs $inputs"
            log.info "Cached result of ${check} was Passed: ${check.passed} Overridden: ${check.override}"
        }
        else
        try { // Check either had not executed, or was not up-to-date
            log.info "Executing check: $check"
            
            List oldOutputs = ctx.@output
            
            check.executed = new Date()
            
            checkClosure()
            
            // A check can modify the outputs even if it doesn't reference any
            // Make sure it doesn't affect what gets passed on to next stage
            ctx.setRawOutput(oldOutputs)
            
            check.passed = true
            if(!Runner.opts.t && !ctx.probeMode) {
                check.save()
            }
        }
        catch(CommandFailedException e) {
            log.info "Check $check was executed and failed ($e)"
            check.passed = false
            check.save()
        }
        
        // Execute result of check
        if(!check.passed && !check.override) {
            ctx.currentCheck = check
            try {
              ctx.currentCheck.autoSave = true
              otherwiseClause()
            }
            finally {
                ctx.currentCheck.autoSave = false
                ctx.currentCheck = null
            }
        }
    }
}
