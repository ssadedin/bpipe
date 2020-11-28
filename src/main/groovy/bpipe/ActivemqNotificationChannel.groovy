package bpipe

import org.apache.activemq.ActiveMQConnectionFactory


import groovy.util.ConfigObject
import groovy.util.logging.Log
import groovy.json.JsonOutput
import groovy.text.Template

import java.util.Map
import javax.jms.Connection
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.Queue
import javax.jms.TextMessage

@Log
class ActivemqNotificationChannel implements NotificationChannel {
    
    Queue queue
    
    Session session
    
    Connection connection
    
    ConfigObject config
    
    MessageProducer producer
    
    public ActivemqNotificationChannel(ConfigObject config) {
        try {
            this.config = config;
            
            if(!(config.containsKey('brokerURL')))
                throw new PipelineError("ActiveMQ configuration is missing required key 'brokerURL'")
                
            if(!(config.containsKey('queue')))
                throw new PipelineError("ActiveMQ configuration is missing required key 'queue'")
            
            this.connection = new ActiveMQConnectionFactory(brokerURL: config.brokerURL).createConnection()
            this.connection.start()
            this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
            this.producer = session.createProducer()
            this.queue = session.createQueue(config.queue)
        }
        catch(Exception e) {
            log.severe("Unable to create message queue to " + config.brokerURL + ": " + e.message)
            throw e
        }
    }


    @Override
    public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {
        
        Map eventDetails = HTTPNotificationChannel.sanitiseDetails(model)

        eventDetails.description = subject
        
        TextMessage msg
        String messageBody
        if(event == PipelineEvent.SEND) {
            messageBody = model['send.content']
            msg = session.createTextMessage(messageBody)
            eventDetails.each { k,v ->
                
                if(v instanceof PipelineStage)
                    v = v.toProperties()
                else
                msg.setStringProperty(k,v)
            }
        }
        else {
          messageBody = JsonOutput.toJson(eventDetails)
          msg = session.createTextMessage(messageBody)
        }
        
          
        msg.setJMSType(event.name())        
        
        log.info "Send $event to ActiveMQ queue"
        producer.send(queue, msg)
    }

    @Override
    public String getDefaultTemplate(String contentType) {
        return 'email.template.txt'; // not used
    }

//    @Override
//    public void onEvent(PipelineEvent eventType, String desc, Map<String, Object> details) {
//          Map eventDetails = bpipe.Utils.sanitizeForSerialization(details)
//          TextMessage msg = session.createTextMessage(JsonOutput.toJson(eventDetails))
//          msg.setJMSType(eventType.name())
//    }

//    static void main(String [] args) {
//        new ActiveMQConnectionFactory(brokerURL: 'tcp://localhost:61616').createConnection().with {
//            start()
//            createSession(false, Session.AUTO_ACKNOWLEDGE).with {
//              TextMessage msg = createTextMessage("test")
//              msg.setJMSType("STAGE_STARTED")
//              createProducer().send(createQueue("queue"), msg)
//            }
//            close()
//          }
//    }
    
    void close() {
        log.info "Closing ActiveMQ connections to $config.queue at $config.brokerURL"
        Utils.closeQuietly(session)
        Utils.closeQuietly(connection)
    }
}
