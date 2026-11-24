package bpipe.storage

import java.io.File
import java.nio.file.Path

import bpipe.executor.CommandExecutor
import groovy.util.logging.Log

@Log
class LocalFileSystemStorageLayer extends StorageLayer {
    
    public static final long serialVersionUID = 0L
    
    LocalFileSystemStorageLayer() {
        this.name = "local"
    }

    /**
     * Check if the file exists, and if it appears not to, list the parent directory
     * to flush any stale view a non-coherent file system client has of it before checking
     * again. The listing is shared with other threads flushing the same directory at the
     * same time (see {@link DirectoryFlusher}).
     */
    @Override
    public boolean exists(String path) {

       File f = new File(path)

       if(f.exists())
           return true

       log.info "File $f does not appear to exist: listing directory to flush file system"
       DirectoryFlusher.instance.flushParentOf(f)

       boolean result = f.exists()
       if(result)
           log.info("File $f revealed by listing directory")
       return result
    }

    /**
     * A plain check of the file system, with no directory listing to flush stale caches.
     */
    @Override
    public boolean existsWithoutFlush(String path) {
        return new File(path).exists()
    }

    @Override
    public Path toPath(String path) {
        return new File(path).toPath()
    }

    @Override
    public String getMountCommand(CommandExecutor executor) {
        // No-op - local storage is assumed to be already mounted
        return "."
    }
}
