package bpipe.storage

import java.nio.file.Files
import java.nio.file.Path

import bpipe.executor.CommandExecutor
import groovy.util.logging.Log

@Log
class TestStorageLayer extends StorageLayer {
    
    String base

    @Override
    public boolean exists(String path) {
        return Files.exists(toPath(path))
    }

    @Override
    public Path toPath(String path) {
        return new File(base, path).toPath()
    }

    @Override
    public void mount(CommandExecutor executor) {
        log.info "Mounting test storage ..."
        // noop
    }

}
