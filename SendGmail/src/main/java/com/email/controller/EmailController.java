package com.email.controller;

import com.email.Services.EmailService;
import com.email.model.EmailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;


@RestController
public class EmailController {

    @Autowired
    private EmailService emailService;
    @PostMapping("/sendemail")
    public ResponseEntity<?> sendEmail(@Valid @RequestBody EmailRequest request){
        boolean result = this.emailService.sendEmail(request.getSubject(), request.getMessage(), request.getTo());
        if(result){
            return ResponseEntity.ok("Email is Sent Successfully....!!");
        }
        else{
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body("Email is not sent..!!");
        }

    }
}
