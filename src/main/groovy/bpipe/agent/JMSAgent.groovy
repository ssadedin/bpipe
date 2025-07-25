package bpipe.agent

import bpipe.Pipeline
import bpipe.PipelineError
import bpipe.Sender
import bpipe.Utils
import bpipe.cmd.BpipeCommand
import bpipe.cmd.RunPipelineCommand
import bpipe.notification.AWSSQSNotificationChannel
import bpipe.notification.ActivemqNotificationChannel
import bpipe.worx.JMSWorxConnection
import bpipe.worx.WorxConnection
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import java.util.logging.Logger
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
import org.apache.activemq.ActiveMQSession

@CompileStatic
class JMSAgent extends Agent {
    
    final static long MESSAGE_WAIT_TIMEOUT_MS = 60000
    
    final static File STOP_FILE = new File(".agent.stop")
    
    /**
     * Per agent instance logging - initialised in run method
     */
    Logger log 

    /**
     * Global logger for static methods
     */
    final static Logger logger = Logger.getLogger('JMSAgent')   

    Queue queue
    
    Session session
    
    Connection connection
    
    MessageConsumer consumer
    
    ConfigObject config
    
    /**
     * Whether bpipe agent will acknowledge messages as soon as it reads them or only when it
     * actually runs a pipeline
     */
    String acknowledgeMode
    
    JMSAgent(ConfigObject config) {
        super(config)
        this.config = config
        this.acknowledgeMode = config.getOrDefault('acknowledgeMode', 'run')
    }
    
    /**
     * Main loop. Accepts messages and hands off for processing
     */
    void run() {
        
        this.log = Logger.getLogger('JMSAgent:' + name)

        log.info "Acknowledge mode is $acknowledgeMode"
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
            
        log.info "Received command: [" + text + "], JMSType=" + message.getJMSType() 
        
        if(text == "stop") {
            this.stopRequested = true
            acknowledgeRun(message)
            return
        }
        
        if(text.trim() == "ping") {
            this.respondToPing(message)
            acknowledgeRun(message)
            return
        }            
        
        String replyToValue = getMessageReplyTo(message)

        Map commandAttributes
        try {
            commandAttributes = (Map)new JsonSlurper().parseText(text)
        }
        catch(Exception e) {
            handleBadCommandMessage(e, message)
            return
        }
        
        if(config.containsKey('transform')) {
            commandAttributes = ((Closure)config.transform)(commandAttributes)
        }
        
        log.info "Processing command: " + commandAttributes
        AgentCommandRunner runner = this.processCommand(commandAttributes) {
            // Callback invoked when command actually gets to execute
            acknowledgeRun(message)
        }
        

        if(replyToValue) {

            log.info "ReplyTo set on message: will send message when complete"
            
            // Write out the completion listener
            BpipeCommand command = runner.command
            if(command instanceof RunPipelineCommand) {
                if(config.getOrDefault('replyMode', 'hook') == 'hook') {
                    setupHookReply(commandAttributes, replyToValue, command, message)
                }
                else {
                    setupDirectReply(commandAttributes, (RunPipelineCommand)command, message, runner)
                }
            }
        }
        
        if(this.singleShot) {
            stopRequested=true
            
            log.info "Single shot command running : stop flag set and waiting for command completion"
            
            // Wait for current command to finish before exiting
            while(runner.runState <2) {
                Thread.sleep(3000)
            }
        }
    }

    private void handleBadCommandMessage(Exception e, Message message) {
        Utils.logException(logger, "Failed parsing command message to Map as JSON", e)
        message.acknowledge()
        String replyToValue = getMessageReplyTo(message)
        if(replyToValue) {
            Map replyValue = [
                command: null,
                status: "failed",
                error: "Unable to parse message JSON: " + e.toString()
            ]
            sendReply(message, JsonOutput.prettyPrint(JsonOutput.toJson(replyValue)))
        }
    }

    private String getMessageReplyTo(Message message) {
        String replyToValue
        if(message.getJMSReplyTo())
            replyToValue = ((Queue)message.getJMSReplyTo()).queueName
        else
            replyToValue = message.getStringProperty('reply-to')?:message.getStringProperty('replyTo')
        return replyToValue
    }
    
    /**
     * 
     * Write a hook into the Bpipe directory for the pipeline so that it
     * sends an agent reply on shutdown.
     * <p>
     * This method of reply causes the reply message to be sent by the actual
     * Bpipe pipeline instance when the pipeline finishes. This ensures that
     * the reply is sent even if the instance is retried manually.
     */
    private void setupHookReply(Map commandAttributes, String replyToValue, RunPipelineCommand command, Message message) {
        command.onDirectoryConfigured = { File dir ->
            File hooksDir = new File(dir, ".bpipe/hooks")
            hooksDir.mkdirs()
            
            // The config may contain a closure which in some circumstances can be triggered
            // to execute by JSON serialisation
            // To avoid that, we need to sanitise the config first by removing entries with values that are Closures
            Map sanitisedConfig = config.grep { Map.Entry e ->  !(e.value instanceof Closure) }.collectEntries()
            
            Map configValue = [
                correlationID : message.JMSCorrelationID,
                * : sanitisedConfig,
                replyQueue: replyToValue,
                response: [
                    replyTo: message.JMSMessageID,
                    command: commandAttributes,
                    directory: dir.toString()
                ]
            ]

            File agentHook = new File(hooksDir, 'agent_hook.groovy')

            log.info("Writing hook for replyTo to $agentHook.absolutePath")

            agentHook.text =
            """
                import groovy.json.*
                bpipe.EventManager.getInstance().addListener(bpipe.PipelineEvent.SHUTDOWN) { type, desc, details ->
                    String configValue = '${JsonOutput.toJson(configValue)}'
                    Map config = new JsonSlurper().parseText(configValue)
                    bpipe.agent.JMSAgent.sendAgentReply(config)
                }
            """.stripIndent()
        }
    }

    /**
     * Set up a callback after the command completes to send the reply directly to the
     * reply queue directly within this agent.
     * <p>
     * This is a legacy method of reply, because it cannot guarantee that a reply 
     * will be sent eventually if the pipeline fails and is restarted.
     */
    private void setupDirectReply(Map commandAttributes, RunPipelineCommand command, Message message, AgentCommandRunner runner) {
        log.info "Using direct replies"
        runner.completionListener = { Map result ->
            log.info "Sending reply for command $commandAttributes.id"


            Map checkDetails = getCheckDetails(command.dir)
            Map<String,Object> resultDetails = (Map<String,Object>)[
                command: commandAttributes,
                result: result + checkDetails
            ]

            if(command instanceof RunPipelineCommand) {
                String runDir = ((RunPipelineCommand)command).runDirectory?.canonicalPath
                resultDetails['directory'] = runDir
            }
            sendReply(message, JsonOutput.prettyPrint(JsonOutput.toJson(resultDetails)))
        }
    }
    
    /**
     * Load the checks from the file system and format into appropriate object for return in reply
     */
    static Map getCheckDetails(String dir) {
        logger.info "Loading checks from $dir "
        def checks = bpipe.Check.loadAll(new File(dir, '.bpipe/checks'))
        return [ 
            checks:  checks.collect { [name: it.name, stage: it.stage, branch: it.branch, message: it.message, passed: it.passed] }
        ]        
    }
    
    void acknowledgeRun(Message msg) {
        if(acknowledgeMode == 'run') {
            log.info "Acknowledging message $msg.JMSMessageID at run start"
             msg.acknowledge()
        }
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
    
    /**
     * Designed to be called from within a Bpipe pipeline callback context such that 
     * a reply can be sent in response to an Agent invoked run with a replyTo header.
     * 
     * @param replyConfig   values determining JMS connection properties to use in 
     *                      sending the reply.
     */
    static void sendAgentReply(Map replyConfig) {
        
        Pipeline pipeline = Pipeline.rootPipeline
        Map checks = getCheckDetails(bpipe.Runner.runDirectory)
        Map suppDetails = [
            result:[
                command: ((Map)replyConfig.command)?.id,
                status: (pipeline == null || pipeline.failed) ? "failed" : "ok",
                directory: bpipe.Runner.runDirectory,
                *:checks
            ]
        ]
        
        logger.info("Supp details are: " + suppDetails)

        def responseJSON = ((Map)replyConfig.response) + suppDetails
        
        logger.info("response JSON to send upstream: " + responseJSON)

        String formattedResponse = new JsonBuilder(responseJSON).toPrettyString()

         // Determine if a "sent file" exists for this send
        String sentFilePath = 'agent.' + replyConfig.replyQueue + '.' + Utils.sha1((String)("${replyConfig.brokerURL}:$replyConfig.replyQueue\n$formattedResponse"))
        File sentFile = new File(Sender.SENT_FOLDER, sentFilePath)
        if(sentFile.exists()) {
            logger.info "Not sending agent reply because sent file $sentFile.absolutePath already exists"
            return
        }
        
        def connection 
        if(replyConfig.type == 'sqs') {
            connection = AWSSQSNotificationChannel.createSQSConnection(replyConfig)
        }
        else {
            connection = bpipe.notification.ActivemqNotificationChannel.createActiveMQConnection(replyConfig)
        }

        try {
            def session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE)
            
            logger.info("Sending agent reply message to $replyConfig.replyQueue (sent file $sentFile.absolutePath does not exist)")
            def dest = session.createQueue((String)replyConfig.replyQueue)
       
            TextMessage response = session.createTextMessage(formattedResponse)
            MessageProducer producer = session.createProducer(dest)
            if(replyConfig.containsKey('correlationID'))
                response.JMSCorrelationID = replyConfig.correlationID
            
            producer.send(response)
            logger.info "Sent response to $replyConfig.replyQueue"
            
            if(!Sender.SENT_FOLDER.exists())
                Sender.SENT_FOLDER.mkdirs()
                
            sentFile.text = formattedResponse
        }
        finally {
            connection.close()
        }
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
        long totalWaitedMs = 0
        final long permitCheckWaitMs = 2000
        while(concurrency.availablePermits()==0) {
            Thread.sleep(permitCheckWaitMs)
            totalWaitedMs+=permitCheckWaitMs
            if(n++ % 5 == 0) {
                log.info "Blocked waiting for $totalWaitedMs for current commands to complete ..."
            }
        }
    }
    
    void connect() {
        
        if(connection != null)
            return
            
           
        if(!(config.containsKey('commandQueue')))
            throw new PipelineError("ActiveMQ configuration is missing required key 'commandQueue'")
            
        log.info "commandQueue is set to $config.commandQueue"
           
        String type = config.getOrDefault('type', 'activemq')

        if(type == 'sqs') {
            this.connection = AWSSQSNotificationChannel.createSQSConnection(config)
        }
        else 
        if(type == 'activemq') {
            if(!(config.containsKey('brokerURL')))
                throw new PipelineError("ActiveMQ configuration is missing required key 'brokerURL'")
            log.info "Connecting to: ${config.brokerURL}"
            this.connection = ActivemqNotificationChannel.createActiveMQConnection(config)
        }

        this.connection.start()        
       
        this.session = connection.createSession(false,
             acknowledgeMode == 'read' ? Session.AUTO_ACKNOWLEDGE  : (type == 'activemq' ? ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE : Session.CLIENT_ACKNOWLEDGE))

        this.queue = session.createQueue((String)config.commandQueue)

        if (config.containsKey('messageSelector')) {
            log.info("Creating consumer for queue=${queue.queueName} with messageSelector=${config.messageSelector}")
            this.consumer = session.createConsumer(queue, (String)config.messageSelector)
        } else {
            log.info("Creating consumer for queue=${queue.queueName}")
            this.consumer = session.createConsumer(queue)
        }

        log.info "Connected to queue $config.commandQueue @ $config.brokerURL"
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
