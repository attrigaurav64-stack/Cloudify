package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MessageRequestDto {

    @NotBlank(message = "Sender name cannot be empty")
    @Size(max = 50, message = "Sender name cannot exceed 50 characters")
    private String sender;

    @NotBlank(message = "Message content cannot be empty")
    private String content;

    private String type = "CHAT";

    public MessageRequestDto() {
    }

    public MessageRequestDto(String sender, String content) {
        this.sender = sender;
        this.content = content;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
