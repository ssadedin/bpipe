package bpipe.agent

import bpipe.PipelineError
import bpipe.worx.JMSWorxConnection
import bpipe.worx.WorxConnection
import groovy.json.JsonSlurper
import groovy.util.logging.Log

import javax.jms.Connection
import javax.jms.Message
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.Queue
import javax.jms.TextMessage
import org.apache.activemq.ActiveMQConnectionFactory

@Log
class JMSAgent extends Agent {
    
    Queue queue
    
    Session session
    
    Connection connection
    
    MessageConsumer consumer
    
    ConfigObject config
    
    JMSAgent(ConfigObject config) {
        this.config = config
    }
    
    void run() {
        while(true) {
            try {
                connect()
                receive()
            }
            catch(Exception e) {
                log.severe("Failure in message connect/receive loop: " + e.message)
                connection = null
                Thread.sleep(15000)
            }
        }
    }
    
    void receive() {
        Message message = this.consumer.receive()
        if(!(message instanceof TextMessage))
            throw new Exception('Unexpected message type received: ' + message.class.name)
            
        TextMessage tm = (TextMessage)message
        log.info "Received command: " + tm.text
        
        Map commandAttributes = new JsonSlurper().parseText(tm.text)
        
        log.info "Processing command: " + commandAttributes
        this.processCommand(commandAttributes)
    }
    
    void connect() {
        
        if(connection != null)
            return
            
        if(!(config.containsKey('brokerURL')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'brokerURL'")
            
        if(!(config.containsKey('commandQueue')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'queue'")
            
        if(!(config.containsKey('responseQueue')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'responseQueue'")
            
        this.connection = new ActiveMQConnectionFactory(brokerURL: config.brokerURL).createConnection()
        this.connection.start()        
        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        this.queue = session.createQueue(config.commandQueue)
        this.consumer = session.createConsumer(queue)
        
        log.info "Connected to ActiveMQ $config.queue @ $config.brokerURL"
    }

    @Override
    public WorxConnection createConnection() {
        Queue queue = this.session.createQueue(config.responseQueue)
        return new JMSWorxConnection(queue, session)
    }

}
