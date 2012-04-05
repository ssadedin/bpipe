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

import java.util.Map

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage

/**
 * Sends notifications using SMTP server
 * 
 * @author ssadedin
 */
class SMTPNotificationChannel implements NotificationChannel {
	
	String host
	
	Integer port
	
	boolean ssl = false
	
	boolean auth = false
	
	String username
	
	String from
	
	String password
	
	String recipients
	
	SMTPNotificationChannel(ConfigObject cfg) {
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
	}

	@Override
	public void notify(PipelineEvent event, String subject, Map<String, Object> model) {
		Properties props = new Properties();
		props.put("mail.smtp.host", host);
		if(port != -1) {
			props.put("mail.smtp.socketFactory.port", String.valueOf(port));
			props.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		}
		props.put("mail.smtp.auth", "true");
		
		if(port != -1)
			props.put("mail.smtp.port", String.valueOf(port));
			
		Session session 
		if(auth) {
			session = Session.getDefaultInstance(props,
				new javax.mail.Authenticator() {
					protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
						return new javax.mail.PasswordAuthentication(username,password);
					}
				});
		}
		else 
			session = Session.getDefaultInstance(props)
 
		String subjectLine = "Pipeline " + event.name().toLowerCase() + ": " + subject + " in directory " + (new File(".").absoluteFile.name)
		Message message = new MimeMessage(session);
		message.setFrom(new InternetAddress(from));

		recipients.split(",").collect { it.trim() }.each { message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(it)) }
		message.setSubject(subjectLine);
		message.setText("Pipeline finished at " + (new Date()) + "\n\nFull path: " + (new File(".").absolutePath));
		Transport.send(message);

		System.out.println("Done");
	}
}
