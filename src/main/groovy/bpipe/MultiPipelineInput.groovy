/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
    
    MultiPipelineInput(List<PipelineFile> input, List<PipelineStage> stages, Aliases aliases) {
		super(input,stages, aliases)	
        assert input.every { it instanceof PipelineFile }
	}
	
	/**
	 * Maps to all of the values separated by spaces
	 */
    @Override
	public String mapToCommandValue(def values) {
		def result = Utils.box(values)
        assert result.every { it instanceof PipelineFile }
        addResolvedInputs(result)
        return result.collect { this.aliases[it] }.join(' ')
	}
    
    List<PipelineFile> getResolvedValues() {
       List boxed = Utils.box(this.input).unique()
       for(PipelineFile i in boxed) {
           if(!this.resolvedInputs.any { it.path == i.path })
               this.resolvedInputs.add(i)
       }
       addFilterExts(boxed)
       
       if(boxed.empty)
           throw new InputMissingError("Expected one or more inputs with extension '" + this.extensionPrefix + "' but none could be located from pipeline.")
           
       return boxed
    }

	String toString() {
       List<PipelineFile> boxed = getResolvedValues()
       return boxed*.renderToCommand().join(" ")
    }
    
    def propertyMissing(String name) {
        
        if(this.resolvedInputs)
            this.resolvedInputs.clear()
            
        def result = super.propertyMissing(name)
        if(result) {
            def mp = new MultiPipelineInput(this.resolvedInputs.clone(), stages, this.aliases)
            mp.parent = this
            mp.resolvedInputs = this.resolvedInputs
            mp.currentFilter = this.currentFilter
            if(result instanceof PipelineInput)
                mp.extensionPrefix = result.extensionPrefix
            log.info("My resolved inputs: " + this.resolvedInputs.hashCode() + " child resolved inputs " + mp.resolvedInputs.hashCode())
            return mp
        }
    }
    
    /**
     * Return the inputs with each one prefixed by the specified flag
     * <p>
     * If the flag ends with "=" then no space is included between the flag
     * and the option. Otherwise, a space is included.
     * 
     * @param flag name of flag, including dashes (eg: "-I" or "--input")
     * @return  string containing each matching input prefixed by the flag and a space
     */
    @Override
    public String withFlag(String flag) {
       List<PipelineFile> boxed = Utils.box(super.input).unique { it.path }
       return super.formatFlag(flag,boxed)
    }
    
	@Override
	public Iterator iterator() {
		return super.input.listIterator()
	}
    
    public int size() {
        return super.input.size()
    }
    
    String asQuotedList() {
        '["' + this.join('","') + '"]'
    }
}
