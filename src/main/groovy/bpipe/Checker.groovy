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
import java.util.regex.Pattern

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
    
    boolean executed = false

    public Checker(PipelineContext ctx, Closure check) {
        this.checkClosure = check
        this.ctx = ctx
    }
    
    public Checker(PipelineContext ctx, String name, Closure check) {
        this.checkClosure = check
        this.ctx = ctx
        this.name = name
    }
    
    private static Pattern EXCLUDED_CHECK_NAME_STAGES = ~'_.*inputs$'
    
    /**
     * Looks if a check is up to date with inputs or not, and if not, executes the check.
     * If it fails, the otherwise clause of the check construct is executed.
     * 
     * @param otherwiseClause
     */
    void otherwise(Closure otherwiseClause) {
        
        log.info("Evaluating Check $ctx.stageName / $name")
        
        this.executed = true
        
        Check check = getCheck()
        
        log.info "Check name = $check.name"
        
        check.pguid = Config.config.pguid
        check.script = Config.config.script
        
        // If the check is up-to-date then simply read its result from the check file
        def inputs = Utils.box(ctx.@input) + ctx.resolvedInputs
        if(check.isUpToDate(inputs)) {
            log.info "Check ${check.toString()} was already executed and is up to date with inputs $inputs"
            log.info "Cached result of ${check} was Passed: ${check.passed} Overridden: ${check.override}"
        }
        else {
            log.info "Check ${check.toString()} was not already executed or not up to date with inputs $inputs"
            this.executeCheck(check)
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
    
    /**
     * Execute the given check, saving its results in the check's corresponding file
     * 
     * @param check     the Check to execute
     */
    void executeCheck(Check check) {
        
        PipelineStage pipelineStage = ctx.getCurrentStage()

        try { // Check either had not executed, or was not up-to-date
            
            EventManager.instance.signal(PipelineEvent.CHECK_EXECUTED, 
                 "Executing check $check.name", 
                 check.getEventDetails(pipelineStage))
  
            runCheckClosure()
            
            check.passed = true
			check.state = 'pass'
            
            EventManager.instance.signal(PipelineEvent.CHECK_SUCCEEDED, 
                "Check ${check.name? /'$check.name'/:''} for stage $pipelineStage.displayName passed", 
                check.getEventDetails(pipelineStage)
            ) 
            
            if(!Runner.opts.t && !ctx.probeMode) {
                check.save()
            }
        }
        catch(CommandFailedException e) {
            log.info "Check $check was executed and failed ($e)"
            check.passed = false
			check.state = 'review'
            check.save()
            EventManager.instance.signal(PipelineEvent.CHECK_FAILED, 
                "Check ${check.name? /'$check.name'/:''} for stage $pipelineStage.displayName failed", 
                check.getEventDetails(pipelineStage)
            )             
        }
    }
    
    /**
     * Run the closure for the check
     * <p>
     * Note: the closure throws an exception if the check fails. If no exception
     * is thrown, the check passed.
     */
    void runCheckClosure() {
        
        check.executed = new Date()
            
        File checkFile = check.getFile(check.stage, check.name, check.branch)
        log.info "Executing check: $check with status in file: $checkFile"
		
        List oldOutputs = ctx.@output
		ctx.internalOutputs = [checkFile.path]
		ctx.internalInputs = Utils.box(ctx.@output)
		try {
            checkClosure()
		}
		finally {
            // A check can modify the outputs even if it doesn't reference any
            // Make sure it doesn't affect what gets passed on to next stage
            ctx.setRawOutput(oldOutputs)
			ctx.internalOutputs = []
			ctx.internalInputs = []
		}
    }
    
    /**
     * Look for a saved execution of this check, and if there is one, return it
     * If there is no saved check, create a new, unsaved one.
     * 
     * @return  a Check object representing the context, branch and stage being checked.
     */
    Check getCheck() {
        if(isLegacyCheck(ctx.stageName, ctx.branch.toString())) {
            check = Check.getCheck(ctx.stageName, this.name, ctx.branch.toString())
        }
        else {
            Pipeline pipeline = Pipeline.currentRuntimePipeline.get()
            
            String stagePath = ctx.pipelineStages.grep { 
                it.stageName && it.stageName != "Unknown" && !it.stageName.matches(EXCLUDED_CHECK_NAME_STAGES) 
            }*.stageName.join("_")
            
            String branchPath = pipeline.branchPath.join("_") + '_' + stagePath
            if(branchPath.size() > 60) {
                branchPath = Utils.sha1(branchPath) + "_" + ctx.branch.toString()
            }
            log.info "Computing check based on branchPath $branchPath"
            check = Check.getCheck(ctx.stageName, this.name, branchPath);
            check.branch = pipeline.branchPath.grep { it != 'all' }.reverse().join('/')
            return check
        }
    }
    
    /**
     * Legacy checks were saved with only the branch name, rather than full branch path.
     * 
     * @param   stageName
     * @param   branch
     * @return  true iif a saved, legacy check exists the given stage name and branch.
     */
    boolean isLegacyCheck(String stageName, String branch) {
        return Check.getFile(ctx.stageName, this.name, ctx.branch.toString()).exists()
    }
    
}
