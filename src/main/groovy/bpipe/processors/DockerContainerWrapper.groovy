package bpipe.processors

import bpipe.*
import bpipe.storage.BindStorageLayer
import bpipe.storage.StorageLayer
import groovy.util.logging.Log

@Log
class DockerContainerWrapper implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
		
        Map config = command.processedConfig.container
        
        def extraVolumes = Config.listValue(config, 'storage').collect { name ->
            
            BindStorageLayer layer = (BindStorageLayer)StorageLayer.create(name)

            ["-v", "$layer.base:$layer.base"]
        }
        .unique()
        .sum()
        ?: []
        
		log.info("Volume arguments for docker command: ${extraVolumes}")
		
		List<String> dockerCommand = [config.getOrDefault('command', ['/bin/sh','-e'])].flatten()

		String entryPoint = config.getOrDefault('entryPoint', null)
		List<String> entryPointArg
		if(entryPoint == "default") {
			entryPointArg = []
		}
		else {
			 entryPointArg = entryPoint ? ["--entrypoint", entryPoint] : ["--entrypoint", "/bin/env"]
		}

        command.shell = 
            [
                "docker",
                "run",
				*entryPointArg,
                "-w", Runner.runDirectory, 
                "-v", "$Runner.runDirectory:$Runner.runDirectory",
				*extraVolumes,
				config.image,
				*dockerCommand
            ]  
    }
}
