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

/**
 * Metadata about a branch within a pipeline
 * <p>
 * Behaves as a String via delegation to String variable, but
 * extends Expando so that the user can set arbitrary properties,
 * which get carried between pipeline stages.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Branch extends Expando {
    
    @Delegate
    String name = ""
    
    String dir = "."
    
    Branch parent = null
    
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
            this.setProperty(entry.key, entry.value)
        }
        this.parent = parent
    }
    
    @Override
    String toString() {
        name
    }
    
    void setProperty(String name, Object value) {
        if((name in PipelineCategory.closureNames.values()) && !(value instanceof Closure)) {
            throw new PipelineError("""
                Attempt to define a branch variable $name with same name as a pipeline stage. 
            
                Please ensure that pipeline stages have different names to branch variables.
            """.stripIndent())
        }
        else {
            super.setProperty(name,value)
        }
    }
}
