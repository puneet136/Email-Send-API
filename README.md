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
### Sample Response
HTTP 200 OK
Email sent successfully!

###⚙️ Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-email@gmail.com
spring.mail.password=your-email-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

###⚠️ Important: For Gmail users, enable "Less secure apps" or use an App Password if 2FA is enabled.

###🧪 Testing with Postman
You can test this API by sending a POST request to http://localhost:8080/sendEmail with the sample JSON in the body.


###🤝 Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change or improve.

###👨‍💻 Author
Puneet Agrawal – Aspiring Java Developer & Backend Engineer


---

Would you like me to generate a logo or badge for this repo as well?
