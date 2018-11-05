package bpipe

import bpipe.storage.StorageLayer
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * A class that abstracts the type of underlying storage that a file is stored in,
 * while presenting a common API.
 * <p>
 * Note: to some extent this mirrors the purpose of the {@link java.nio.file.Path} class.
 * Unfortunately, that class is not serializable, and presents a very differnet API to
 * {@link java.io.File}. This class offers a serializable wrapper that unifies the APIs 
 * a little bit more.
 * 
 * @author simon.sadedin
 */
@Log
class PipelineFile implements Serializable {
   
    public static final long serialVersionUID = 0L
    
    String path
    
    StorageLayer storage
    
    protected PipelineFile(path) {
        assert path != null
        this.path = path
    }
    
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
    @CompileStatic
    Path toPath() {
        storage.toPath(path)
    }
    
//    @Memoized
    @CompileStatic
    String getName() {
        toPath().fileName
    }
    
    @CompileStatic
    PipelineFile normalize() {
        new PipelineFile(toPath().normalize().toString(), storage)
    }
    
    @CompileStatic
    boolean isDirectory() {
        Files.isDirectory(toPath())
    }
    
    @CompileStatic
    long lastModified() {
       try {
           Files.getLastModifiedTime(toPath()).toMillis() 
       }
       catch(NoSuchFileException ex) {
           return 0
       }
    }
    
    @CompileStatic
    long length() {
        if(exists())
            return Files.size(toPath())
        else
            return 0L
    }
    
    @CompileStatic
    String getAbsolutePath() {
        toPath().toAbsolutePath().toString()
    }
    
    /**
     * @param pattern   compiled regex pattern
     * @return  true iff the name (not whole path) of this file matches the given pattern
     */
    @CompileStatic
    boolean matches(Pattern pattern) {
        getName().matches(pattern)
    }
    
    @CompileStatic
    String getPrefix() {
        PipelineCategory.getPrefix(this.toString())
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
