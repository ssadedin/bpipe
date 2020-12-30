package bpipe.processors

import bpipe.*
import bpipe.storage.BindStorageLayer
import bpipe.storage.StorageLayer

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
        
        command.shell = 
            [
                "docker",
                "run",
                "-w", Runner.runDirectory, 
                "-v", "$Runner.runDirectory:$Runner.runDirectory"
            ] + extraVolumes + [config.image, 'bash', '-e']
    }
}
