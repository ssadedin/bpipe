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

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import groovy.util.logging.Log;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Sends notifications using SMTP server
 * 
 * @author ssadedin
 */
@Log
class SMTPNotificationChannel implements NotificationChannel {
	
	String host
	
	Integer port
	
	boolean ssl = false
	
	boolean auth = false
	
	String username
	
	String from
	
	String password
	
	String recipients
    
    String format = "html"
    
	SMTPNotificationChannel(Map cfg) {
		host = cfg.host
		ssl = cfg.secure?:false
		if(!cfg.port) {
			port = ssl ? 465  :  -1
		}
		else 
			port = cfg.port
			
		username = cfg.username
		
		// Turn on authentication automatically if a password is provided
		if(cfg.password) {
			password = cfg.password
			auth = true
		}
		
		recipients = cfg.to
		from = cfg.from?:username
        
        if(cfg.containsKey("format"))
            format = cfg.format
	}

	protected SMTPNotificationChannel() {
	}
	
	@Override
	public void notify(PipelineEvent event, String subject, Template template, Map<String, Object> model) {

		String subjectLine = "Pipeline " + event.name().toLowerCase() + ": " + subject + " in directory " + (new File(".").absoluteFile.parentFile.name)
        
        if(model["send.contentType"] == 'application/json') {
            log.info "Not sending message [$subject] to SMTP notification channel because it has unsupported content-type"
            return
        }
        
        if(event == PipelineEvent.SEND) {
            // For SEND event, the text is extracted entirely from send.content, the template is already applied
            String content
            if(model["send.content"] instanceof String)
                content = model["send.content"]
            else
            if(model["send.file"])
                content = "See attached file"
            else
                content = "See subject"

            sendEmail(subjectLine, content, model["send.file"]?new File(model["send.file"]):null, model["send.contentType"])
        }
        else {
            String text = template.make(model).toString()
            if(event == PipelineEvent.REPORT_GENERATED) { // For a report event, attach the actual report
                sendEmail(subjectLine,text, new File(new File(model.reportListener.outputDir), model.reportListener.outputFileName))
            }
            else {
                sendEmail(subjectLine,text, null, model['send.contentType'])
            }
        }
	}
    
    public void sendEmail(String subjectLine, String text, File attachment = null, String contentType = null) {
        
       
        if(contentType == null)
            contentType = "text/plain"
        
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		if(port != -1) {
			props.put("mail.smtp.socketFactory.port", String.valueOf(port));
			props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		}
		
		if(port != -1)
			props.put("mail.smtp.port", String.valueOf(port));
			
		Session session 
		if(auth) {
    		props.put("mail.smtp.auth", "true");
			session = Session.getDefaultInstance(props,
				new javax.mail.Authenticator() {
					protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
						return new javax.mail.PasswordAuthentication(SMTPNotificationChannel.this.username,SMTPNotificationChannel.this.password);
					}
				});
		}
		else 
			session = Session.getDefaultInstance(props)
 
		Message message = new MimeMessage(session);
		message.setFrom(new InternetAddress(from));
		recipients.split(",").collect { it.trim() }.each { message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(it)) }
        
		message.setSubject(Utils.truncnl(subjectLine, 80));
        
        if(attachment) {
            Multipart multipart = new MimeMultipart();
            
            // Add the first part: the text content
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            if(contentType)
        		messageBodyPart.setContent(text, contentType + "; charset=utf-8")
            else
                messageBodyPart.setText(text)
                
            multipart.addBodyPart(messageBodyPart)
            
            // Add the second part: the attachment
            log.info "Adding attachment to notification email: $attachment.absolutePath"
            MimeBodyPart attachBodyPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachment);
            attachBodyPart.setDataHandler(new DataHandler(source));
            attachBodyPart.setFileName(attachment.name);
            multipart.addBodyPart(attachBodyPart);
            message.setContent(multipart)
        }
        else {
            if(contentType) {
        		message.setContent(text, contentType + "; charset=utf-8")
            }
            else
        		message.setText(text);
        }
        
        log.info "Sending email message to $recipients [subject=$subjectLine]"
		Transport.send(message);
    }
    
    String getDefaultTemplate(String contentType) {
        
        // Default to the format user has configured in config
        String templateFormat = this.format
        
        // Override if the pipeline itself is specifying a particular format
        if(contentType) {
           if(contentType == "text/html")
               templateFormat = "html"
           else
           if(contentType == "text/plain")
               templateFormat = "text"
        }
        
        if(templateFormat == "text")
            "email.template.txt"
        else
        if(templateFormat == "html")
            "email.template.html"
        else {
            log.warning("Email format $format is not recognised: please use 'html' or 'text'")
            "email.template.html"
        }
    }
}

@Log
class GMAILNotificationChannel extends SMTPNotificationChannel {
	GMAILNotificationChannel(Map cfg) {
		host = "smtp.gmail.com"
		ssl = true
		port = 465
		username = cfg.username
		password = cfg.password
		auth = true
		recipients = cfg.to
		from = cfg.from?:username
	}
	
}
