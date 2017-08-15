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

import groovy.transform.CompileStatic;
import java.util.regex.Pattern

/**
 * Metadata about a branch within a pipeline
 * <p>
 * Behaves as a String via delegation to String variable, but
 * extends Expando so that the user can set arbitrary properties,
 * which get carried between pipeline stages.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Branch extends Expando implements Serializable {
    
    private static Pattern REMOVE_TRAILING_DOT_SECTIONS_PATTERN = ~'\\.[^.]*$'
    
    public static final long serialVersionUID = 0L
    
    @Delegate
    String name = ""
    
    String dir = "."
    
    Branch parent = null
    
    transient Closure dirChangeListener = null
    
    Branch getTop() { // Causes compile to fail :-(
        if(parent == null) {
            return this
        }
        else {
            return parent.top
        }
    }
    
    public void setParent(Branch parent) {
        // Copy values of properties 
        parent.properties.each { entry ->
            if(entry.key != 'name')
                this.setProperty(entry.key, entry.value)
        }
        this.parent = parent
    }
    
    @Override
    String toString() {
        name
    }
    
    /**
     * Search upwards until we find a name that can be used to identify the branch
     * that isn't blank and isn't numeric. This is designed for generating a user 
     * recognisable identifier that can trace the origin of this branch.
     * 
     * @return identifier for the ancestry of this branch
     */
    @CompileStatic
    String getFirstNonTrivialName() {
        Branch parent = null;
        
        String sanitisedPipelineName = name?.replaceAll(REMOVE_TRAILING_DOT_SECTIONS_PATTERN,'');
       
        if(sanitisedPipelineName && !sanitisedPipelineName.isNumber())
            return sanitisedPipelineName
            
        if(parent == null)
            return sanitisedPipelineName // if no parent then we're stuck with whatever we have
            
        return parent.getFirstNonTrivialName()
    }
    
    void setDir(String dir) {
        this.dir = dir;
        if(this.dirChangeListener != null)
            this.dirChangeListener(dir)
    }
    
    void setProperty(String name, Object value) {
        
        if((name in PipelineCategory.closureNames.values()) && !(value instanceof Closure)) {
            throw new PipelineError("""
                Attempt to define a branch variable $name with same name as a pipeline stage. 
            
                Please ensure that pipeline stages have different names to branch variables.
            """.stripIndent())
        }
        else {
            if(name == 'dirChangeListener') {
                this.dirChangeListener = value
            }            
            else
            if(name == 'dir') {
                this.dir = value
                if(this.dirChangeListener != null)
                    this.dirChangeListener(value)
            }
            super.setProperty(name,value)
        }
    }
}
