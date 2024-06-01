package bpipe

import groovy.transform.ToString

/**
 * A channel is a type of branch which filters prioritises inputs
 * matching a given specification. Inputs that do not match the spec
 * will always be used only if no inputs matching the specification
 * exist.
 * 
 * @author Simon Sadedin
 */
@ToString(includeNames=true)
class PipelineChannel implements Serializable {
    
    public static final long serialVersionUID = 0L
    
    Iterable source
    
    String name
    
    List<Object> files
    
    PipelineChannel named(String value) {
        this.name = value
        return this
    }
    
    public static channel(Map<String,Object> source) {
        new PipelineChannel(source:source*.key, files: source*.value)
    }

    public static channel(String... source) {
        new PipelineChannel(source:source as List)
    }
    
    public static channel(Iterable source) {
        new PipelineChannel(source:source)
    }
}
