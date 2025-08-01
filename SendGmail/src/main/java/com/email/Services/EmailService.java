package com.email.Services;

import org.springframework.stereotype.Service;

import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;


@Service
public class EmailService {
    public boolean sendEmail(String subject , String message , String to){
        //Rest of code...
        boolean flag = false;
        String from = "enter your sender's email";
        String host = "smtp.gmail.com";

		Properties properties = System.getProperties();

		System.out.println("Properties " + properties);

		//Setting important information to properties object

		//host set
		properties.put("mail.smtp.host" , host);
		properties.put("mail.smtp.port" , "465");
		properties.put("mail.smtp.ssl.enable" , "true");
		properties.put("mail.smtp.auth" , "true");

		//Step 1:to get the session object

		Session session = Session.getInstance(properties, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication("sender's Email" , "16-digit app password(for authentication)");
			}
		});
		session.setDebug(true);
		//Step 2 : Compose Message (text , Multi media part)
		MimeMessage m = new MimeMessage(session);
		try{
			//from email id
			m.setFrom(from);

			//Adding recipient to message
			m.addRecipient(Message.RecipientType.TO , new InternetAddress(to));

			//Adding subject  to message
			m.setSubject(subject);

			//Adding Text to message
			m.setText(message);

			//Send
			//Step 3 : Send the message using the transport class
			Transport.send(m);
            flag = true;
		}catch (Exception e){
			e.printStackTrace();
		}
        return flag;
    }
}
