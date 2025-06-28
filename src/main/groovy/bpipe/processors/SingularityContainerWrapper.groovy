/*
 * Copyright (c) 2012-2023 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe.processors

import bpipe.*
import bpipe.storage.BindStorageLayer
import bpipe.storage.StorageLayer
import groovy.util.logging.Log

/**
 * A wrapper that adapts commands to run inside a singularity container
 * 
 * @author simon.sadedin
 */
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

        String singularityCommand = config.getOrDefault("singularityExecutable", "singularity")
        log.info "Resolved singularity image $config.image for command $command.id at $imagePath"

        String shell = config.getOrDefault('shell', '/bin/bash')

        String execOptions = config.execOptions ?: ''
        
        command.shell = 
            [
                singularityCommand,
                "exec", execOptions,
                "-B", Runner.runDirectory,
                "--pwd", bpipe.Runner.runDirectory,
            ] + extraVolumes + [imagePath, shell, '-e']
            
        log.info "Singularity command prefix: " + command.shell.join(' ')
    }
}
