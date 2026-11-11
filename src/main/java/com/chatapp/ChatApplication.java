package com.chatapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChatApplication {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  Starting Spring Boot 3 Enterprise Chat Application...");
        System.out.println("  REST Endpoints:    http://localhost:8080/api/messages");
        System.out.println("  WebSocket STOMP:   ws://localhost:8080/ws-chat");
        System.out.println("  Professional UI:   http://localhost:8080");
        System.out.println("==================================================================");

        SpringApplication.run(ChatApplication.class, args);
    }
}
