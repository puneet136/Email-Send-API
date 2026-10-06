package com.email.Services;

import com.email.bulk.provider.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	private static final Logger log =
			LoggerFactory.getLogger(EmailService.class);

	private final EmailProvider emailProvider;

	public EmailService(EmailProvider emailProvider) {
		this.emailProvider = emailProvider;
	}

	public boolean sendEmail(
			String subject,
			String message,
			String to) {

		try {
			emailProvider.send(to, subject, message);
			return true;
		} catch (MailException exception) {
			log.warn(
					"SMTP submission failed or could not be confirmed. Type: {}",
					exception.getClass().getSimpleName()
			);
			return false;
		}
	}
}