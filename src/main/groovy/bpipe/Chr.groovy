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

@Log
class Chr implements Comparable<Chr> {
	
	static List<Chr> knownValues = []
    
    Map config
	
	Chr(String name, Map config) {
		this.name = name
        this.config = config;
		String postfix = name.replaceAll("^chr", "")
		if(postfix.matches(/^[0-9]*/)) {
			this.ordinal = Integer.parseInt(postfix)
		}
		else {
			this.ordinal = -1
		}
		
		if(!knownValues.contains(this)) {
			knownValues.add(this)
			knownValues.sort()
		}
	}
	
	String name
	
	Integer ordinal
	
	public boolean equals(Object o) {
		if(!(o instanceof Chr))
			return false
		return o.name == this.name
	}

	@Override
	public int compareTo(Chr o) {
		if(ordinal >= 0 && o.ordinal >=0)
			return ordinal.compareTo(o.ordinal)
		else 
		if(this.ordinal < 0 && o.ordinal >= 0) {
			return 1;
		}
		else
		if(this.ordinal >= 0 && o.ordinal < 0) {
			return -1;
		}
		else
			return this.name.compareTo(o.name)
	}
	
	public Chr next() {
		int index = knownValues.indexOf(this)
		if(index < knownValues.size()-1)
			return null
		return knownValues[index+1]		
	}
	
	public Chr prev() {
		int index = knownValues.indexOf(this)
		if(index < 1)
			return null
			
		return knownValues[index-1]
	}
    
    RegionValue getRegion() {
        return new RegionValue(name)
    }
}
