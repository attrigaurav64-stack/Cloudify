package chat_app.model;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatMessage {

    private Integer id;
    private String sender;
    private String content;
    private Timestamp sentAt;
    private String formattedTime;
    private String type; // "CHAT", "JOIN", "LEAVE"

    public ChatMessage() {
        this.type = "CHAT";
    }

    public ChatMessage(String sender, String content) {
        this.sender = sender;
        this.content = content;
        this.type = "CHAT";
        this.sentAt = new Timestamp(System.currentTimeMillis());
        this.formattedTime = formatTimestamp(this.sentAt);
    }

    public ChatMessage(Integer id, String sender, String content, Timestamp sentAt) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.sentAt = sentAt;
        this.formattedTime = formatTimestamp(sentAt);
        this.type = "CHAT";
    }

    public static String formatTimestamp(Timestamp ts) {
        if (ts == null) {
            return new SimpleDateFormat("hh:mm a").format(new Date());
        }
        return new SimpleDateFormat("hh:mm a").format(ts);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
        this.formattedTime = formatTimestamp(sentAt);
    }

    public String getFormattedTime() {
        if (formattedTime == null && sentAt != null) {
            formattedTime = formatTimestamp(sentAt);
        }
        return formattedTime;
    }

    public void setFormattedTime(String formattedTime) {
        this.formattedTime = formattedTime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "id=" + id +
                ", sender='" + sender + '\'' +
                ", content='" + content + '\'' +
                ", formattedTime='" + formattedTime + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
