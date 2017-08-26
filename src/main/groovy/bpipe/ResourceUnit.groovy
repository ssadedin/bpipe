package bpipe

import java.io.Serializable
import java.util.regex.Matcher
import java.util.regex.Pattern
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
    
    static Pattern MEMORY_PATTERN = ~'([0-9]{1,}) *([a-zA-Z]{1,})'
    
    
    static Map<String,Closure> MEMORY_UNIT_CONVERSION = [
        "G" : { x -> x * 1000 },
        "M" : { x -> x },
        "GB" : { x -> x * 1000 },
        "MB" : { x -> x },
        "T" : { x -> x * 1000000 },
        "TB" : { x -> x * 1000000 }
    ]
    
    static ResourceUnit memory(Object value) {
        
        def fromInt = { x -> new ResourceUnit(amount: x*1024, key: "memory") }
        
        if(value instanceof Integer)
            return fromInt(value)
            
        String stringValue = String.valueOf(value)
        if(stringValue.isInteger())
            return fromInt(stringValue.toInteger())
            
        Matcher patternMatch = (MEMORY_PATTERN.matcher(stringValue))
        if(patternMatch) {
            int amount = patternMatch[0][1].toInteger()
            String unit = patternMatch[0][2].toUpperCase()
            if(amount < 0)
                throw new PipelineError("Amount for memory specification is ${amount} < 0: please specify a positive integer")
                
            if(MEMORY_UNIT_CONVERSION.containsKey(unit)) {
                amount = MEMORY_UNIT_CONVERSION[unit](amount)
                return new ResourceUnit(amount:amount, key:"memory")
            }
            else 
                throw new PipelineError("Unknown unit ${unit} specified for memory configuration. Please use MB, GB, or TB")
        }
        
        throw new PipelineError("Unable to parse memory specification: $value")
    }
    
    Object clone() {
        new ResourceUnit(amount:amount, maxAmount:maxAmount, key:key)
    }
}
