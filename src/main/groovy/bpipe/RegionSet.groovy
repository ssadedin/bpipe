package bpipe

import groovy.util.logging.Log;

@Log
class RegionSet {

    /**
     * Set of sequences belonging to this RegionSet.
     * Note that the same chromosome can be in here twice with 
     * different regions.  When that happens, the index in the map becomes
     * the chromosome name with a number appended, eg:
     * chrY, chrY.1, chrY.2, etc.
     */
    Map<String,Sequence> sequences = [:]
    
    RegionSet() {
    }
    
    RegionSet(Sequence seq) {
        this.chromosomes[seq.name] = seq
    }
    
    RegionSet(List<Sequence> seqs) {
        for(Sequence s in seqs) {
            addSequence(s)
        }
    }

    static RegionSet index(File file) {

        RegionSet g = new RegionSet()

        file.splitEachLine(~/\t/) { cols ->

            // println  "Gene name = " + cols[12] + " Chr = " + cols[2] + " tx start = " + cols[4] + " tx end = " + cols[5]

            String chr = cols[2]
            Sequence s = g.sequences[chr]
            if(!s) {
                s = new Sequence(name:chr)
                g.sequences[chr] = s
            }
            s.add(cols[12], cols[4] as int, cols[5] as int)
        }
        return g
    }
    
    /**
     * Adds names the given sequence to this region set
     */
    void addSequence(Sequence s) {
        String name = s.name
        int count = 0
        while(this.sequences.containsKey(name)) {
            name = s.name + "." + (++count)       
        }
        this.sequences[name] = s    
    }
    
    /**
     * Group this set of regions into <code>num</code> pieces for processing.
     * 
     * Grouping involves both combining separate sequences within the genome
     * as well as potentially splitting sequences to arrivce at the right
     * number of parts. The algorithm attempts to arrive at <code>num</code>
     * most equal pieces using the smallest possible number of divisions.
     * 
     * @param num     Number of parts to split the genome into
     * @return        A set of RegionSet objects representing the given
     *                genome split into the requested number of parts
     */
    Set<RegionSet> group(int parts) {
        
        // We start with a new RegionSet for each sequence
        SortedSet<RegionSet> results = new TreeSet({ a,b -> a.size() < b.size()})
        results.addAll(sequences.collect { new RegionSet(it.value) })
        
        // While the number of parts is too large we should combine smaller ones together
        while(results.size() > parts) {
           combineSmallest(results)
        }
        
        // TODO: While number of parts too small, split apart large sequences
        while(results.size() < parts) {
            // Split the largest region set into two
            splitLargest(results)
        }
        
        // TODO: While number of parts too uneven, split largest part, invoke
        // above loop again (refactor!) to reduce down 
    }
    
    void splitLargest(SortedSet results) {
        
        RegionSet largest = results.first()
        
        List result = largest.splitInTwo()
        results.remove(largest)
        
        log.info "Splitting largest region sof size ${largest.size()} into parts of size [${results[0].size()}, ${results[1].size()} to increase parallelism to ${results.size()+1}"
        
        results.add(result[0])
        results.add(result[1])
    }
    
    /**
     * Return a RegionSet that is this RegionSet split into two RegionSets 
     * containing approximately the same amount of genomic sequence.
     */
    List splitInTwo() {
        List<Sequence> ordered = ([] + sequences.values()).sort { it.size() }.reverse()
        
        println "Sequences ordered by size are $ordered"
        
        RegionSet result1 = new RegionSet()
        RegionSet result2 = new RegionSet()
        while(ordered) {
            Sequence s = ordered.first()
            ordered.remove(s)
            if(result1.size() <= result2.size())
                result1.addSequence(s)
            else
                result2.addSequence(s)
        }
        
        if(result1.size() > 2*result2.size()) {
            // Take the largest sequence from result 1 and split it in two
            Sequence largest = result1.sequences.max { it.value.size() }.value
            List split = largest.split()
            log.info "Sequence $largest split to ${split[0]} and ${split[1]}"
            result1.removeSequence(largest)
            result1.addSequence(split[0])
            result2.addSequence(split[1])
        }
        
        return [result1,result2]
    }
    
    void removeSequence(Sequence s) {
        def entry = this.sequences.find { it.value.is(s) }
        if(entry == null)
            throw new IllegalArgumentException("Cannot remove sequence $s from region set $this: sequence not part of region")
            
        this.sequences.remove(entry.key)
    }

    /**
     * Find the two smallest region sets in the results and replace them with a 
     * combined region set that contains the regions in both of them.
     */
	 void combineSmallest(SortedSet results) {
        // Find the smallest genome
		RegionSet smallest = results.last()
		results.remove(smallest)

		RegionSet secondSmallest = results.last()
		results.remove(secondSmallest)

		log.info("Combining regions $smallest and $secondSmallest to reduce parallelism to ${results.size()-1}")

		// Combine the two smallest and add them back in as a single genome
		results.add(new RegionSet(smallest.sequences.values() + secondSmallest.values.collection()))
	}
    
    long size() {
        // Note: we don't use built in sum() because of worry about int overflow
        long result = 0
        this.sequences.each { result += it.value.size() }
        return result
    }
}
