package bpipe

import groovy.lang.Closure

/**
 * Wraps an inner closure but allows a separate 
 * set of variables to be carried and injected to the
 * closure in a thread-local way when it is executed.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class ParameterizedClosure extends Closure {
    
    public ParameterizedClosure(Map variables, Closure body) {
        super(body.owner);
        this.body = body
        this.extraVariables = variables
    }
    
    Map extraVariables
    
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

    @Override
    public void setDelegate(Object delegate) {
        body.setDelegate(delegate);
        super.setDelegate(delegate);
    }
}
