package chat_app.controller;

import chat_app.model.ChatMessage;
import chat_app.model.ChatStats;
import chat_app.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MessageRestController {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public MessageRestController(MessageRepository messageRepository, SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessage>> getRecentMessages(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageRepository.findRecentMessages(limit));
    }

    @GetMapping("/messages/poll")
    public ResponseEntity<List<ChatMessage>> pollMessages(
            @RequestParam(defaultValue = "0") int afterId) {
        return ResponseEntity.ok(messageRepository.findMessagesAfter(afterId));
    }

    @PostMapping("/messages")
    public ResponseEntity<ChatMessage> sendMessage(@RequestBody ChatMessage request) {
        if (request.getSender() == null || request.getSender().trim().isEmpty()) {
            request.setSender("Anonymous");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ChatMessage saved = messageRepository.save(request);

        // Broadcast to all WebSocket listeners as well
        try {
            messagingTemplate.convertAndSend("/topic/public", saved);
        } catch (Exception e) {
            // Ignore if websocket broker is not active
        }

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/stats")
    public ResponseEntity<ChatStats> getStats() {
        int count = messageRepository.countTotalMessages();
        List<String> senders = messageRepository.findRecentSenders();
        boolean isConnected = messageRepository.testConnection();
        String timeStr = new SimpleDateFormat("hh:mm:ss a").format(new Date());

        ChatStats stats = new ChatStats(
                count,
                senders,
                isConnected ? "Online" : "Connecting...",
                timeStr
        );
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", "UP");
        map.put("database", messageRepository.testConnection() ? "CONNECTED" : "DISCONNECTED");
        map.put("service", "Spring Boot Chat UI");
        return ResponseEntity.ok(map);
    }
}
