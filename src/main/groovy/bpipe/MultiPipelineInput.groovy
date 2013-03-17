package bpipe

import java.util.Iterator;
import groovy.util.logging.Log;

/**
 * Extends {@link PipelineInput} to handle multiple inputs.
 * <p>
 * Where {@link PipelineInput} tries to always return just a single
 * input that matches what the user expects, {@link MultiPipelineInput} 
 * resolves all available inputs that match what the user expects and returns
 * them separated by spaces.
 * 
 * @author ssadedin
 */
@Log
class MultiPipelineInput extends PipelineInput implements Iterable {
	
    MultiPipelineInput(def input, List<PipelineStage> stages) {
		super(input,stages)	
	}
	
	/**
	 * Maps to all of the values separated by spaces
	 */
    @Override
	public String mapToCommandValue(def values) {
		def result = Utils.box(values)
        result.each { this.resolvedInputs << String.valueOf(it) }   
        return result.collect { String.valueOf(it) }.join(' ')
	}

	String toString() {
       List boxed = Utils.box(super.@input)
       this.resolvedInputs += boxed
       return boxed.join(" ")
    }
    
    def propertyMissing(String name) {
        def result = super.propertyMissing(name)
        if(result) {
            def mp = new MultiPipelineInput(this.resolvedInputs, stages)
            mp.parent = this
            mp.resolvedInputs = this.resolvedInputs
            log.info("My resolved inputs: " + this.resolvedInputs.hashCode() + " child resolved inputs " + mp.resolvedInputs.hashCode())
            return mp
        }
     }
    
	@Override
	public Iterator iterator() {
		return Utils.box(super.@input).listIterator()
	}
}
