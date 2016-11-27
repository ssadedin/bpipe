package bpipe

import groovy.util.logging.Log;
import static bpipe.GenomicRange.range

/**
 * A simple data structure to hold information about a gene
 * 
 * @TODO: rename this to 'Feature' so as to generalise
 * @author ssadedin
 */
class Gene implements Serializable {
    private static final long serialVersionUID = 1L;
    String name
    GenomicRange location
    
    String toString() {
        "$name:$location.from-$location.to"
    }
}

/**
 * A sequence is a contiguous stretch of DNA. In practise this approximates to 
 * a chromosome or a subsequence thereof, but of course, it can be
 * any sort of contig.
 * 
 * @author ssadedin
 */
@Log
class Sequence implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    String name
    
    GenomicRange range = new GenomicRange(0..0)
    
    TreeMap<Integer, Gene> genes = new TreeMap()
    
    transient Map<String,Gene> genesByName = [:]
    
    Sequence() {
    }
    
    /**
     * Create a sequence from a chromosome name and a list of gene/region pairs
     * (a list of lists).
     * 
     * @param name
     * @param genes
     */
    Sequence(String name, List... genes) {
        this.name = name
        genes.each { add(it[0],it[1].from, it[1].to) }
    }
    
    /**
     * If the gene is already known, expands the range of the gene
     * to the union of the existing range and the provided range.
     * If not known, stores this range as the initial range.
     * 
     * @param geneName    name of gene
     * @param start        start of gene
     * @param end        end of gene
     */
    Gene add(String geneName, int start, int end) {
        Gene gene = genesByName[geneName]
        if(!gene) {
            // In Ensembl, multiple genes start at the same location?!
            // We just treat them as synonyms for each other
            gene = genes[start]
            genesByName[geneName] = gene
        }
            
        if(!gene) {
            gene = new Gene(name:geneName, location:range(start..end))
            genesByName[geneName] = gene
            if(genes[start])
                throw new IllegalStateException("Gene $geneName already started at $start as gene ${genes[start].name}")
                
            genes[start] = gene
        }
        
        if(gene.location.from<start)
            gene.location.from = start
            
        if(gene.location.to<end)
            gene.location.to = end
            
        if(start < this.range.from)    
            this.range.from = start
            
        if(end > this.range.to) {
//            println "End is ${end.class.name}"
//            println "Range to is ${range.to.class.name}"
            this.range.to = end
        }
            
        return gene
    }
    
    /**
     * Split this sequence approximately in two parts,
     * by bisecting the genes nearest the center in the 
     * space between them.
     */
    List<Sequence> split() {
        
        log.info("Split of $range.from-$range.to (size=${range.to-range.from})")
        int middle = this.range.from + (this.range.to - this.range.from) / 2
        
        // The gap between features that we will try to bisect
        GenomicRange gap = range(this.range.from..this.range.to)
        
        log.info "Looking for nearest lower feature to $middle"
        
         // Try not to bisect a feature
        Gene lower = this.genes.lowerEntry(middle)?.value
        if(lower) {
            log.info "Lower feature is $lower.name from $lower.location.from - $lower.location.to middle is $middle"
            gap.from = lower.location.to
            
            if(middle in lower.location)
                log.info "Split bisects gene $lower"
        }
        else {
            log.info "No lower feature found (search from $middle)"    
        }
        
        int searchFrom = middle
        if(lower && lower.location.to < this.range.to-1) 
            searchFrom = lower.location.to
        
        Gene higher = this.genes.higherEntry(searchFrom)?.value
        if(higher) {
            gap.to = higher.location.from
            if(higher.is(lower)) {
                log.info "Higher and lower feature are the same: $higher"
                lower = this.genes.lowerEntry(middle-1)?.value
                gap.from = lower ? lower.location.to : this.range.from
            }
            log.info "Higher feature is $higher.name from $higher.location.from - $higher.location.to"
        }
       
        middle = gap.to + (gap.from - gap.to) / 2
        if(lower && middle <= lower.location.to)
            throw new IllegalStateException("Bisecting distance between features $lower and $higher produced a location inside $lower. Please report this as a bug.")
        
        if(higher && lower && higher.location.from - lower.location.to < 10000) 
            log.warning "Bisecting genes $lower.name and $higher.name produced a split < 5kb apart"
        
        return [
            new Sequence(name: this.name, genes: this.genes.headMap(middle), range: range(this.range.from..middle)),
            new Sequence(name: this.name, genes: this.genes.tailMap(middle+1), range: range(middle..this.range.to))
        ]
    }
    
    int size() {
        return this.range.size()
    }
    
    String toString() {
        "$name:$range.from-$range.to"
    }
}