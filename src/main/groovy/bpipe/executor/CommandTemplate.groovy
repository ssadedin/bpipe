package bpipe.executor

import groovy.text.SimpleTemplateEngine
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
class CommandTemplate {
   
    public static final long serialVersionUID = 0L;

    private static String CMD_SCRIPT_FILENAME = "cmd.sh"

    File populateCommandTemplate(File jobDir, String jobTemplateName, Map properties) {
        
        String templateText = renderCommandTemplate(jobTemplateName, properties)
        
        /*
         * Create '.cmd.sh' wrapper used by the 'qsub' command
         */
        def cmdWrapperScript = new File(jobDir,CommandTemplate.CMD_SCRIPT_FILENAME)
        
        log.info "Writing command template to to $cmdWrapperScript.absolutePath"
        cmdWrapperScript.text = templateText
        
        return cmdWrapperScript
    }
    
    @CompileStatic
    String renderCommandTemplate(String jobTemplateName, Map properties) {
        
        if(properties?.jobTemplate) {
            if(properties.jobTemplate instanceof Closure) {
                jobTemplateName = String.valueOf(((Closure)properties['jobTemplate'])(properties))
            }
            else 
                jobTemplateName = String.valueOf(properties.jobTemplate)
        }
        
        
        File commandTemplate = bpipe.ReportGenerator.resolveTemplateFile(jobTemplateName)
        log.info "Generating output from command template ${commandTemplate.absolutePath}"
        
        SimpleTemplateEngine e  = new SimpleTemplateEngine()
        String templateText = commandTemplate.withReader { r ->
            e.createTemplate(r).make(properties).toString()
        }
        return templateText
    }
}
