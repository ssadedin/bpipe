package bpipe

import java.io.Serializable

import groovy.util.logging.Log

/**
 * A resource that can be managed by Bpipe.
 * The key is the name of the resource (eg: "memory")
 * and the amount is the amount that is being used by  particular
 * operation.
 * <p>
 * Note: see {@link PipelineBodyCategory} for the magic that makes
 * these become referencable within pipline stages as "GB", 'threads',
 * and so on.
 *
 * @author ssadedin
 */
@Log
class ResourceUnit implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    public static final int UNLIMITED = -1
    
    int amount = 0;
    
    /**
     * Note that the default max amount of 0 implies that the
     * maximum amount will be unlimited, or will take the limit
     * imposed automatically by configuration
     */
    int maxAmount = 0
    
    String key
    
    String toString() {
        "$amount $key"
    }
    
    Object clone() {
        new ResourceUnit(amount:amount, maxAmount:maxAmount, key:key)
    }
}
