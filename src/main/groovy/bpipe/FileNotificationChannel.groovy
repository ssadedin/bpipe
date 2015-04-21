package bpipe

import groovy.text.Template
import groovy.util.logging.Log;

import java.util.Map;

/**
 * A dummy notification channel that just saves files in a "notifications" folder
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
class FileNotificationChannel implements NotificationChannel {
    
    ConfigObject cfg
    
    File dir = new File("notifications")
    
    int count = 1

    public FileNotificationChannel(ConfigObject cfg) {
        this.cfg = cfg
    }

    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        log.info "Saving file for event $event (subject = $subject)"
        if(!dir.exists())
            dir.mkdirs()
            
        File targetFile = new File(dir, "${count}_${event.name()}.txt")
        if(event == PipelineEvent.SEND) {
            if(model.containsKey("send.file") && model["send.file"] != null)
                targetFile = new File(model["send.file"])
                
            if(model["send.content"] instanceof String)
                targetFile.text = model["send.content"]
            else
            if(model["send.content"] instanceof File)
                targetFile << model["send.content"].bytes
        }
        else {
            if(!dir.exists())
                dir.mkdirs() 
            targetFile.text = template.make(model).toString()
        }
    }

    @Override
    public String getDefaultTemplate(String contentType) {
        "file.template.txt"
    }

}
