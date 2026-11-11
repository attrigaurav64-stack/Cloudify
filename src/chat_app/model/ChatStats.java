package chat_app.model;

import java.util.ArrayList;
import java.util.List;

public class ChatStats {

    private int totalMessages;
    private List<String> activeUsers;
    private String dbStatus;
    private String serverTime;

    public ChatStats() {
        this.activeUsers = new ArrayList<>();
    }

    public ChatStats(int totalMessages, List<String> activeUsers, String dbStatus, String serverTime) {
        this.totalMessages = totalMessages;
        this.activeUsers = activeUsers != null ? activeUsers : new ArrayList<>();
        this.dbStatus = dbStatus;
        this.serverTime = serverTime;
    }

    public int getTotalMessages() {
        return totalMessages;
    }

    public void setTotalMessages(int totalMessages) {
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
