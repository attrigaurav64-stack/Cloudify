package com.chatapp.dto;

import java.util.ArrayList;
import java.util.List;

public class ChatStatsDto {

    private long totalMessages;
    private List<String> activeUsers;
    private String dbStatus;
    private String serverTime;

    public ChatStatsDto() {
        this.activeUsers = new ArrayList<>();
    }

    public ChatStatsDto(long totalMessages, List<String> activeUsers, String dbStatus, String serverTime) {
        this.totalMessages = totalMessages;
        this.activeUsers = activeUsers != null ? activeUsers : new ArrayList<>();
        this.dbStatus = dbStatus;
        this.serverTime = serverTime;
    }

    public long getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(long totalMessages) {
        this.totalMessages = totalMessages;
    }

    public List<String> getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(List<String> activeUsers) {
        this.activeUsers = activeUsers;
    }

    public String getDbStatus() {
        return dbStatus;
    }

    public void setDbStatus(String dbStatus) {
        this.dbStatus = dbStatus;
    }

    public String getServerTime() {
        return serverTime;
    }

    public void setServerTime(String serverTime) {
        this.serverTime = serverTime;
    }
}
