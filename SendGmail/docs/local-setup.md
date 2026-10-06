# Local Email API — Setup and Workflow

## Current implementation

- POST /sendemail accepts one plain-text email request.
- Request fields are validated before sending.
- EmailService delegates sending to the EmailProvider interface.
- SmtpEmailProvider uses Spring JavaMailSender.
- Mailpit captures test emails locally.
- MySQL is installed but not yet integrated.

## Verified development environment

- Windows
- JDK 25.0.4
- Maven Wrapper 3.9.11
- Spring Boot 3.5.4
- Java compilation target: 17
- MySQL 8.0.46
- IntelliJ IDEA

Build and local sending were tested in this environment.
This does not establish compatibility for every dependency or workload.

## Start Mailpit

From the folder containing mailpit.exe:

```powershell
.\mailpit.exe --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025
```

Keep this terminal running.

Inbox: http://localhost:8025

## Build and start the application

From the SendGmail folder:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

## API request

POST http://localhost:8080/sendemail

Content-Type: application/json

```json
{
  "to": "puneet@example.test",
  "subject": "Local test",
  "message": "Hello Puneet!"
}
```

## Validation rules

- Recipient: required, valid email syntax, maximum 254 characters.
- Subject: required, maximum 200 characters.
- Message: required, maximum 10000 characters.
- Whitespace-only fields are rejected.
- Email syntax validation does not verify mailbox existence.

## Request workflow

1. Tomcat receives the HTTP request on port 8080.
2. Spring MVC deserializes JSON into EmailRequest.
3. Bean Validation checks the request.
4. Invalid fields produce HTTP 400 through ApiExceptionHandler.
5. Valid requests reach EmailController and EmailService.
6. SmtpEmailProvider creates a SimpleMailMessage.
7. JavaMailSender submits it to Mailpit on port 1025.
8. Received messages appear in the Mailpit UI on port 8025.

## Current responses

- HTTP 200: SMTP submission returned successfully.
- HTTP 400: Validation failure or missing/unreadable request body.
- HTTP 406: Legacy response when the service catches MailException.

HTTP 406 for SMTP failure is a known issue to correct.
SMTP acceptance does not guarantee recipient inbox delivery.
An exception can also represent an uncertain submission outcome.

## Verified manually

- A valid request produced HTTP 200 and appeared in Mailpit.
- Invalid fields produced HTTP 400 with field-specific errors.

## Current limitations

- Sending is synchronous.
- No campaign or recipient storage in MySQL.
- No durable queue, retries, rate limiting or idempotency.
- No authentication or delivery-event tracking.
- SMTP settings are for local testing.
- Mailpit inbox persistence is not configured.