package bpipe.storage

import bpipe.PipelineFile
import groovy.transform.CompileStatic

class LocalPipelineFile extends PipelineFile {

    LocalPipelineFile(String path) {
        super(path, new LocalFileSystemStorageLayer())
    }
    
    @CompileStatic
    static List<LocalPipelineFile> from(List<Object> fileLikeObjs) {
        fileLikeObjs.collect { Object obj ->
            new LocalPipelineFile(String.valueOf(obj))
        }
    }
}
