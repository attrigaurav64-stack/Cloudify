package com.chatapp.controller;

import com.chatapp.dto.ChatMessageDto;
import com.chatapp.dto.MessageRequestDto;
import com.chatapp.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;

    @Autowired
    public ChatWebSocketController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Handles live incoming WebSocket messages from clients.
     */
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessageDto sendMessage(@Payload MessageRequestDto messageRequest) {
        return chatService.sendMessage(messageRequest);
    }

    /**
     * Broadcasts when a user joins the chat room.
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessageDto addUser(@Payload MessageRequestDto messageRequest) {
        ChatMessageDto event = new ChatMessageDto();
        event.setType("JOIN");
        event.setSender(messageRequest.getSender());
        event.setContent(messageRequest.getSender() + " joined the chat");
        return event;
    }
}
