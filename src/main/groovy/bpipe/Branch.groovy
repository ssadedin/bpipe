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

class Branch extends Expando implements Comparable {
    
    @Delegate
    String name = ""
    
    Branch parent = null
    
    @CompileStatic
    Branch getTop() {
        if(parent == null) {
            return this
        }
        else {
            return parent.top
        }
    }
    
    /*
     * Not working?
    @Override
    public boolean equals(Object other) {
        if(other instanceof Branch)  
            return this.name == other?.name
            
        return this.name == other
    }
    */
    
    public void setParent(Branch parent) {
        // Copy values of properties 
        parent.properties.each { entry ->
            this.setProperty(entry.key, entry.value)
        }
    }

    /*
    @Override
    public int hashCode() {
        return name.hashCode();
    }



    @Override
    public int compareTo(Object o) {
        if(this.name != null)
            this.name.compareTo(o)
        else
            return o == this.name
    }
    */
}
