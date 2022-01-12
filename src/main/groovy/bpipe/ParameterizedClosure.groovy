/*
 * Copyright (c) 2012 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package bpipe

import groovy.lang.Closure
import groovy.transform.CompileStatic
import groovy.util.logging.Log;

/**
 * Wraps an inner closure but allows a separate 
 * set of variables to be carried and injected to the
 * closure in a thread-local way when it is executed.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@CompileStatic
class ParameterizedClosure extends Closure {
    
    public ParameterizedClosure(Map<String,Object> variables, Closure body) {
        super(body.owner);
        this.body = body
        this.extraVariables = variables
        if(body instanceof ParameterizedClosure) {
            ParameterizedClosure pc = (ParameterizedClosure)body
            this.inputStages = pc.@inputStages
        }
    }
  
    public ParameterizedClosure(Map options=null, List spec, Closure body) {
        super(body.owner);
        this.body = body
        
        if(spec.every { it instanceof Closure}) {
            this.inputStages = spec
        }
        else
        if(spec.every { it instanceof String}) {
            this.patterns = spec
            if(options)
                this.options = options
        }
        
        this.extraVariables = [:]
        if(body instanceof ParameterizedClosure) {
            ParameterizedClosure pc = (ParameterizedClosure)body
            this.extraVariables = pc.@extraVariables
        }
    }
     
    /**
     * A Map defining variables OR a closure that can be 
     * executed to return such a map
     */
    def extraVariables
    
    /**
     * Input stages to prioritise
     * 
     * These are set when using stage.from(...) in the pipeline
     */
    List<Closure> inputStages
    
    /**
     * List of input patterns to prioritise inputs by name
     */
    List<String> patterns
    
    /**
     * Options for looking up patterns
     */
    Map options = null
    
    /**
     * The wrapped body that will be executed after customisations to inputs and vairables are applied
     */
    Closure body

    @Override
    public Object call() {
      body()
    }    
    
    @Override
    public Object call(Object[] args) {
        body.call(args);
    }
    
    @Override
    public Object call(Object arguments) {
        body.call(arguments);
    }
    
    public Object doCall(Object arg) {
        return call(arg);
    }

    @Override
    public void setDelegate(Object delegate) {
		if(delegate && delegate instanceof PipelineDelegate && body.delegate && body.delegate instanceof PipelineDelegate) {
            PipelineDelegate pd = (PipelineDelegate)body.delegate
            pd.context.set(delegate.context.get())
		}
		else
	        body.setDelegate(delegate);
			
        super.setDelegate(delegate);
    }
    
    @Override
    public Object getDelegate() {
        return body.getDelegate()
    }
}
