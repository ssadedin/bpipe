package bpipe.notification

import org.apache.activemq.ActiveMQConnection

import org.apache.activemq.ActiveMQConnectionFactory

import bpipe.*

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
abstract class JMSNotificationChannel implements NotificationChannel {
    
    Queue queue
    
    Session session
    
    Connection connection
    
    Map config
    
    MessageProducer producer
    
    public JMSNotificationChannel(Map config) {
        try {
            if(!(config.containsKey('queue')))
                throw new PipelineError("JMS configuration is missing required key 'queue'")

            this.config = config;
            this.connection = createConnection(config)
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
        
        if(event == PipelineEvent.FINISHED) {
            model.remove('commands')
        }
        
        Map eventDetails = HTTPNotificationChannel.sanitiseDetails(model)

        eventDetails.description = subject
        
        TextMessage msg
        String messageBody
        if(event == PipelineEvent.SEND) {
            messageBody = model['send.content']
        }
        else {
            messageBody = JsonOutput.toJson(eventDetails)
        }

        msg = session.createTextMessage(messageBody)
        
        this.configureMessage(msg, event, eventDetails)
        
        msg.setJMSType(event.name())        
        
        log.info "Send $event to ActiveMQ queue"
        
        Queue queue = this.queue
        if(model.containsKey('queue')) {
            queue = session.createQueue(model.queue)
        }
        producer.send(queue, msg)
    }

    /**
     * Optional method for subclasses to implement to set custom properties on the connection
     */
    void configureMessage(TextMessage msg, PipelineEvent event, Map<String, Object> model) {
    }

    @Override
    public String getDefaultTemplate(String contentType) {
        return 'email.template.txt'; // not used
    }
   
    void close() {
        log.info "Closing JMS connections to $config.queue (brokerURL=$config.brokerURL)"
        Utils.closeQuietly(session)
        Utils.closeQuietly(connection)
    }
    
    abstract Connection createConnection(Map config)
}
