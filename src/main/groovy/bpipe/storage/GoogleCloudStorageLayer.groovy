package bpipe.storage

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import bpipe.Utils
import groovy.transform.Memoized

class GoogleCloudStorageLayer extends StorageLayer {
    
    String bucket = getDefaultBucket()

    @Override
    public boolean exists(String path) {
        return Files.exists(toPath(path))
    }

    @Override
    public Path toPath(String path) {
        return Paths.get(URI.create("gs://$bucket/$path"));
    }
    
    @Memoized
    static String getDefaultBucket() {
        'bpipe-' + Utils.sha1(bpipe.Runner.HOSTNAME+'::' + bpipe.Runner.canonicalRunDirectory)
    }
}
