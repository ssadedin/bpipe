package bpipe

import groovy.util.ConfigObject
import groovy.util.logging.Log
import groovy.json.JsonOutput
import groovy.text.Template

/**
 * Notification provider that makes REST-style API POSTs
 * 
 * @author Simon Sadedin
 */
@Log
class HTTPNotificationChannel implements NotificationChannel {
    
    Map config
    
    String contentType
    
    public HTTPNotificationChannel(ConfigObject config) {
        this(config,'application/json')
    }

    public HTTPNotificationChannel(Map config, String contentType) {
        try {
            this.config = config;
            
            if(!(config.containsKey('url')))
                throw new PipelineError("HTTP configuration is missing required key 'url'")
                
            this.contentType = contentType
        }
        catch(Exception e) {
            log.severe("Unable to create message queue to " + config.brokerURL + ": " + e.message)
            throw e
        }
    }

    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        
        def eventDetails 
        if('send.content' in model) {
            eventDetails = model['send.content']
            if(!(eventDetails instanceof String)) {
                eventDetails = sanitiseDetails(eventDetails)        
            }
        }
        else {
            eventDetails = sanitiseDetails(model)        
        }
        
        String url = config.url
        if('send.url' in model) {
            URI uri = new URI(model['send.url'])
            if(uri.isAbsolute()) {
                url = config.url
            }
            else {
                if(url.endsWith('/'))
                    url = url[0..-2]

                String sendUrl = model['send.url']
                if(sendUrl.startsWith('/'))
                    sendUrl = sendUrl.substring(1)

                url = [url,sendUrl].join('/')
                
                log.info "Combined URLs from send and base config: $url"
            }
        }


        sendToURL(url, template.make(content:eventDetails).toString())
    }
    
    void sendToURL(def content) {
        sendToURL(config.url, content)
    }

    void sendToURL(String url, def content) {
        
        def contentIn = resolveJSONContentSource(content)
        
        if(this.config.getOrDefault('prettyPrint', false) && (contentIn instanceof String)) {
            contentIn = JsonOutput.prettyPrint(contentIn)
        }
        
        if(config.params) {
            if(!url.contains('?'))
                url += '?'
            
            url += config.params.collect { key, value -> 
                URLEncoder.encode(key) + '=' + (value==null?'':URLEncoder.encode(value)) 
            }.join('&')
        }
        
        Map headers = ['Content-Type':this.contentType]
        if(config?.username && config?.password) {
            headers.Authorization = 'Basic ' + "$config.username:$config.password".bytes.encodeBase64().toString()
        }
        
        if(config?.containsKey('headers')) {
            if(!(config.headers instanceof Map)) 
                throw new PipelineError('Headers should be passed to send statement as a Map object')
            headers += config.headers
        }
        
        log.info "Sending to $config.url with content type $contentType and headers: ${headers.grep { !it.key=='Authorization' }}"
        try {
            Utils.connectAndSend(contentIn, url, headers)
        }
        finally {
            if(contentIn.respondsTo('close'))
                contentIn.close()
        }
    }
    
    def resolveJSONContentSource(def contentIn) {
            
        if(contentIn instanceof PipelineInput) {
            contentIn = new File(contentIn.toString()).newInputStream()
        }
        else
        if(contentIn instanceof File)
            contentIn = contentIn.newInputStream()
        else
        if(contentIn instanceof Map || contentIn instanceof List) {
            contentIn = JsonOutput.toJson(contentIn)
        }
        return contentIn
    }
    
    /**
     * Some objects / properties do not convert to JSON properly, and
     * cause things like infinite recursion or overly inflated output.
     * 
     * @param model
     * @return  Sanitised model for sending
     */
    static Map sanitiseDetails(Map model) {
        model.collectEntries { key, value ->
            if(value instanceof Number)
                [key,value]
            else {
                if(value instanceof PipelineStage)
                    value = value.toProperties()
                return [key, 
                    value instanceof Map || value instanceof List ? JsonOutput.toJson(value) : String.valueOf(value)
                ]
            }
        }
    }
    
    @Override
    public String getDefaultTemplate(String contentType) {
        return null
    }
}
