package com.chatapp.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;

/**
 * Terminal-based JDBC Chat Client.
 * Demonstrates multithreading (listener daemon thread) and parameterized JDBC SQL.
 */
public class TerminalClient implements Runnable {

    private Connection conn;
    private final String name;
    private int lastMessageId = 0;
    private volatile boolean running = true;

    public TerminalClient(String name, String dbUrl, String dbUser, String dbPass) {
        this.name = name;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Connecting to database...");
            conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);

            createTableIfNotExists();
            initCurrentMessageId();

            System.out.println("=================================================");
            System.out.println("  Connected to Cloud Chat as [" + this.name + "]");
            System.out.println("  Interoperable with Spring Boot Web UI!");
            System.out.println("  Type your message and press ENTER to send.");
            System.out.println("  Type 'exit' to leave.");
            System.out.println("=================================================\n");

            Thread listenerThread = new Thread(this, "MessageListenerThread");
            listenerThread.setDaemon(true);
            listenerThread.start();

            startChatLoop();

        } catch (ClassNotFoundException e) {
            System.err.println("Error: MySQL JDBC Driver not found in classpath.");
        } catch (SQLException e) {
            System.err.println("Database Connection Failed: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS messages ("
                   + "id INT AUTO_INCREMENT PRIMARY KEY, "
                   + "sender VARCHAR(50) NOT NULL, "
                   + "content TEXT NOT NULL, "
                   + "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                   + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            System.err.println("Table note: " + e.getMessage());
        }
    }

    private void initCurrentMessageId() {
        String sql = "SELECT COALESCE(MAX(id), 0) AS max_id FROM messages";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                this.lastMessageId = rs.getInt("max_id");
            }
        } catch (SQLException e) {
            System.err.println("Could not check current max id: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(1000);
                if (conn == null || conn.isClosed()) {
                    continue;
                }

                String sql = "SELECT id, sender, content, sent_at FROM messages WHERE id > ? ORDER BY id ASC";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, lastMessageId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String sender = rs.getString("sender");
                            String content = rs.getString("content");
                            Timestamp sentAt = rs.getTimestamp("sent_at");

                            lastMessageId = Math.max(lastMessageId, id);

                            if (!sender.equalsIgnoreCase(this.name)) {
                                String timeStr = formatTime(sentAt);
                                System.out.println("\n[" + timeStr + "] " + sender + ": " + content);
                                System.out.print("> ");
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (SQLException e) {
                if (running) {
                    System.err.println("Error fetching messages: " + e.getMessage());
                }
            }
        }
    }

    private void startChatLoop() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                System.out.println("Leaving chat. Goodbye!");
                running = false;
                break;
            }

            if (!input.isEmpty()) {
                sendMessage(input);
            }
        }
    }

    private void sendMessage(String msg) {
        String sql = "INSERT INTO messages (sender, content) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, this.name);
            pstmt.setString(2, msg);
            pstmt.executeUpdate();

            String timeStr = new SimpleDateFormat("hh:mm a").format(new Date());

            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int myId = keys.getInt(1);
                    synchronized (this) {
                        lastMessageId = Math.max(lastMessageId, myId);
                    }
                }
            }

            System.out.println("[" + timeStr + "] [Sent] " + this.name + ": " + msg);

        } catch (SQLException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    private String formatTime(Timestamp ts) {
        if (ts == null) {
            return new SimpleDateFormat("hh:mm a").format(new Date());
        }
        return new SimpleDateFormat("hh:mm a").format(ts);
    }

    private void cleanup() {
        running = false;
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {}
        }
    }

    public static void main(String[] args) {
        String name = null;
        if (args.length >= 1 && !args[0].trim().isEmpty()) {
            name = args[0].trim();
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.print("Enter your name to join chat: ");
            if (sc.hasNextLine()) {
                name = sc.nextLine().trim();
            }
            if (name == null || name.isEmpty()) {
                System.out.println("No name entered. Exiting.");
                return;
            }
        }

        Properties props = new Properties();
        File[] candidates = new File[] {
            new File("db.properties"),
            new File("../db.properties"),
            new File("src/main/resources/application.properties"),
            new File("resources/application.properties")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    props.load(fis);
                    break;
                } catch (IOException ignored) {}
            }
        }

        String dbUrl = args.length >= 2 ? args[1] : props.getProperty("db.url", 
            props.getProperty("spring.datasource.url", "jdbc:mysql://gateway01.ap-northeast-1.prod.aws.tidbcloud.com:4000/test?sslMode=VERIFY_IDENTITY"));
        String dbUser = args.length >= 3 ? args[2] : props.getProperty("db.user", 
            props.getProperty("spring.datasource.username", "37ZfgcHRHQJgXJk.root"));
        String dbPass = args.length >= 4 ? args[3] : props.getProperty("db.password", 
            props.getProperty("spring.datasource.password", "6TlL6kPlTdDJoTve"));

        new TerminalClient(name, dbUrl, dbUser, dbPass);
    }
}
