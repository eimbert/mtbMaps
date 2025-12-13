package com.paygoon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class PayGoonApplication {

	public static void main(String[] args) {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "admin123";
        String bcryptHash = encoder.encode(rawPassword);
        System.out.println("password:"+ bcryptHash);
		SpringApplication.run(PayGoonApplication.class, args);
	}

}
