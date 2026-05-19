package bpipe.processors

import bpipe.Command
import bpipe.CommandProcessor
import bpipe.ResourceUnit
import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
class UvEnvWrapper implements CommandProcessor {

    @CompileStatic
    @Override
    public void transform(Command command, List<ResourceUnit> resources) {
        if(!command.processedConfig.containsKey('uv_env') && !command.processedConfig.containsKey('uv_project'))
            return

        // Treat null values as unset
        if(command.processedConfig.uv_env == null && command.processedConfig.uv_project == null)
            return

        Map<String,Object> config = (Map<String,Object>)command.processedConfig

        String uv = Utils.resolveExe("uv", "uv", config)

        if(command.processedConfig.containsKey('uv_project') && command.processedConfig.uv_project != null) {
            // Use "uv run" mode - uv manages the environment automatically
            String projectDir = (String)command.processedConfig.uv_project

            String prefix = "$uv run --directory $projectDir -- "

            log.info "Configuring uv project environment using command prefix: $prefix"
            command.command = prefix + command.command
        }
        else if(command.processedConfig.uv_env != null) {
            // Use venv activation mode
            String uvEnv = (String)command.processedConfig.uv_env

            String prefix = """


                export VIRTUAL_ENV="$uvEnv"; export PATH="$uvEnv/bin:\$PATH"; unset PYTHONHOME;
            """.stripIndent()

            log.info "Configuring uv virtual environment using command prefix: $prefix"
            command.command = prefix + command.command
        }
    }
}
