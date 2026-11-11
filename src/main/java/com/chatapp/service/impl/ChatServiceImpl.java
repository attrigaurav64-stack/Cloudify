package com.chatapp.service.impl;

import com.chatapp.dto.ChatMessageDto;
import com.chatapp.dto.ChatStatsDto;
import com.chatapp.dto.MessageRequestDto;
import com.chatapp.entity.Message;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.MessageRepository;
import com.chatapp.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ChatServiceImpl(MessageRepository messageRepository, SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getRecentMessages(int limit) {
        List<Message> list = messageRepository.findTop50ByOrderByIdDesc();
        // Convert to chronological order (oldest to newest)
        Collections.reverse(list);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> pollNewMessages(int afterId) {
        List<Message> list = messageRepository.findByIdGreaterThanOrderByIdAsc(afterId);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatMessageDto sendMessage(MessageRequestDto request) {
        String sender = (request.getSender() == null || request.getSender().trim().isEmpty())
                ? "Anonymous" : request.getSender().trim();
        String content = request.getContent() != null ? request.getContent().trim() : "";

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be blank");
        }

        Message entity = new Message(sender, content);
        Message saved = messageRepository.save(entity);

        ChatMessageDto dto = toDto(saved);
        if (request.getType() != null) {
            dto.setType(request.getType());
        }

        // Real-time broadcast to all connected WebSocket clients
        try {
            messagingTemplate.convertAndSend("/topic/public", dto);
        } catch (Exception e) {
            // Log and allow HTTP response to succeed
        }

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageDto> searchMessages(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getRecentMessages(50);
        }
        return messageRepository.searchMessages(query.trim())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChatStatsDto getStats() {
        long total = messageRepository.count();
        List<String> senders = messageRepository.findRecentSenders();
        boolean healthy = isDatabaseHealthy();
        String timeStr = new SimpleDateFormat("hh:mm:ss a").format(new Date());

        return new ChatStatsDto(
                total,
                senders != null ? senders : new ArrayList<>(),
                healthy ? "Online" : "Connecting...",
                timeStr
        );
    }

    @Override
    @Transactional
    public void deleteMessage(Integer id) {
        if (!messageRepository.existsById(id)) {
            throw new ResourceNotFoundException("Message with ID " + id + " not found");
        }
        messageRepository.deleteById(id);
    }

    @Override
    @Transactional
    public ChatMessageDto updateMessage(Integer id, String newContent) {
        Message msg = messageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message with ID " + id + " not found"));
        msg.setContent(newContent);
        Message saved = messageRepository.save(msg);
        return toDto(saved);
    }

    @Override
    public boolean isDatabaseHealthy() {
        try {
            return messageRepository.count() >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private ChatMessageDto toDto(Message entity) {
        return new ChatMessageDto(
                entity.getId(),
                entity.getSender(),
                entity.getContent(),
                entity.getSentAt()
        );
    }
}
