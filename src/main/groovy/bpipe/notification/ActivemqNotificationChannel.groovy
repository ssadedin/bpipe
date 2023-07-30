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
class ActivemqNotificationChannel extends JMSNotificationChannel {
    
    Queue queue
    
    Session session
    
    Connection connection
    
    Map config
    
    MessageProducer producer
    
    public ActivemqNotificationChannel(Map config) {
        super(config)
    }

    void configureMessage(TextMessage msg, PipelineEvent event, Map<String, Object> eventDetails) {
        if(event == PipelineEvent.SEND) {
            eventDetails.each { k,v ->
                
                if(v instanceof PipelineStage) 
                    v = v.toProperties() // TODO: dead code - remove
                else
                if(k != 'send.content')
                    msg.setStringProperty(k,v)
            }
        }
    }
   
    Connection createConnection(Map config) {
        createActiveMQConnection(config)
    }

    static Connection createActiveMQConnection(Map config) {
        if(!(config.containsKey('brokerURL')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'brokerURL'")
            
           
        def connectionFactory = new ActiveMQConnectionFactory(brokerURL: config.brokerURL)
        
        if(config.containsKey('username')) {
            if(!(config.containsKey('password')))
                throw new PipelineError("ActiveMQ configuration is missing required key 'password'")
                
            def password = config.password
            if(password instanceof Closure) {
                password = password()
            }
            assert config.username instanceof String
            assert config.password instanceof String

            log.info "Configured ActiveMQ using usernmae $config.username"
            return connectionFactory.createConnection(config.username, config.password)
        }
        else {
            log.info "Configured ActiveMQ without username / password"
            return connectionFactory.createConnection()
        }        
    }
}
