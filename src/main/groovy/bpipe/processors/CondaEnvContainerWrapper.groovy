package bpipe.processors

import bpipe.Command
import bpipe.CommandProcessor
import bpipe.Config
import bpipe.ResourceUnit
import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
class CondaEnvContainerWrapper implements CommandProcessor {

    @CompileStatic
    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        if(!command.processedConfig.containsKey('conda_env'))
            return
            
        String condaEnv = (String)command.processedConfig.conda_env

        Map<String,Object> config = (Map<String,Object>)command.processedConfig

       String shell = config.getOrDefault('shell', '/bin/bash')
       
        // Crude but works for most shell setups
        String shellType = shell.tokenize("/")[-1]
        
        String conda = Utils.resolveExe("conda", "conda")
        
        String prefix = 
        """

            $conda info --envs

            export SHELL="$shell"; eval "\$('$conda' 'shell.${shellType}' 'hook' 2> /dev/null)"; conda activate $condaEnv ;
        """.stripIndent()
        
        log.info "Configuring conda environment using command prefix: $prefix"

        command.command = prefix + command.command
    }
}
