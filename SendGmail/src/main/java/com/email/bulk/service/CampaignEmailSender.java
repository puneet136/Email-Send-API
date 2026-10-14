package com.email.bulk.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class CampaignEmailSender {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public CampaignEmailSender(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String fromAddress) {

        if (fromAddress == null || fromAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "Sender email address must not be blank"
            );
        }

        this.mailSender = mailSender;
        this.fromAddress = fromAddress.strip();
    }

    public void send(
            String recipientEmail,
            String subject,
            String body) {

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email must not be blank"
            );
        }

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException(
                    "Email subject must not be blank"
            );
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException(
                    "Email body must not be blank"
            );
        }

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}