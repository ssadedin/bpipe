package bpipe

import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

@Log
class PipelineFile implements Serializable {
   
    public static final long serialVersionUID = 0L
    
    String path
    
    StorageLayer storage
    
    PipelineFile(String path, StorageLayer storage) {
        assert path != null
        assert storage != null
        this.path = path
        this.storage = storage
    }
    
    PipelineFile newName(String newName) {
        return new PipelineFile(newName, storage)
    }
    
    boolean exists() {
        storage.exists(path)
    }
    
//    @Memoized
    Path toPath() {
        storage.toPath(path)
    }
    
//    @Memoized
    @CompileStatic
    String getName() {
        toPath().fileName
    }
    
    @CompileStatic
    boolean isDirectory() {
        Files.isDirectory(toPath())
    }
    
    /**
     * @param pattern   compiled regex pattern
     * @return  true iff the name (not whole path) of this file matches the given pattern
     */
    @CompileStatic
    boolean matches(Pattern pattern) {
        getName().matches(pattern)
    }
    
    boolean isMissing(OutputMetaData p, String type) {
                
        log.info "Checking file " + this.path
        
        if(this.exists())
            return false // not missing
                    
        if(!p) {
            log.info "There are no properties for $path and file is missing"
            return true
        }
                
        if(p.cleaned) {
            log.info "File $path [$type] does not exist but has a properties file indicating it was cleaned up"
            return false
        }
        else {
            log.info "File $path [$type] does not exist, has a properties file indicating it is not cleaned up"
            return true
        }
    }
   
    @Override
    @CompileStatic
    String toString() {
        path
    }

}
