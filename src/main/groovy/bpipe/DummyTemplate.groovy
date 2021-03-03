package bpipe

import groovy.text.Template
import groovy.transform.CompileStatic

/**
 * A dummy template that just returns fixed contents
 * 
 * @author Simon Sadedin
 */
@CompileStatic
class DummyTemplate implements Template, Writable {
    
    String contents
    
    public DummyTemplate() {
    }

    @Override
    public Writable make() {
        throw new IllegalArgumentException('Dummy template must be provided contents at invocation time')
    }

    @Override
    public Writable make(Map arg0) {
        this.contents = arg0.content?:''
        return this
    }

    @Override
    public Writer writeTo(Writer out) throws IOException {
        StringWriter w = new StringWriter()
        w.write(contents)
        return w
    }
    
    @Override 
    String toString() {
        return this.contents
    }
}
