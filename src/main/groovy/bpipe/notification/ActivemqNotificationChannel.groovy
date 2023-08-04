package bpipe.notification

import groovy.json.JsonException
import groovy.json.JsonSlurper
import org.apache.activemq.ActiveMQConnection

import org.apache.activemq.ActiveMQConnectionFactory

import bpipe.*

import groovy.util.logging.Log
import groovy.json.JsonOutput
import groovy.text.Template

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
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

        if (config.containsKey('username') && config.containsKey('credentialsJsonFile')) {
            throw new PipelineError("ActiveMQ configuration username and credentialsJsonFile are mutually exclusive, please only provide one or the other")
        }
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
        else if (config.containsKey('credentialsJsonFile')) {

            /**
             * If optional credentialsJsonKeyMapping is provided, then use that to get the json property values,
             * otherwise, assume the file contains username and password properties.
             *
             * Expected json format of credentialsJsonFile:
             * {
             *     "username": username,
             *     "password": secret
             * }
             *
             * or if credentialsJsonKeyMapping is provided:
             * {
             *     "client_id": oidc-client-id,
             *     "id_token": oidc-id-token
             * }
             *
             * where credentialsJsonKeyMapping is:
             * {
             *    "username": "client_id",
             *    "password": "id_token"
             * }
             */
            try {
                Path filePath = Paths.get(config.credentialsJsonFile as String)
                if (!Files.exists(filePath)) {
                    throw new PipelineError("ActiveMQ configuration credentialsJsonFile does not exist: ${config.credentialsJsonFile}")
                }
                log.info("Using ActiveMQ credentialsJsonFile: ${config.credentialsJsonFile}")
                String fileContents = filePath.toFile().text
                def credentialsJson = new JsonSlurper().parseText(fileContents)
                String usernameKey = config.credentialsJsonKeyMapping?.username ?: "username"
                String passwordKey = config.credentialsJsonKeyMapping?.password ?: "password"
                log.info("Using usernameKey=$usernameKey and passwordKey=$passwordKey from credentials file for lookup")
                String username = credentialsJson."$usernameKey"
                String password = credentialsJson."$passwordKey"

                if (username && password) {
                    return connectionFactory.createConnection(username, password)
                } else {
                    throw new PipelineError("ActiveMQ configuration using ${config.credentialsJsonFile} has missing username and/or password")
                }
            }
            catch (InvalidPathException | JsonException | IOException e) {
                throw new PipelineError("ActiveMQ configuration using ${config.credentialsJsonFile} is not valid, ${e.message}")
            }
        }
        else {
            log.info "Configured ActiveMQ without username / password"
            return connectionFactory.createConnection()
        }        
    }
}
