package bpipe

import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.util.logging.Log;

/**
 * A dummy notification channel that just saves files in a "notifications" folder
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@CompileStatic
class FileNotificationChannel implements NotificationChannel {
    
    Map cfg
    
    File dir = new File("notifications")
    
    int count = 1

    public FileNotificationChannel(Map cfg) {
        this.cfg = cfg
    }

    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        log.info "Saving file for event $event (subject = $subject)"
        if(!dir.exists())
            dir.mkdirs()
            
        File targetFile = new File(dir, "${count}_${event.name()}.txt")
        if(event == PipelineEvent.SEND) {
            if(model.containsKey("send.file") && model["send.file"] != null && this.cfg.get('customTarget',true)) 
                targetFile = new File(String.valueOf(model["send.file"]))
                
            modelContentToFile(model, targetFile)

            log.info "Saved content to ${targetFile}"
        }
        else {
            if(!dir.exists())
                dir.mkdirs() 

            targetFile.text = template.make(model).toString()
            log.info "Saved content to ${targetFile}"
        }
        ++count
    }

    static void modelContentToFile(final Map model, final File targetFile) {
        targetFile.absoluteFile.parentFile.mkdirs()
        def content = model["send.content"]
        boolean isJson = (model["send.contentType"] == "application/json")
        if(content instanceof String) {
            targetFile.text = isJson ? ((String)content + '\n') : content
        }
        else
        if(isJson) {
           String json = Utils.safeJson(content)
           targetFile.text = json + '\n'
        }
        else
        if(content instanceof File)
            targetFile << ((File)model["send.content"]).bytes
    }

    @Override
    public String getDefaultTemplate(String contentType) {
        "file.template.txt"
    }
}
