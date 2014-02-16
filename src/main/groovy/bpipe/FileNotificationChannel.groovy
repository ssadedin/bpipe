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
       dir.mkdirs() 
    }

    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        log.info "Saving file for event $event (subject = $subject)"
        new File(dir, "${count}_${event.name()}.txt").text = template.make(model).toString()
    }

    @Override
    public String getDefaultTemplate() {
        "file.template.txt"
    }

}
