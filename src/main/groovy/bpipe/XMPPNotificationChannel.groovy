/*
 * Copyright (c) 2012 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package bpipe

import groovy.text.Template;
import groovy.util.logging.Log;
import java.util.regex.Pattern.LastNode;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;

/**
 * Sends notifications by using XMPP 
 * 
 * @author ssadedin
 */
@Log
class XMPPNotificationChannel implements NotificationChannel {
	
	/**
	 * Configuration
	 */
	ConnectionConfiguration connConfig = null;
	
	/**
	 * Who will receive the notifications
	 */
	String recipients = null
	
	String username
	
	String password
	
	long interval = 0
	
	static long lastNotificationTimeMs = -1L
	
	/**
	 * Create an XMPP notification channel using the provided configuration
	 * @param cfg
	 */
	XMPPNotificationChannel(Map cfg) {
		// Examples for Google talk
		//
		//	host = "talk.google.com"
		//	port = "5222"
		//	service = "gmail.com"
		String host = cfg.host
		String port = cfg.port
		String service = cfg.service
		connConfig = new ConnectionConfiguration(host, Integer.parseInt(port), service);
		recipients = cfg.to
		username = cfg.username
		password = cfg.password
		interval = cfg.interval?:0
		
		// Note: if you add stuff here, also add it in GTalkNotificationChannel construtor below
	}
	
	/**
	 * Used by child class
	 */
	protected XMPPNotificationChannel() {
	}

	@Override
	public void notify(PipelineEvent event, String subject, Template template,  Map<String, Object> model) {
		
		synchronized(XMPPNotificationChannel.class) {
		
			XMPPConnection connection = new XMPPConnection(connConfig);
            try {
    			connection.connect();
    			connection.login(username, password);
            }
            catch(Exception e) {
                log.severe "Failed to connect / login to XMPP service: $e"
                System.err.println "WARNING: Failed to send XMPP notification due to connect or login error: $e"
                if(connection.isConnected())
                    connection.disconnect()
                return
            }
			
			log.info("Logged in as " + connection.getUser());
	
			Presence presence = new Presence(Presence.Type.available);
			connection.sendPacket(presence);
			
			ChatManager chatmanager = connection.getChatManager();
			boolean failed = false
			
			String eventDescr = Utils.upperCaseWords(event.name().toLowerCase().replaceAll("_"," "))
            
            model.description = eventDescr
            model.subject = subject
			
            String content
            if(event == PipelineEvent.SEND)
                content = subject
            else
//    			content = eventDescr + ": " + subject + " (" + (new File(".").absoluteFile.parentFile.name) + ")"
                content = template.make(model).toString()
                
			recipients.split(",").each {
				try {
					Chat chat = chatmanager.createChat(it, null);
					Message msg = new Message(it, Message.Type.chat);
					msg.setBody(content);
					chat.sendMessage(msg);
				}
				catch(Exception e) {
					log.warning("Failed to notify user $it of result (message: $content): " + e)
					failed = true
				}
			}
			connection.disconnect()
		}
	}
    
    String getDefaultTemplate(String contentType) {
        "xmpp.template.txt"
    }
}

/**
 * XMPP with defaults for google chat built in
 * 
 * @author ssadedin
 */
@Log
class GTALKNotificationChannel extends XMPPNotificationChannel {
	GTALKNotificationChannel(Map cfg) {
		connConfig = new ConnectionConfiguration("talk.google.com", 5222, "gmail.com");
		recipients = cfg.to
		username = cfg.username
		password = cfg.password
		interval = cfg.interval?:0
	}	
}
