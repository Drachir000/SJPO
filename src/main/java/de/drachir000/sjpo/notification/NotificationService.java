package de.drachir000.sjpo.notification;

import de.drachir000.sjpo.config.InstanceConfig;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Service for sending notifications via SMTP and ntfy
 */
public class NotificationService {
	
	private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
	
	/**
	 * Send notification using configured methods
	 */
	public void sendNotification(InstanceConfig.NotificationConfig config,
	                             String subject, String message) {
		
		if (config == null) {
			return;
		}
		
		// Send SMTP notification
		if (config.getSmtp() != null && config.getSmtp().isEnabled()) {
			sendSmtpNotification(config.getSmtp(), subject, message);
		}
		
		// Send ntfy notification
		if (config.getNtfy() != null && config.getNtfy().isEnabled()) {
			sendNtfyNotification(config.getNtfy(), subject, message);
		}
		
	}
	
	/**
	 * Send email notification via SMTP
	 */
	private void sendSmtpNotification(InstanceConfig.SmtpConfig smtp,
	                                  String subject, String message) {
		
		try {
			Properties props = new Properties();
			props.put("mail.smtp.host", smtp.getHost());
			props.put("mail.smtp.port", String.valueOf(smtp.getPort()));
			props.put("mail.smtp.auth", "true");
			
			if (smtp.isUseTls()) {
				props.put("mail.smtp.starttls.enable", "true");
			}
			
			Session session = Session.getInstance(props, new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(smtp.getUsername(), smtp.getPassword());
				}
			});
			
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(smtp.getFrom()));
			
			for (String recipient : smtp.getTo()) {
				msg.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
			}
			
			msg.setSubject(subject);
			msg.setText(message);
			
			Transport.send(msg);
			logger.info("SMTP notification sent: {}", subject);
			
		} catch (MessagingException e) {
			logger.error("Failed to send SMTP notification: {}", e.getMessage());
		}
		
	}
	
	/**
	 * Send notification via ntfy
	 */
	private void sendNtfyNotification(InstanceConfig.NtfyConfig ntfy,
	                                  String title, String message) {
		
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			
			String url = ntfy.getServer() + "/" + ntfy.getTopic();
			HttpPost post = new HttpPost(url);
			
			// Set headers
			post.setHeader("Title", title);
			post.setHeader("Priority", ntfy.getPriority());
			
			if (ntfy.getToken() != null && !ntfy.getToken().isBlank()) {
				post.setHeader("Authorization", "Bearer " + ntfy.getToken());
			}
			
			// Set message body
			post.setEntity(new StringEntity(message));
			
			client.execute(post, response -> {
				
				int statusCode = response.getCode();
				
				if (statusCode >= 200 && statusCode < 300) {
					logger.info("Ntfy notification sent: {}", title);
				} else {
					logger.error("Ntfy notification failed with status: {}", statusCode);
				}
				
				return null;
				
			});
			
		} catch (Exception e) {
			logger.error("Failed to send ntfy notification: {}", e.getMessage());
		}
		
	}
	
}