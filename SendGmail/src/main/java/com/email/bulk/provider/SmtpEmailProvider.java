package com.email.bulk.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailProvider(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String fromAddress) {

        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void send(String to, String subject, String message) {
        SimpleMailMessage email = new SimpleMailMessage();

        email.setFrom(fromAddress);
        email.setTo(to);
        email.setSubject(subject);
        email.setText(message);

        mailSender.send(email);
    }
}