package bpipe.processors

import bpipe.Command
import bpipe.CommandProcessor
import bpipe.ResourceUnit
import groovy.util.logging.Log

@Log
class EnvironmentVariableSetter implements CommandProcessor {

    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        
        def configEnv = command.processedConfig.getOrDefault('env', false)
        if(!configEnv) {
            return
        }

        if(configEnv instanceof ConfigObject) {
            command.command = 
                configEnv.collect { "export $it.key='$it.value'" }.join('; ')  +
                '; ' +
                command.command
        }
        else {
            String error = "env setting in config for $command.name was of type ${configEnv.class} but expected a ConfigObject. Please check the format in your file."
            System.err.println error
            log.warning(error)
        }
    }
}
