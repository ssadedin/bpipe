package bpipe

import groovy.transform.ToString
import java.nio.file.Path

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
        
        // Need to ensure that source files are serialisable, so 
        // convert to strings
        List<List<String>> sourceFiles = source*.value.collect { def fileLikes ->
            return Utils.box(fileLikes).collect { fileLike ->
                if(fileLike instanceof Path)
                    fileLike = fileLike.toFile()
                if(fileLike instanceof File)
                    fileLike = fileLike.path
                return fileLike.toString()
            }
        }
        
        new PipelineChannel(source:source*.key, files: sourceFiles)
    }

    public static channel(String... source) {
        new PipelineChannel(source:source as List)
    }
    
    public static channel(Iterable source) {
        new PipelineChannel(source:source)
    }
}
