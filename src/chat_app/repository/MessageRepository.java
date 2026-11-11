package chat_app.repository;

import chat_app.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class MessageRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ChatMessage> rowMapper = (rs, rowNum) -> {
        int id = rs.getInt("id");
        String sender = rs.getString("sender");
        String content = rs.getString("content");
        Timestamp sentAt = rs.getTimestamp("sent_at");
        return new ChatMessage(id, sender, content, sentAt);
    };

    @PostConstruct
    public void initTable() {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS messages ("
                       + "id INT AUTO_INCREMENT PRIMARY KEY, "
                       + "sender VARCHAR(50) NOT NULL, "
                       + "content TEXT NOT NULL, "
                       + "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                       + ")";
            jdbcTemplate.execute(sql);
            System.out.println("MessageRepository: Database verified / table 'messages' initialized.");
        } catch (Exception e) {
            System.err.println("MessageRepository: Table init note - " + e.getMessage());
        }
    }

    public List<ChatMessage> findRecentMessages(int limit) {
        if (limit <= 0) limit = 50;
        String sql = "SELECT id, sender, content, sent_at FROM messages ORDER BY id DESC LIMIT ?";
        try {
            List<ChatMessage> list = jdbcTemplate.query(sql, rowMapper, limit);
            Collections.reverse(list); // Chronological order (oldest to newest)
            return list;
        } catch (Exception e) {
            System.err.println("Error fetching recent messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<ChatMessage> findMessagesAfter(int lastId) {
        String sql = "SELECT id, sender, content, sent_at FROM messages WHERE id > ? ORDER BY id ASC";
        try {
            return jdbcTemplate.query(sql, rowMapper, lastId);
        } catch (Exception e) {
            System.err.println("Error polling new messages: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public ChatMessage save(ChatMessage msg) {
        String sql = "INSERT INTO messages (sender, content) VALUES (?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, msg.getSender());
                ps.setString(2, msg.getContent());
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            if (key != null) {
                msg.setId(key.intValue());
            }
            if (msg.getSentAt() == null) {
                msg.setSentAt(new Timestamp(System.currentTimeMillis()));
            }
            return msg;
        } catch (Exception e) {
            System.err.println("Error saving message: " + e.getMessage());
            throw new RuntimeException("Failed to persist message", e);
        }
    }

    public List<String> findRecentSenders() {
        String sql = "SELECT DISTINCT sender FROM messages ORDER BY id DESC LIMIT 15";
        try {
            return jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public int countTotalMessages() {
        String sql = "SELECT COUNT(*) FROM messages";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean testConnection() {
        try {
            Integer val = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return val != null && val == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
