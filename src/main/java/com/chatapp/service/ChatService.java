package com.chatapp.service;

import com.chatapp.dto.ChatMessageDto;
import com.chatapp.dto.ChatStatsDto;
import com.chatapp.dto.MessageRequestDto;

import java.util.List;

public interface ChatService {

    List<ChatMessageDto> getRecentMessages(int limit);

    List<ChatMessageDto> pollNewMessages(int afterId);

    ChatMessageDto sendMessage(MessageRequestDto request);

    List<ChatMessageDto> searchMessages(String query);

    ChatStatsDto getStats();

    void deleteMessage(Integer id);

    ChatMessageDto updateMessage(Integer id, String newContent);

    boolean isDatabaseHealthy();
}
