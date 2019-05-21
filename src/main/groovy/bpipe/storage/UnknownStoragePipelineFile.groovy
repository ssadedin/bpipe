package bpipe.storage

import java.nio.file.Path

import bpipe.PipelineError
import bpipe.PipelineFile
import bpipe.executor.CommandExecutor
import groovy.transform.CompileStatic
import groovy.util.logging.Log

class UnknownFileStorageLayer extends StorageLayer {
		UnknownFileStorageLayer() {
			this.name = "uknown"
		}
            
        public boolean exists(String path) {
            true
        }
            
        public Path toPath(String path) {
            new File(path).toPath()
        }

        @Override
        public String getMountCommand(CommandExecutor executor) {
            return "."
        }
}

@Log
class UnknownStoragePipelineFile extends PipelineFile {  
	
    UnknownStoragePipelineFile(String thePath) {
        super(thePath, new UnknownFileStorageLayer())
        
        log.info "Created new unknown storage pipeline file: $thePath"
    }

    @Override
    public String renderToCommand() {
		throw new PipelineError("The file " + path + " could not be found or associated to a configured storage entry")
    }
}
