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

import bpipe.storage.LocalPipelineFile
import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Carries the state of a 'check' construct in Bpipe
 * 
 * @author Simon Sadedin
 */
@Log
class Check {
    
    String pguid
    
    String branch
    
    String branchHash
    
    String stage
    
    String name
    
    String script
    
    Date executed
    
    boolean passed = false
    
	String comment
	
	/**
	 * A check is in one of 4 states:
	 * 
	 *  <li> new
	 *  <li> pass
	 *  <li> review
	 *  <li> fail
	 *  
	 */
	String state = 'new'
    
    String message
    
    boolean override = false
    
    boolean autoSave = false

    public Check() {
        this.pguid = Config.config.pguid
        this.script = Config.config.script
    }
	
	Map getEventDetails(PipelineStage pipelineStage=null) {
	    Map details = [
                name: this.name,
                message: this.message,
                pguid: this.pguid,
				result: this.passed || this.override,
                script: this.script,
                stageName:this.stage,
				stage: [stageName: this.stage],
                branch: this.branch,
                passed: this.passed,
                override: this.override,
                executed: this.executed?.time,
                state: this.state,
				comment: this.comment
        ]		
		
		if(pipelineStage)
			details.stage = pipelineStage
		else 
			details.stageName = this.stage
			
		return details
	}
    
    void save() {
        
        Properties p = new Properties()
        p.branch = branch
        if(branchHash)
            p.branchHash = branchHash
        p.stage = stage
        p.passed = passed ? "true" : "false"
        
        if(message)
            p.message = message
        
        if(executed)
            p.executed = String.valueOf(executed.getTime())
        
        if(this.name)
            p.name = this.name
                
        
        if(override != null)
            p.override = override.toString()
            
        p.setProperty('pguid', pguid)
        p.script = script
		p.state = state
        
        if(comment != null)
            p.comment = comment
        
        p.store(getFile(stage,name,branchHash).newWriter(), "Bpipe Check Properties")
    }
    
    /**
     * Return true if this check is up-to-date with respect to the given inputs
     */
    boolean isUpToDate(List<PipelineFile> inputs) {
       File checkFile = getFile(stage, name, branchHash)
       if(!checkFile.exists()) {
           return false
       }
       
       if(Dependencies.instance.getOutOfDate([new LocalPipelineFile(checkFile.absolutePath)], inputs)) {
           return false
       }
       
       return true
    }
    
    /**
     * Either load or create a new Check appropriate to the given stage name and branch
     */
    static Check getCheck(String stageName, String name, String branchHash) {
        File file = getFile(stageName, name, branchHash)
        if(file.exists()) {
            new Check(branchHash:branchHash).load(file)
        }
        else {
            return new Check(stage: stageName, name:name, branchHash: branchHash, passed: false)
        }
    }
    
    /**
     * Return a file for storing a check based on the branch and stage 
     * name
     */
    static File getFile(String stageName, String name, String branchHash) {
        if(branchHash == "")
            branchHash = "all"
        def checkName = FastUtils.dotJoin(branchHash,stageName,name?.toLowerCase()?.replaceAll(" ","_"),"properties")
        return new File(Checker.CHECK_DIR, checkName) 
    }
    
    /**
     * Load the details of a check from its properties file
     * 
     * @param file
     * @return
     */
    Check load(File file) {
        Properties p = new Properties()
        file.withReader { r -> p.load(r) }
        this.branch = p.branch
        this.stage = p.stage
        this.executed = p.executed ? new Date(p.executed.toLong()) : null
        this.message = p.message ?: null
        this.passed = Boolean.parseBoolean(p.passed)
        this.override = ((p.override != null) ? Boolean.parseBoolean(p.override) : false)
        this.pguid = p.pguid ?: null
        if(p.containsKey("name"))
            this.name = p.name
        this.script = p.script ?: null
		this.state = p.state  
		if(this.state == null) { // legacy checks
			this.state = (this.passed || this.override) ? 'succeeded' : 'failed'
		}
        this.comment = p.comment
        this.branchHash = p.branchHash
        return this
    }
    
    void setMessage(String value) {
        boolean modified = (value != this.message)
        this.message = value
        if(autoSave && modified) {
            this.save()
        }
    }
    
    void setBranch(String value) {
        if(value == "" || value == null)
            value = "all"
        this.branch = value
    }
    
    void overrideCheck(boolean result, String comment=null) {
        log.info "Override me: pguid = $pguid"
        this.override = true; 
        this.comment = comment
		if(result) {
			this.state = 'pass'
		}
		else {
			this.state = 'fail'
		}
        this.save() 
        EventManager.instance.signal(PipelineEvent.CHECK_OVERRIDDEN, "Check overridden", getEventDetails())
    }
    
    /**
     * Load all the checks for the current directory
     * 
     * @return
     */
    static List<Check> loadAll(File dir=Checker.CHECK_DIR) {
        def allFiles = dir.listFiles().grep { File f -> f.name.endsWith(".properties") }
        return allFiles.collect { new Check().load(it) }
    }

    String toString() {
        "Check $name for stage $stage in branch $branch"
    }
}
