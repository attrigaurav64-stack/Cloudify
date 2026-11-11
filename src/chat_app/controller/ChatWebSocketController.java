package chat_app.controller;

import chat_app.model.ChatMessage;
import chat_app.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final MessageRepository messageRepository;

    @Autowired
    public ChatWebSocketController(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        if (chatMessage.getSender() == null || chatMessage.getSender().trim().isEmpty()) {
            chatMessage.setSender("Anonymous");
        }
        return messageRepository.save(chatMessage);
    }

    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessage addUser(@Payload ChatMessage chatMessage) {
        chatMessage.setType("JOIN");
        chatMessage.setContent(chatMessage.getSender() + " joined the chat");
        return chatMessage;
    }
}
