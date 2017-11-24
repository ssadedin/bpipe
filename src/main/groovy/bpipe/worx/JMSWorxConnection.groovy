package bpipe.worx

import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage

class JMSWorxConnection extends WorxConnection {
    
    Queue queue
    
    Session session
    
    MessageProducer producer
    
    Map lastResponse

    public JMSWorxConnection(Queue queue, Session session) {
        super();
        this.queue = queue;
        this.session = session;
        this.producer = session.createProducer(queue)
    }  
    
    @Override
    public void sendJson(String path, String json) {
        TextMessage message = session.createTextMessage()
        message.text = json
        message.JMSType = path
        try {
            this.producer.send(message)
            this.lastResponse = [status:"ok"]
        }
        catch(Exception e) {
            this.lastResponse = [
                status:"error",
                message: e.message
            ]
            throw e
        }
    }
    
    Object readResponse() {
        return lastResponse
    }
    
    
    @Override
    public void close() {
        // we don't close the session because we assume it is managed by the higher 
        // level code.
        this.producer.close()
    }
}
