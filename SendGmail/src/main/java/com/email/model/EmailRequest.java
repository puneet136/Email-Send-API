package com.email.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 10000, message = "Message must not exceed 10000 characters")
    private String message;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email format is invalid")
    @Size(max = 254, message = "Recipient email must not exceed 254 characters")
    private String to;


    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must not exceed 200 characters")
    private String subject;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public EmailRequest(String message, String to, String subject) {
        this.message = message;
        this.to = to;
        this.subject = subject;
    }

    @Override
    public String toString() {
        return "EmailRequest{" +
                "message='" + message + '\'' +
                ", to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                '}';
    }

    public EmailRequest() {
    }
}
