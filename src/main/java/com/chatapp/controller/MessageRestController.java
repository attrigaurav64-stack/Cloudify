package com.chatapp.controller;

import com.chatapp.dto.ApiResponse;
import com.chatapp.dto.ChatMessageDto;
import com.chatapp.dto.ChatStatsDto;
import com.chatapp.dto.MessageRequestDto;
import com.chatapp.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MessageRestController {

    private final ChatService chatService;

    @Autowired
    public MessageRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Fetch recent messages history (returns JSON array for direct UI rendering).
     */
    @GetMapping("/messages")
    public ResponseEntity<List<ChatMessageDto>> getRecentMessages(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(chatService.getRecentMessages(limit));
    }

    /**
     * Incremental poll endpoint for real-time synchronization.
     */
    @GetMapping("/messages/poll")
    public ResponseEntity<List<ChatMessageDto>> pollMessages(
            @RequestParam(defaultValue = "0") int afterId) {
        return ResponseEntity.ok(chatService.pollNewMessages(afterId));
    }

    /**
     * Send a new message.
     */
    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(
            @Valid @RequestBody MessageRequestDto request) {
        ChatMessageDto created = chatService.sendMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Keyword search across message content and sender.
     */
    @GetMapping("/messages/search")
    public ResponseEntity<List<ChatMessageDto>> searchMessages(
            @RequestParam String query) {
        return ResponseEntity.ok(chatService.searchMessages(query));
    }

    /**
     * Delete a message by ID.
     */
    @DeleteMapping("/messages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(@PathVariable Integer id) {
        chatService.deleteMessage(id);
        return ResponseEntity.ok(ApiResponse.ok("Message deleted successfully", null));
    }

    /**
     * Edit / update a message by ID.
     */
    @PutMapping("/messages/{id}")
    public ResponseEntity<ChatMessageDto> updateMessage(
            @PathVariable Integer id,
            @RequestBody Map<String, String> body) {
        String newContent = body.get("content");
        if (newContent == null || newContent.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ChatMessageDto updated = chatService.updateMessage(id, newContent.trim());
        return ResponseEntity.ok(updated);
    }

    /**
     * Live metrics and active members.
     */
    @GetMapping("/stats")
    public ResponseEntity<ChatStatsDto> getStats() {
        return ResponseEntity.ok(chatService.getStats());
    }

    /**
     * Backend heartbeat and database health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> map = new HashMap<>();
        boolean dbOk = chatService.isDatabaseHealthy();
        map.put("status", dbOk ? "UP" : "DEGRADED");
        map.put("database", dbOk ? "CONNECTED" : "UNREACHABLE");
        map.put("service", "Spring Boot 3 Chat Backend");
        map.put("framework", "Spring Boot 3.3.5");
        return ResponseEntity.ok(map);
    }
}
