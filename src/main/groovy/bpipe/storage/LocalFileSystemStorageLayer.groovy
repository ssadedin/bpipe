package bpipe.storage

import java.io.File
import java.nio.file.Path

import groovy.util.logging.Log

@Log
class LocalFileSystemStorageLayer extends StorageLayer {

    @Override
    public boolean exists(String path) {
        
       File f = new File(path)
        
       if(f.exists())
           return true
           
       if(!f.exists()) {
           log.info "File $f does not appear to exist: listing directory to flush file system"
           try { f.absoluteFile.parentFile.listFiles() } catch(Exception e) { log.warning("Failed to list files of parent directory of $f"); }
           if(f.exists())
               log.info("File $f revealed by listing directory")
       } 
       return f.exists()
    } 

    @Override
    public Path toPath(String path) {
        return new File(path).toPath()
    }
}
