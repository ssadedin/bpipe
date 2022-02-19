package bpipe.notification

import bpipe.*
import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.util.logging.Log;

import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

@CompileStatic
class SlackNotificationException extends Exception {
    
    public SlackNotificationException(String message) {
        super(message);
    }
}

/**
 * A dummy notification channel that just saves files in a "notifications" folder
 * 
 * @author simon.sadedin@mcri.edu.au
 */
@Log
@CompileStatic
class SlackNotificationChannel implements NotificationChannel {
    
    Map cfg
    
    File dir = new File(".bpipe/sent/slack")
    
    OkHttpClient client = new OkHttpClient()

    String channel
    
    String token
    
    public SlackNotificationChannel(Map cfg) {
        this.cfg = cfg
        
        if(!cfg.containsKey('token'))
            throw new IllegalArgumentException("Slack notification channel requires token property to be specified")
        
        if(!cfg.containsKey('channel'))
            throw new IllegalArgumentException("Slack notification channel requires the channel property to be specified")

         this.channel = cfg.channel
         this.token = (String)cfg.token
    }

    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        log.info "Sending slack notification for event $event (subject = $subject)"
           
        String text
        Request request
        if(event == PipelineEvent.SEND) {
                
            def content = model["send.content"]
            boolean isJson = (model["send.contentType"] == "application/json")
            if(content instanceof String) {
                text = isJson ? ((String)content + '\n') : content
            }
            else
            if(isJson) {
               String json = Utils.safeJson(content)
               text = json + '\n'
            }
            else
            if(content instanceof File)
                text = ((File)model["send.content"]).text
                
            if(model['send.file']) {
                
                def fileValue = model['send.file']
                File file
                if(fileValue instanceof String)
                    file = new File(fileValue)
                else
                    file = (File)fileValue
                
                log.info "Uploading file $file.absolutePath to slack to attach to SEND event with content " + file.text
                
                RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(),  RequestBody.create(file.bytes))
                        .addFormDataPart("channels", channel)
                        .addFormDataPart("initial_comment", text)
                        .build();

                request = new Request.Builder()
                        .url("https://slack.com/api/files.upload")
                        .header("Authorization", "Bearer $token")
                        .post(requestBody)
                        .build();
            }
        }
        
        if(request == null) {
            
            model.subject = subject

            if(!text)
                text = template.make(model).toString()
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), Utils.safeJson(channel: channel, text: text));
          
            request = new Request.Builder()
                .url("https://slack.com/api/chat.postMessage")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build();
        }
        
        Response response = client.newCall(request).execute()
        if (!response.isSuccessful())
            throw new SlackNotificationException(new String(response.body().bytes()))

        log.info "Successfully sent message to slack channel $channel"
        
    }

    @Override
    public String getDefaultTemplate(String contentType) {
        "slack.template.txt"
    }
}
