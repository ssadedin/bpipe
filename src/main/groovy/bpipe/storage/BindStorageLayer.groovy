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
    
    /**
     * The path of the storage on the host (ie: the source of the bind mount)
     */
    String base
    
    /**
     * Optional path at which the storage should appear inside the container. If
     * not set, the storage is bound at the same path as on the host ({@link #base}).
     * <p>
     * Named with an underscore so that it maps directly from the <code>container_path</code>
     * attribute in the <code>filesystems</code> config block (see {@link StorageLayer#create}).
     * <p>
     * Note: Bpipe does not rewrite paths in commands to account for this mapping. When a
     * different container path is configured, commands must refer to files under the storage
     * using the container path.
     */
    String container_path
    
    /**
     * @return  the path at which this storage should be mounted inside a container
     */
    String getContainerTarget() {
        if(!container_path)
            return base

        // Both docker and singularity require the target of a bind mount to be absolute
        if(!container_path.startsWith('/'))
            throw new bpipe.PipelineError(
                "The container_path '$container_path' configured for filesystem '$name' is not an absolute path. " + 
                "Please set container_path for this filesystem to an absolute path.")

        return container_path
    }

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
