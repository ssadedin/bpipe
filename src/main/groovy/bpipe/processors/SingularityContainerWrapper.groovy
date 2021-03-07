package bpipe.processors

import bpipe.*
import bpipe.storage.BindStorageLayer
import bpipe.storage.StorageLayer
import groovy.util.logging.Log

@Log
class SingularityContainerWrapper implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        
        Map config = command.processedConfig.container
        
        def extraVolumes = Config.listValue(config, 'storage').collect { name ->
            
            BindStorageLayer layer = (BindStorageLayer)StorageLayer.create(name)

            ["-B", layer.base]
        }
        .unique()
        .sum()
        ?: []
        
        if(extraVolumes)
            log.info "Additonal volume mounts configured for command $command.id: $extraVolumes"
        
        File imagePath = new File(config.image.toString())
        if(!imagePath.exists()) {
            def scriptDirImagePath = new File(Config.scriptDirectory, 'containers/' + config.image)
            if(scriptDirImagePath.exists()) {
                imagePath = scriptDirImagePath
            }
        }

        log.info "Resolved singularity image $config.image for command $command.id at $imagePath"
        
        command.shell = 
            [
                "singularity",
                "run",
                "-B", Runner.runDirectory,
                "--pwd", bpipe.Runner.runDirectory,
            ] + extraVolumes + [imagePath, 'bash', '-e']
            
       log.info "Singularity command prefix: " + command.shell.join(' ')
    }
}
