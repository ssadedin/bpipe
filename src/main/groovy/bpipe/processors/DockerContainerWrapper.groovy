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
import bpipe.executor.AWSEC2CommandExecutor
import bpipe.executor.CommandExecutor
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
    
    
    CommandExecutor commandExecutor
    
    DockerContainerWrapper(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor
    }

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
		
        Map config = command.processedConfig.container
        
        String shell = config.getOrDefault('shell', '/bin/bash')

        List extraVolumes = Config.listValue(config, 'storage').collect { name ->
            
            BindStorageLayer layer = (BindStorageLayer)StorageLayer.create(name)

            ["-v", "$layer.base:$layer.base"]
        }
        .unique()
        .sum()
        ?: []
        
        // Todo: move to some kind of 'configureContainerVolumes' on command executor?
        if(commandExecutor instanceof AWSEC2CommandExecutor) {
            extraVolumes.addAll(["-v", "/tmp/bpipe-aws:/tmp/bpipe-aws"])
        }
        
		log.info("Volume arguments for docker command: ${extraVolumes}")
		
		List<String> dockerCommand = [config.getOrDefault('command', [shell])].flatten()

		List<String> dockerRunOptions = Config.listValue(config, 'options') ?: ['--rm']

		String entryPoint = config.getOrDefault('entryPoint', null)
		List<String> entryPointArg
		if(entryPoint == "default") {
			entryPointArg = []
		}
		else {
			 entryPointArg = entryPoint ? ["--entrypoint", entryPoint] : ["--entrypoint", "/usr/bin/env"]
		}
        
        List<String> additionalArgs = []
        if(config.containsKey('platform')) {
            additionalArgs.addAll(["--platform", config.platform])
        }
        
        if(config.containsKey('inherit_user') && config.inherit_user == true) {
            Map userInfo = Utils.getUserInfo()
            log.info("Using uid: $userInfo for docker command")
            additionalArgs.addAll(["--user", "$userInfo.uid"])
        }

        command.shell = 
            [
                "docker",
                "run",
                "-i",
                *additionalArgs,
                *dockerRunOptions,
                *entryPointArg,
                "-w", Runner.runDirectory, 
                "-v", "$Runner.runDirectory:$Runner.runDirectory",
                *extraVolumes,
                config.image,
                *dockerCommand
            ]  
            
       log.info "Configured docker shell command as: " + (command.shell.join(' '))
    }
}
