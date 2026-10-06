package com.email.bulk.provider;

public interface EmailProvider {
    void send(String to, String subject , String message);
}
