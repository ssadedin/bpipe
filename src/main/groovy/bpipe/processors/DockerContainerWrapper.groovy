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
 * A wrapper that adapts commands to run inside a docker container
 * 
 * @author simon.sadedin
 */
@Log
class DockerContainerWrapper implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
		
        Map config = command.processedConfig.container
        
        String shell = config.getOrDefault('shell', '/bin/bash')

        def extraVolumes = Config.listValue(config, 'storage').collect { name ->
            
            BindStorageLayer layer = (BindStorageLayer)StorageLayer.create(name)

            ["-v", "$layer.base:$layer.base"]
        }
        .unique()
        .sum()
        ?: []
        
		log.info("Volume arguments for docker command: ${extraVolumes}")
		
		List<String> dockerCommand = [config.getOrDefault('command', [shell,'-e'])].flatten()

		String entryPoint = config.getOrDefault('entryPoint', null)
		List<String> entryPointArg
		if(entryPoint == "default") {
			entryPointArg = []
		}
		else {
			 entryPointArg = entryPoint ? ["--entrypoint", entryPoint] : ["--entrypoint", "/usr/bin/env"]
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
