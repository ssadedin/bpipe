package bpipe

import groovy.util.logging.Log;

@Log
class Chr implements Comparable<Chr> {
	
	static List<Chr> knownValues = []
	
	Chr(String name) {
		this.name = name
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
        return new RegionValue(value:name)
    }
}
