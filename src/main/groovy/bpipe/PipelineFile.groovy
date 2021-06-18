package bpipe

import bpipe.storage.LocalFileSystemStorageLayer
import bpipe.storage.StorageLayer
import bpipe.storage.UnknownStoragePipelineFile
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
@CompileStatic
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
    Path toPath() {
        storage.toPath(path)
    }
    
//    @Memoized
    String getName() {
        toPath().fileName
    }
    
    PipelineFile normalize() {
        new PipelineFile(toPath().normalize().toString(), storage)
    }
    
    PipelineFile div(String subDir) {
        new PipelineFile(this.path + '/' + subDir, storage)
    }
    
    boolean isDirectory() {
        Files.isDirectory(toPath())
    }
    
    long lastModified() {
       try {
           Files.getLastModifiedTime(toPath()).toMillis() 
       }
       catch(NoSuchFileException ex) {
           return 0
       }
    }
    
    long length() {
        if(exists())
            return Files.size(toPath())
        else
            return 0L
    }
    
    String getAbsolutePath() {
        toPath().toAbsolutePath().toString()
    }
    
    /**
     * @param pattern   compiled regex pattern
     * @return  true iff the name (not whole path) of this file matches the given pattern
     */
    boolean matches(Pattern pattern) {
        getName().matches(pattern)
    }
    
    String getPrefix() {
        PipelineCategory.getPrefix(this.toString())
    }
    
    boolean isMissing(OutputMetaData p, String type) {
                
        log.info "Checking file " + this.path + " in storage " + this.storage
        
        if(this.exists())
            return false // not missing
                    
        long fileSystemSyncTimeMs = Config.userConfig.getOrDefault('fileSystemSyncTimeMs', 500) as Long
        if(!p) {
            log.info "There are no properties for $path and file is missing"
            return this.flushMetadataAndCheckIfMissing(fileSystemSyncTimeMs)
        }
                
        if(p.cleaned) {
            log.info "File $path [$type] does not exist but has a properties file indicating it was cleaned up"
            return false
        }
        else {
            log.info "File $path [$type] does not exist, has a properties file indicating it is not cleaned up"
            return this.flushMetadataAndCheckIfMissing(fileSystemSyncTimeMs)
        }
    }
    
    boolean flushMetadataAndCheckIfMissing(long timeout=0L) {
        log.info "File does not appear to exist: listing directory to flush file system: " + this
        Path p = this.toPath()
        Path parent = p.toAbsolutePath().parent
        try { Files.list(parent) } catch(Exception e) { log.warning("Failed to list files of parent directory of " + this + " ($parent)"); }
        if(this.exists()) {
            log.info("File " + this + " revealed by listing directory to flush metadata") 
            return false
        }
        else {
            while(timeout>0) {
                Thread.sleep(400)
                if(!this.flushMetadataAndCheckIfMissing(0))
                    return false
                timeout -= 400
            }
            return true
        }
    }
   
    @Override
    String toString() {
        path
    }
    
    String renderToCommand() {
        assert storage.name != 'unknown'
        
        if(storage instanceof LocalFileSystemStorageLayer) 
            return this.toString()
        else
            return "{bpipe:$storage.name://${this.toString()}}"
    }
}
