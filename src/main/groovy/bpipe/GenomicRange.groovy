package bpipe

import groovy.lang.IntRange
import groovy.transform.CompileStatic

import java.io.Serializable;

/**
 * Extends the normal Integer Range class to make it serializable
 */
@CompileStatic
class GenomicRange implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public int from
    
    public int to
   
    /**
     * Convert a non-Genomic range into a GenomicRange
     */
    GenomicRange(IntRange r) {
        this.from = r.from
        this.to = r.to
    }
    
    /**
     * Convenience method to make constructing ranges very concise
     */
    public static GenomicRange range(IntRange r) {
        new GenomicRange(r)
    } 
    
    int size() {
        return Math.abs(from - to)
    }
}
