package bpipe

import java.util.zip.GZIPInputStream;

import groovy.util.logging.Log;

@Log
class RegionSet implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    String name

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
        this.sequences[seq.name] = seq
    }
    
    RegionSet(List<Sequence> seqs) {
        for(Sequence s in seqs) {
            addSequence(s)
        }
    }

    /**
     * Read a tab separated file in the format provided by UCSC for genes
     * to infer a genome model as a set of regions.
     */
    static RegionSet index(InputStream stream) {

        RegionSet g = new RegionSet()
        int count = 0
        new GZIPInputStream(stream).eachLine { line ->
            if(count %1000 == 0)
                log.info "Processing line $count"
            List cols = line.split("\t")
            // println  "Gene name = " + cols[12] + " Chr = " + cols[2] + " tx start = " + cols[4] + " tx end = " + cols[5]
            String chr = cols[2]
            Sequence s = g.sequences[chr]
            if(!s) {
                s = new Sequence(name:chr)
                g.sequences[chr] = s
            }
            s.add(cols[12], cols[4] as int, cols[5] as int)
            ++count
        }
        return g
    }
    
    static RegionSet load(File file) {
        new FileInputStream(file).withStream { new ObjectInputStream(it).readObject() }
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
     * A synonym for {@link #group(int)}
     */
    Set<RegionSet> split(int parts) {
        group(parts)
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
        
        // A sorted set ordered by size and then object to 
        SortedSet<RegionSet> results = new TreeSet({ a,b -> b.size().compareTo(a.size())?:a.hashCode().compareTo(b.hashCode())} as Comparator)
        
        // We start with a new RegionSet for each sequence
        results.addAll(sequences.collect { new RegionSet(it.value) })
        
        log.info "Grouping ${results.size()} sequences into $parts groups"
        
        // While the number of parts is too large we should combine smaller ones together
        log.info "*** Combining regions to decrease to $parts parts"
        while(results.size() > parts) {
           combineSmallest(results)
        }
        
        // While number of parts too small, split apart large sequences
        log.info "*** Splitting regions to increase to $parts parts"
        while(results.size() < parts) {
            // Split the largest region set into two
            if(!splitLargest(results))
                break
        }
        
        // While number of parts too uneven, split largest part, invoke
        // above loop again to reduce down 
        while(results.first().size() > 2*results.last().size()) {
            log.info "*** Rebalancing regions due to ${results.first().size()} > 2 x ${results.last().size()}"
            if(!splitLargest(results))
                break
            combineSmallest(results)
        }
        results.eachWithIndex { r,i -> r.name = name + "."+i }
        return results
    }
    
    boolean splitLargest(SortedSet results) {
        
        RegionSet largest = results.first()
        
        def (large,small) = largest.splitInTwo()
        float ratio = (float)large.size() / (float)small.size()
        
        assert ratio > 1.0f
        if(ratio > 0.1 && ratio < 10) {
            results.remove(largest)
            
            log.info "Splitting largest region sof size ${largest.size()} into parts of size [${large.size()}, ${small.size()} (ratio=$ratio) to increase parallelism to ${results.size()+2}"
            
            results.add(large)
            results.add(small)
            return true
        }
        else 
            return false
    }
    
    /**
     * Return a RegionSet that is this RegionSet split into two RegionSets 
     * containing approximately the same amount of genomic sequence.
     * <p>
     * This works in two passes. First it tries to do the split simply by 
     * sorting whole sequences into two separate RegionSet objects.  If that 
     * produces a result where the ranges are within a factor of 2 then it is 
     * accepted as the result. This implements a strong preference to maintain
     * whole sequences if possible.
     * <p>
     * However if the regions are unbalanced by more than a factor of two
     * then the algorithm tries to divide one of the sequences into two. It 
     * selects the largest sequence in the largest region and tries to split it.
     * If that produces a split of more than 10% it is accepted, but if the split
     * itself is very unbalanced then the next sequence is tried until it runs out. 
     * It is possible no split is possible that will return a balanced result 
     * in which case the algorithm gives up and returns the unbalanced split.
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
        
        List<Sequence> ordered2 = ([] + result1.sequences.values()).sort { it.size() }.reverse()
        
        // This loop exits when every sequence from result1 has been tried as a 
        // split candidate OR when the results are sufficiently balanced
        while(result1.size() > 2*result2.size()) {
            // Take the largest sequence left that divides nicely from sequence1 and split it
            while(ordered2) {
                Sequence largest = ordered2[0]
                ordered2.remove(largest)
                
                List split = largest.split()
                if(split[0].size() && split[1].size()) {
                    float ratio = (float)split[0].size()/(float)split[1].size()
                    if(ratio > 0.1f && ratio < 10f) {
                        log.info "Sequence $largest split to ${split[0]} and ${split[1]}"
                        result1.removeSequence(largest)
                        
                        // We keep preferencing result1 as the largest region
                        if(split[0].size()>split[1].size()) {
                            result1.addSequence(split[0])
                            result2.addSequence(split[1])
                        }
                        else {
                            result1.addSequence(split[1])
                            result2.addSequence(split[0])
                        }
                        break
                    }
                    else
                        log.info "Ratio $ratio of split ${split[0]} and ${split[1]} to low/high to justify"
                }
            }
            if(!ordered2)
                break
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

		log.info("Combining regions $smallest and $secondSmallest to reduce parallelism to ${results.size()+1}")

		// Combine the two smallest and add them back in as a single RegionSet
		results.add(new RegionSet(smallest.sequences.values() + secondSmallest.sequences.values()))
	}
    
    long size() {
        // Note: we don't use built in sum() because of worry about int overflow
        long result = 0
        this.sequences.each { result += it.value.size() }
        return result
    }
    
    String toString() {
        "RegionSet[sequences=${sequences.values()}]"
    }
    
    RegionValue getRegion() {
        new RegionValue(value:this.sequences.values().collect { Sequence s -> "$s.name:$s.range.from-$s.range.to" }.join(" "))
    }
}
