package bpipe.notification

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

import com.amazon.sqs.javamessaging.ProviderConfiguration
import com.amazon.sqs.javamessaging.SQSConnection
import com.amazon.sqs.javamessaging.SQSConnectionFactory
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.sqs.AmazonSQSClientBuilder

@Log
class AWSSQSNotificationChannel extends JMSNotificationChannel {
    
    Queue queue
    
    Session session
    
    Connection connection
    
    Map config
    
    MessageProducer producer
    
    public AWSSQSNotificationChannel(Map config) {
        super(config)
    }
    
    void configureMessage(TextMessage msg, PipelineEvent event, Map<String, Object> model) {
        msg.setStringProperty('JMSXGroupID', 'bpipe.' + event.name())
        msg.setStringProperty('JMS_SQS_DeduplicationId', System.currentTimeMillis().toString())
    }

    Connection createConnection(Map config) {
        createSQSConnection(config)
    }

    static Connection createSQSConnection(Map config) {
        
        AWSCredentials credentials
        
        if(config.containsKey('accessKey')) {
            credentials = new BasicAWSCredentials((String)config.accessKey, (String)config.accessSecret);
        }
        else 
        if(config.containsKey('profile')) {
            Map keyInfo = bpipe.executor.AWSCredentials.theInstance.keys[config.profile]
            if(keyInfo) {
                credentials = new BasicAWSCredentials(keyInfo.access_key_id, keyInfo.secret_access_key);
            }
            else 
             throw new IllegalStateException("Profile $config.profile for SQS connection could not be found in your AWS credentials file")
        }
        
        if(credentials == null) {
            throw new IllegalStateException(
            "Unable to resolve credentials for AWS SQS connection. Please specify AWS " +
            "profile in your credentials file, or specify accessKey and accessSecret in your config file")
        }
 
        SQSConnectionFactory connectionFactory = new SQSConnectionFactory(
                 new ProviderConfiguration(),
                 AmazonSQSClientBuilder.standard()
                     .withRegion(config.region)
                     .withCredentials(new AWSStaticCredentialsProvider(credentials))
                 );
 
        // Create the connection
        return connectionFactory.createConnection();
    }
}
