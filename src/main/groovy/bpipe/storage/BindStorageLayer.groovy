package bpipe.storage

import java.nio.file.Files
import java.nio.file.Path

import bpipe.executor.CommandExecutor
import groovy.util.logging.Log

/**
 * A storage layer that is mounted by binding it into a container.
 * 
 * Note: this type of storage layer is mounted as part of the
 * container run command, rather than using the mount command.
 * 
 * @author Simon Sadedin
 */
@Log
class BindStorageLayer extends StorageLayer {
    
    String base

    @Override
    public boolean exists(String path) {
        Path fullPath = toPath(path)
        log.info "Checking if $fullPath exists"
        return Files.exists(fullPath)
    }

    @Override
    public Path toPath(String path) {
        return new File(base, path).toPath()
    }

    @Override
    public String getMountCommand(CommandExecutor executor) {
        // noop
        return base
    }

}
