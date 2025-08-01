# 📧 Email Send API

A lightweight and easy-to-use RESTful API built with **Spring Boot** that allows you to send emails by providing the recipient address, subject, and message in a simple JSON format.

## 🚀 Features

- Send plain text emails using a POST request
- Built using Spring Boot
- Minimal and clean structure
- Easy to integrate into other Java projects or microservices

## 🛠️ Technologies Used

- Java 17+
- Spring Boot
- Spring Web
- JavaMailSender (Spring Boot Starter Mail)

## 📦 Project Structure

Email-Send-API/
├── src/
│ ├── main/
│ │ ├── java/com/email/
│ │ │ ├── controller/
│ │ │ │ └── EmailController.java
│ │ │ ├── model/
│ │ │ │ └── EmailRequest.java
│ │ │ └── service/
│ │ │ └── EmailService.java
│ │ └── resources/
│ │ └── application.properties
├── pom.xml
└── README.md


## 📨 API Usage

### Endpoint


### Request Body (JSON)

```json
{
  "to": "recipient@example.com",
  "subject": "Test Subject",
  "message": "Hello, this is a test message."
}

