package bpipe

import groovy.util.logging.Log;

/**
 * A simple data structure to hold information about a gene
 * @author ssadedin
 */
class Gene implements Serializable {
    String name
    Range<Integer> location
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
    
    String name
    
    Range<Integer> range = 0..0
    
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
            gene = new Gene(name:geneName, location:start..end)
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
            
        if(end > this.range.to)
            this.range.to = end
            
        return gene
    }
    
    /**
     * Split this sequence approximately in two parts,
     * by bisecting the genes nearest the center in the 
     * space between them.
     */
    List<Sequence> split() {
        
        int middle = (this.range.to - this.range.from) / 2
        
        // Try not to bisect a gene
        Gene lower = this.genes.lowerEntry(middle).value
        log.fine "Lower gene is $lower.name from $lower.location.from - $lower.location.to middle is $middle"
        
        if(middle in lower.location)
            log.fine "Split bisects gene"
            
        Gene higher = this.genes.higherEntry(middle).value
        log.fine "Higher gene is $higher.name from $higher.location.from - $higher.location.to"
        
        if(higher.is(lower))
            lower = this.genes.lowerEntry(middle-1).value
        
        middle = lower.location.to + (higher.location.from - lower.location.to) / 2
        if(middle <= lower.location.to)
            throw new IllegalStateException("Bisecting distance between genes produced a location inside the lower gene. Please report this as a bug.")
        
        if(higher.location.from - lower.location.to < 10000) 
            log.warning "Bisecting genes $lower.name and $higher.name produced a split < 5kb apart"
        
        return [
            new Sequence(name: this.name, genes: this.genes.headMap(middle), range: this.range.from..middle),
            new Sequence(name: this.name, genes: this.genes.tailMap(middle+1), range:middle..this.range.to)
        ]
    }
    
    int size() {
        return this.range.size()
    }
    
    String toString() {
        "$name[$range.from-$range.to]"
    }
}