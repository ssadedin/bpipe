package bpipe

import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

import groovy.transform.CompileStatic;
import groovy.util.logging.Log;


@Log
class RegionSet implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Optional configuration object - this is not used in RegionSet, however
     * it's used in Chr and for uniformity it's added here too. Later on
     * support for it can be extended to RegionSet objects too.
     */
    Map config
    
    /**
     * Name of this region set
     */
    String name
    
    /**
     * Optional id for this region set - if present, will be used to cache the regions to ensure
     * reproducible analyses
     */
    String id

    /**
     * Set of sequences belonging to this RegionSet.
     * Note that the same chromosome can be in here twice with 
     * different regions.  When that happens, the index in the map becomes
     * the chromosome name with a number appended, eg:
     * chrY, chrY.1, chrY.2, etc.
     */
    Map<String,Sequence> sequences = new TreeMap()
    
    /**
     * Cached set of chromosome names present in this RegionSet.
     * Maintained by addSequence() and removeSequence().
     */
    Set<String> chromosomeNames = new HashSet<String>()
    
    RegionSet() {
    }
    
    RegionSet(Sequence seq) {
        this.sequences[seq.name] = seq
        this.chromosomeNames.add(seq.name)
    }
    
    RegionSet(List<Sequence> seqs) {
        for(Sequence s in seqs) {
            addSequence(s)
        }
    }
    
    private static Pattern LEADING_CHR = ~/^chr/

    /**
     * Read a tab separated file in the format provided by UCSC for genes
     * to infer a genome model as a set of regions.
     * 
     * @param stream                input stream to read from
     * @param convertChromosomes    whether to strip 'chr' from chromosome names
     */
    static RegionSet index(InputStream stream, boolean convertChromosomes) {

        RegionSet g = new RegionSet()
        int count = 0
        new GZIPInputStream(stream).eachLine { line ->
            if(count %1000 == 0)
                log.info "Processing line $count"
            List cols = line.split("\t")
            // println  "Gene name = " + cols[12] + " Chr = " + cols[2] + " tx start = " + cols[4] + " tx end = " + cols[5]
            String chr = cols[2]
            
            if(convertChromosomes)
                chr = chr.replaceAll(LEADING_CHR, '')
            
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
    @CompileStatic
    void addSequence(Sequence s) {
        String name = s.name
//        int count = 0
//        while(this.sequences.containsKey(name)) {
//            name = s.name + "." + (++count)       
//        }
        this.sequences[s.toString()] = s
        this.chromosomeNames.add(s.name)
    }
    
    
    Set<RegionSet> split(List<Object> sequences, int parts) {
        
        List<String> sequenceValues = sequences*.toString()
        
        new RegionSet(this.sequences.grep { it.key in sequenceValues }*.value).split(parts)
    }
    
    /**
     * A synonym for {@link #group(int)}.
     * When options.sequential is true, delegates to {@link #splitSequential}.
     */
    Set<RegionSet> split(Map options=[:], int parts) {
        if (options.sequential)
            return new LinkedHashSet<RegionSet>(splitSequential(options, parts))
        group(options, parts)
    }

    /**
     * Split this RegionSet into sequential parts, keeping genomically adjacent regions
     * together. Unlike {@link #group}, this algorithm never mixes chromosomes within a
     * part and walks the genome in sorted order.
     *
     * A new part is started when any of the following is true:
     *   - a chromosome boundary is crossed
     *   - the gap to the next region exceeds options.maxDistance (if provided)
     *   - the current part is ≥75% of the target size and the next region would push it
     *     above 125% of the target (in which case the region is split at the exact
     *     fill-to-target point; the remainder begins the next part)
     *
     * @param options  optional map; supports maxDistance (int, bp)
     * @param parts    target number of parts (may be exceeded to honour constraints)
     * @return         list of RegionSets in genomic order
     */
    List<RegionSet> splitSequential(Map options=[:], int parts) {
        if (sequences.isEmpty())
            return []

        long totalSize = size()
        if (totalSize == 0)
            return [this]

        long targetSize = Math.max(1L, totalSize.intdiv(parts))
        int maxDistance = (options.maxDistance != null) ? (int)options.maxDistance : Integer.MAX_VALUE

        List<Sequence> ordered = new ArrayList<Sequence>(sequences.values())
        ordered.sort { a, b ->
            int cmp = a.name <=> b.name
            cmp != 0 ? cmp : a.range.from <=> b.range.from
        }

        List<RegionSet> result = []
        RegionSet current = new RegionSet()
        long currentSize = 0L
        String currentChrom = null
        Sequence lastSeq = null

        for (Sequence seq in ordered) {
            boolean chromBoundary = currentChrom != null && seq.name != currentChrom
            boolean distanceSplit = !chromBoundary && lastSeq != null &&
                                    (seq.range.from - lastSeq.range.to) > maxDistance

            if ((chromBoundary || distanceSplit) && !current.sequences.isEmpty()) {
                result.add(current)
                current = new RegionSet()
                currentSize = 0L
                lastSeq = null
            }

            currentChrom = seq.name
            long seqSize = (long)seq.size()
            Sequence addedToCurrent

            if (currentSize * 4L >= targetSize * 3L) {
                long combined = currentSize + seqSize
                if (combined * 4L > targetSize * 5L) {
                    long remaining = targetSize - currentSize
                    if (remaining > 0L && remaining < seqSize) {
                        int splitPoint = seq.range.from + (int)remaining
                        Sequence firstHalf = new Sequence(name: seq.name, range: new GenomicRange(seq.range.from..splitPoint))
                        Sequence secondHalf = new Sequence(name: seq.name, range: new GenomicRange(splitPoint..seq.range.to))
                        current.addSequence(firstHalf)
                        result.add(current)
                        current = new RegionSet()
                        current.addSequence(secondHalf)
                        currentSize = (long)secondHalf.size()
                        addedToCurrent = secondHalf
                    }
                    else {
                        // Already over target (remaining ≤ 0) or seq is tiny relative to remaining
                        result.add(current)
                        current = new RegionSet()
                        current.addSequence(seq)
                        currentSize = seqSize
                        addedToCurrent = seq
                    }
                }
                else {
                    current.addSequence(seq)
                    result.add(current)
                    current = new RegionSet()
                    currentSize = 0L
                    addedToCurrent = null
                }
            }
            else {
                current.addSequence(seq)
                currentSize += seqSize
                addedToCurrent = seq
            }

            lastSeq = addedToCurrent
        }

        if (!current.sequences.isEmpty())
            result.add(current)

        result.eachWithIndex { r, i -> r.name = (name ?: '') + '.' + i }
        return result
    }
    
    Set<RegionSet> partition(int sizeBp) {
        log.info "Partition ${this.sequences.size()} sequences into $sizeBp bp chunks"
        this.sequences.collect { String name, Sequence s ->
            (s.range.from..s.range.to).step(sizeBp).collect { int startBp ->
                Sequence seq = new Sequence(s.name)
                seq.range = new GenomicRange(startBp..Math.min(startBp+sizeBp,s.range.to))
                new RegionSet(seq)
            }
        }.flatten() as Set
    }
    
    @CompileStatic
    static RegionSet bed(Map options=[:], File fileName) {
        bed(options, fileName.absolutePath)
    }

    /**
     * Return a region set resolved from a BED file
     * 
     * @param options   
     * @param fileName
     * @return
     */
    @CompileStatic
    static RegionSet bed(Map options=[:], String fileName) {
        if(fileName == null)
            throw new PipelineError("Provided BED file was null or missing")
            
        if(!new File(fileName).exists()) 
            throw new PipelineError("Provided BED file $fileName could not be found")
        
        (RegionSet)Utils.time("load $fileName") {
            int padding = 0;
            if(options.padding)
                padding = options['padding'].toString().toInteger()
    
            RegionSet regionSet = new RegionSet()
            File regionFile = new File(fileName)
            regionFile.eachLine { String line ->
                List<String> parts = line.tokenize('\t')
                if(parts.size()<3)
                    throw new PipelineError("BED file should have at least 3 tab separated columns")
    
    
                int start = Math.max(parts[1].toInteger() - padding, 0)
                int end = parts[2].toInteger() + padding
    
                Sequence sequence = new Sequence(name:parts[0], range:new GenomicRange((start)..(end)))
                regionSet.addSequence(sequence)
            }
            
            // We want the id to change if the source bed file changes size or timestamp
            regionSet.id = Utils.sha1(fileName +':' + regionFile.lastModified() + ':' + regionFile.length())
            
            return regionSet
        }
    }
    
    Set<RegionSet> readSavedRegions(Map options, int parts) {
        List<File> regionFiles = (1..parts).collect { int part ->
            new File("${RegionValue.REGIONS_DIR}/${id}_${parts}_${part}")
        }
        
        List<File> missingRegionFiles = regionFiles.grep { !it.exists() }
        if(missingRegionFiles) {
            log.info "Unable to load previously saved regions because these files are missing: " + missingRegionFiles
            return null
        }
        
        log.info "Regions for region set $id are already cached: using previously saved regions"
        return regionFiles.collect { File regionFile ->
            RegionSet regionSet = new RegionSet()
            regionFile.readLines().each { line -> 
                List fields = line.tokenize('\t'); 
                Sequence sequence = new Sequence(fields[0], new GenomicRange(fields[0].toInteger()..fields[1].toInteger()))
            }
            return regionSet
        } as Set
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
    @CompileStatic
    Set<RegionSet> group(Map options=[:], int parts) {
        
        boolean allowSplitRegions = options.allowBreaks == null ? true : options.allowBreaks
        boolean byChromosome = options.byChromosome ? true : false
        
        if(this.id) {
            Set<RegionSet> savedRegions = this.readSavedRegions(options,parts)
            if(savedRegions != null) {
                return savedRegions
            }
        }
        
        // A sorted set ordered by size and then object to 
        SortedSet<RegionSet> results = new TreeSet(new RegionSetComparator())
        
        for(Map.Entry<String,Sequence> e : sequences) {
            results.add(new RegionSet(e.value))
        }
        
        log.info "Grouping ${results.size()} sequences into $parts groups"
        
        // While the number of parts is too large we should combine smaller ones together
//        if(results.size()>parts)
//            log.info "*** Combining regions to decrease to $parts parts"
            
        while(results.size() > parts) {
            if(!combineSmallest(results, byChromosome))
                break
        }
        
        if(byChromosome && results.size() > parts) {
            log.warning "byChromosome constraint resulted in ${results.size()} parts instead of requested $parts (not enough same-chromosome regions to combine)"
        }
        
        // While number of parts too small, split apart large sequences
        if(results.size()<parts)
            log.info "*** Splitting regions to increase to $parts parts"
        while(results.size() < parts) {
            // Split the largest region set into two
            if(!splitLargest(results, allowSplitRegions))
                break
        }
        
        // While number of parts too uneven, split largest part, invoke
        // above loop again to reduce down.
        // Use a bounded iteration count to prevent infinite loops while still
        // allowing multiple large regions of similar size to all be split.
        int rebalanceAttempts = 0
        int maxRebalanceAttempts = results.size()
        while(results.first().size() > 2*results.last().size()) {
            if(++rebalanceAttempts > maxRebalanceAttempts) {
                log.info "*** Rebalancing exceeded max attempts ($maxRebalanceAttempts), stopping"
                break
            }
            log.info "*** Rebalancing regions due to ${results.first().size()} > 2 x ${results.last().size()}"
            if(!splitLargest(results, allowSplitRegions))
                break
            if(!combineSmallest(results, byChromosome))
                break
        }
        results.eachWithIndex { r,i -> r.name = name + "."+i }
        return results
    }
    
    boolean splitLargest(SortedSet<RegionSet> results, boolean allowSplitRegions) {
        
        RegionSet largest = results.first()
        
        def (part1,part2) = largest.splitInTwo(allowSplitRegions)
        RegionSet large = part1.size() >= part2.size() ? part1 : part2
        RegionSet small = part1.size() >= part2.size() ? part2 : part1
        float ratio = small.size() > 0 ? (float)large.size() / (float)small.size() : Float.MAX_VALUE
        
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
    List<RegionSet> splitInTwo(boolean allowSplitSequences) {
        List<Sequence> ordered = ([] + sequences.values()).sort { it.size() }.reverse()
        
        // Start by sorting the regions into two piles by ordering by size
        // and then putting alternating region sets into each pile
        // This should give a good start at approximately even sized piles
        
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
       
        if(!allowSplitSequences)
            return [result1,result2]
        
        // Make a second set of ordered sequences from result1
        // We expect result1 to be bigger, so we will even up the piles
        // by splitting regions from result1 and giving them to result2
        List<Sequence> ordered2 = ([] + result1.sequences.values()).sort { it.size() }.reverse()

        log.info("Will check ${ordered2.size()} regions to reassign to other split in case of size bias")
        
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
                else {
                    log.info("Split of $largest produced zero size second region: ignoring")
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
        
        // Recompute chromosome names since other sequences may share the same chromosome
        this.chromosomeNames.clear()
        for(Sequence seq : this.sequences.values()) {
            this.chromosomeNames.add(seq.name)
        }
    }

    /**
     * Find the two smallest region sets in the results and replace them with a 
     * combined region set that contains the regions in both of them.
     * 
     * @param byChromosome  if true, only combine region sets that share at least one chromosome name
     * @return true if a combination was performed, false if no valid combination exists
     */
    boolean combineSmallest(SortedSet<RegionSet> results, boolean byChromosome) {
        if(byChromosome) {
            NavigableSet<RegionSet> navResults = (NavigableSet<RegionSet>) results
            Iterator<RegionSet> it = navResults.descendingIterator()
            
            Map<String, RegionSet> seen = new HashMap<String, RegionSet>()
            RegionSet first = null
            RegionSet second = null
            
            while(it.hasNext()) {
                RegionSet rs = it.next()
                for(String chr : rs.chromosomeNames) {
                    if(seen.containsKey(chr)) {
                        first = rs
                        second = seen[chr]
                        break
                    }
                }
                if(first != null)
                    break
                for(String chr : rs.chromosomeNames) {
                    seen[chr] = rs
                }
            }
            
            if(first == null || second == null) {
                log.info("No compatible region sets found for combining (byChromosome constraint)")
                return false
            }
            
            results.remove(first)
            results.remove(second)
//            log.info("Combining regions $first and $second to reduce parallelism to ${results.size()+1} (byChromosome)")
            results.add(new RegionSet(first.sequences.values() + second.sequences.values()))
            return true
        }
        else {
            // Find the smallest genome
            RegionSet smallest = results.last()
            results.remove(smallest)

            RegionSet secondSmallest = results.last()
            results.remove(secondSmallest)

//            log.info("Combining regions $smallest and $secondSmallest to reduce parallelism to ${results.size()+1}")

            // Combine the two smallest and add them back in as a single RegionSet
            results.add(new RegionSet(smallest.sequences.values() + secondSmallest.sequences.values()))
            return true
        }
    }
     
    private static Pattern ALTERNATE_HAPLOTYPE_PATTERN = ~'.*_hap[0-9]*$'
     
    /**
     * Remove all sequences that do not belong to a major chromosome.
     * This is interpreted as:
     * <ul>
     *  <li>Starts with "Un"
     *  <li>Ends with "random"
     *  <li>Ends with "_hap[number]"
     */
    @CompileStatic // <= causes strange error at runtime in chr_region test
    void removeMinorContigs() {
        this.sequences = this.sequences.grep { Map.Entry<String,Sequence> e -> !isMinorContig(e.key) }.collectEntries()
    }
    
    @CompileStatic
    boolean isMinorContig(final String chr) {
        return chr.startsWith('NC_') ||
            chr.startsWith('GL') ||
            chr.startsWith('Un_') ||
            chr.startsWith('chrUn_') ||
            chr.startsWith('M') ||
            chr.startsWith('chrM') ||
            chr.endsWith('_random') || 
            chr.endsWith('_alt') ||
            chr.endsWith('_fix') ||
            chr.endsWith('EBV') ||
            chr.startsWith('HLA-') ||
            chr.matches(ALTERNATE_HAPLOTYPE_PATTERN)
    }
    
    @CompileStatic
    long size() {
        // Note: we don't use built in sum() because of worry about int overflow
        long result = 0
        for(Map.Entry<String,Sequence> seq in this.sequences) {
            result += seq.value.size()
        }
        return result
    }
    
    String toString() {
        "RegionSet[sequences=${sequences.values()}]"
    }
    
    @CompileStatic
    RegionValue getRegion() {
        new RegionValue(this.sequences.values())
    }
    
    @CompileStatic
    boolean overlaps(final String chr, final int from, final int to) {
        this.sequences.any { it.value.overlaps(chr, from, to) }
    }
    
    @CompileStatic
    boolean overlaps(final String chr) {
        this.sequences.any { it.value.name == chr }
    }
}
