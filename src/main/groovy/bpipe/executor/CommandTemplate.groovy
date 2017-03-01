package bpipe.executor

import groovy.text.SimpleTemplateEngine
import groovy.util.logging.Log

@Log
class CommandTemplate {
   
    public static final long serialVersionUID = 0L;

    private static String CMD_SCRIPT_FILENAME = "cmd.sh"

    File populateCommandTemplate(File jobDir, String jobTemplateFile, Map properties) {
        
        /*
         * Create '.cmd.sh' wrapper used by the 'qsub' command
         */
        def cmdWrapperScript = new File(jobDir,CommandTemplate.CMD_SCRIPT_FILENAME)
        
        if(properties?.jobTemplate) {
            if(properties.jobTemplate instanceof Closure) {
                jobTemplateFile = String.valueOf(properties.jobTemplate(properties))
            }
            else 
                jobTemplateFile = String.valueOf(properties.jobTemplate)
        }
        
        File commandTemplate = bpipe.ReportGenerator.resolveTemplateFile(jobTemplateFile)
        
        log.info "Generating output from command template ${commandTemplate.absolutePath} to $cmdWrapperScript.absolutePath"
        SimpleTemplateEngine e  = new SimpleTemplateEngine()
        String templateText = commandTemplate.withReader { r ->
            e.createTemplate(r).make(properties).toString()
        }
        
        cmdWrapperScript.text = templateText
        
        return cmdWrapperScript
    }
}
