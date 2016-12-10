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

class Check {
    
    String branch
    
    String stage
    
    String name
    
    Date executed
    
    boolean passed = false
    
    String message
    
    boolean override = false
    
    boolean autoSave = false

    public Check() {
    }
    
    void save() {
        
        Properties p = new Properties()
        p.branch = branch
        p.stage = stage
        p.passed = passed ? "true" : "false"
        
        if(message)
            p.message = message
        
        if(executed)
            p.executed = String.valueOf(executed.getTime())
        
        if(this.name)
            p.name = this.name
                
        p.override = override.toString()
        p.store(getFile(stage,name,branch).newWriter(), "Bpipe Check Properties")
    }
    
    /**
     * Return true if this check is up-to-date with respect to the given inputs
     */
    boolean isUpToDate(def inputs) {
       File checkFile = getFile(stage, name, branch)
       checkFile.exists() && Dependencies.instance.checkUpToDate([checkFile.absolutePath], inputs) 
    }
    
    /**
     * Either load or create a new Check appropriate to the given stage name and branch
     */
    static Check getCheck(String stageName, String name, String branch) {
        File file = getFile(stageName, name, branch)
        if(file.exists()) {
            return new Check().load(file)
        }
        else {
            return new Check(stage: stageName, name:name, branch: branch, passed: false)
        }
    }
    
    /**
     * Return a file for storing a check based on the branch and stage 
     * name
     */
    static File getFile(String stageName, String name, String branch) {
        if(branch == "")
            branch = "all"
        def checkName = FastUtils.dotJoin(branch,stageName,name?.toLowerCase()?.replaceAll(" ","_"),"properties")
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
        if(p.containsKey("name"))
            this.name = p.name
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
    
    /**
     * Load all the checks for the current directory
     * 
     * @return
     */
    static List<Check> loadAll() {
        def allFiles = Checker.CHECK_DIR.listFiles().grep { it.name.endsWith(".properties") }
        return allFiles.collect { new Check().load(it) }
    }
    
    String toString() {
        "Check $name for stage $stage in branch $branch"
    }
}
