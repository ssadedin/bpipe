package bpipe.agent

import bpipe.PipelineError
import bpipe.cmd.BpipeCommand
import bpipe.cmd.RunPipelineCommand
import bpipe.worx.JMSWorxConnection
import bpipe.worx.WorxConnection
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Log

import javax.jms.BytesMessage
import javax.jms.Connection
import javax.jms.Destination
import javax.jms.Message
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.Queue
import javax.jms.TextMessage
import org.apache.activemq.ActiveMQConnectionFactory

@Log
@CompileStatic
class JMSAgent extends Agent {
    
    final static long MESSAGE_WAIT_TIMEOUT_MS = 30000
    
    final static File STOP_FILE = new File(".agent.stop")
    
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
                if(checkStop()) {
                    break
                }
            }
            catch(Exception e) {
                log.severe("Failure in message connect/receive loop: " + e.message)
                bpipe.Utils.closeQuietly(connection)
                connection = null
                Thread.sleep(15000)
            }
        }
        
        System.exit(0)
    }
    
    /**
     * Return true if the agent has been requested to stop via various
     * possible means.
     * <p>
     * The main aim here is to cater for shared user environments where the user requesting
     * the stop is different to the user who ran the agent. Two methods include,
     * sending a poison pill message containing only the word "stop", and creating a
     * "stop file" in the same directory as the working directory of the agent.
     * 
     * @return true if the agent has been requested to stop 
     */
    boolean checkStop() {
        
        if(stopRequested)
            return true
        
        if(STOP_FILE.exists()) {
            log.info "Stop file detected: exiting agent"
            
            // Try to delete it: if we don't have permissions, we might not be successful
            STOP_FILE.delete()
            
            this.stopRequested = true
            
            return true
        }
        
        return false
    }
    
    
    void receive() {
        
        // Although the default behavior ensures that no more than the allowed 
        // number of commands run at the same time, we don't want our agent to 
        // gobble up the queued commands if it is sharing the queue.
        // So we don't request another message until we know there is a permit
        // available
        waitForPermits()
            
        log.info "Waiting for next message ..."
        Message message = this.consumer.receive(MESSAGE_WAIT_TIMEOUT_MS)
        if(message == null) // timed out
            return
        
        String text
        if(message instanceof TextMessage)
            text = ((TextMessage)message).text
        else
        if(message instanceof BytesMessage) {
            BytesMessage bm = (BytesMessage)message
            byte [] body = new byte[bm.bodyLength]
            bm.readBytes(body)
            text = new String(body, 'UTF-8')
        }
        else
            throw new Exception('Unexpected message type received: ' + message.class.name)
            
        log.info "Received command: " + text
        
        if(text == "stop") {
            this.stopRequested = true
            return
        }
        
        if(text == "ping") {
            this.respondToPing(message)
            return
        }            
        
        Map commandAttributes = (Map)new JsonSlurper().parseText(text)
        
        if(config.containsKey('transform')) {
            commandAttributes = ((Closure)config.transform)(commandAttributes)
        }
        
        log.info "Processing command: " + commandAttributes
        AgentCommandRunner runner = this.processCommand(commandAttributes)
        
        if(message.getJMSReplyTo() || message.getStringProperty('reply-to') || message.getStringProperty('replyTo')) {
            log.info "ReplyTo set on message: will send message when complete"
            runner.completionListener = { Map result ->
                log.info "Sending reply for command $commandAttributes.id"
                
                BpipeCommand command = runner.command
                
                log.info "Loading checks from $command.dir "
                        
                def checks = bpipe.Check.loadAll(new File(command.dir, '.bpipe/checks'))
        
                Map<String,Object> resultDetails = (Map<String,Object>)[
                        command: commandAttributes,
                        result: result + [ 
                            checks:  checks.collect { [name: it.name, stage: it.stage, branch: it.branch, message: it.message, passed: it.passed] }
                        ]
                ]
                
                if(command instanceof RunPipelineCommand) {
                    String runDir = ((RunPipelineCommand)command).runDirectory?.canonicalPath
                    resultDetails['directory'] = runDir
                }
                sendReply(message, JsonOutput.prettyPrint(JsonOutput.toJson(resultDetails)))
            }
        }
        
        if(this.singleShot)
            stopRequested=true
    }
    
    void respondToPing(Message tm) {
        log.info "Received ping: responding to ${tm.getJMSReplyTo()}"
        String responseJSON = formatPingResponse()
        sendReply(tm, responseJSON)
    }

    private void sendReply(Message tm, String responseJSON) {
        Destination dest = tm.getJMSReplyTo()
        if(dest == null)
            if(tm.getStringProperty('reply-to'))
                dest = session.createQueue(tm.getStringProperty('reply-to'))

        if(dest == null)
            dest = session.createQueue(tm.getStringProperty('replyTo'))

        TextMessage response = session.createTextMessage(responseJSON)
        MessageProducer producer = session.createProducer(dest)
        if(tm.JMSCorrelationID)
            response.JMSCorrelationID = tm.JMSCorrelationID
            
        log.info "Sending reply to $dest"
        producer.send(response)
    }
    
    String formatPingResponse() {
       return JsonOutput.prettyPrint(JsonOutput.toJson([
            [ 
                status: 'ok',
                timestamp: System.currentTimeMillis(),
                details: [
                    user: System.properties['user.name'],
                    dir: System.properties['user.dir'],
                    executed: executed,
                    errors: errors
                ]
            ]
        ])) 
    }
    
    void waitForPermits() {
        
        if(concurrency == null)
            return
            
        int n = 0
        int waitMs = 0
        while(concurrency.availablePermits()==0) {
            Thread.sleep(2000)
            if(n++ % 5 == 0) {
                waitMs+=2000
                log.info "Blocked waiting for $waitMs for current commands to complete ..."
            }
        }
        
        return
    }
    
    void connect() {
        
        if(connection != null)
            return
            
        if(!(config.containsKey('brokerURL')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'brokerURL'")
            
        if(!(config.containsKey('commandQueue')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'queue'")
            
        log.info "Connecting to: ${config.brokerURL}"
            
        this.connection = new ActiveMQConnectionFactory((String)config.brokerURL).createConnection()
        this.connection.start()        
        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        this.queue = session.createQueue((String)config.commandQueue)
        this.consumer = session.createConsumer(queue)
        
        log.info "Connected to ActiveMQ $config.commandQueue @ $config.brokerURL"
    }

    @Override
    public WorxConnection createConnection() {
        if(config.getOrDefault('responseQueue', false)) {
            Queue queue = this.session.createQueue((String)config.responseQueue)
            return new JMSWorxConnection(queue, session)
        }
        else {
            // Create a dummy connection that does not send anything
            return new WorxConnection() {
                
                    void sendJson(String path, String json) {}
    
                     Object readResponse()  { [:] }
                
                     void close()  { }
            }
        }
    }

}
