package chat_app.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * Standalone Embedded Web Server for Chat UI.
 * Runs instantly on any standard JDK using pure Java & JDBC (no Maven build required!).
 * Perfect for quick demos, offline grading, and testing alongside chat_app.Client.
 */
public class StandaloneUiServer {

    private static final int PORT = 8080;
    private static Connection conn;
    private static String dbUrl;
    private static String dbUser;
    private static String dbPass;

    public static void main(String[] args) throws Exception {
        loadConfig();
        initDb();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API endpoints
        server.createContext("/api/messages", new MessagesHandler());
        server.createContext("/api/messages/poll", new PollHandler());
        server.createContext("/api/stats", new StatsHandler());
        server.createContext("/api/health", new HealthHandler());

        // Static file handler for UI assets
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("=================================================");
        System.out.println("  Chat Application Professional Web UI");
        System.out.println("  Server is LIVE at: http://localhost:" + PORT);
        System.out.println("  Open your browser to chat in real-time!");
        System.out.println("  Press Ctrl+C to stop.");
        System.out.println("=================================================\n");
    }

    private static void loadConfig() {
        Properties props = new Properties();
        File[] candidates = new File[] {
            new File("db.properties"),
            new File("../db.properties"),
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
        dbUrl = props.getProperty("db.url", 
            props.getProperty("spring.datasource.url", "jdbc:mysql://gateway01.ap-northeast-1.prod.aws.tidbcloud.com:4000/test?sslMode=VERIFY_IDENTITY"));
        dbUser = props.getProperty("db.user", 
            props.getProperty("spring.datasource.username", "37ZfgcHRHQJgXJk.root"));
        dbPass = props.getProperty("db.password", 
            props.getProperty("spring.datasource.password", "6TlL6kPlTdDJoTve"));
    }

    private static synchronized Connection getConnection() throws SQLException {
        try {
            if (conn == null || conn.isClosed() || !conn.isValid(2)) {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {}
                conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
            }
        } catch (SQLException e) {
            // Re-throw or attempt fresh reconnection
            conn = null;
            throw e;
        }
        return conn;
    }

    private static void initDb() {
        try {
            Connection c = getConnection();
            String sql = "CREATE TABLE IF NOT EXISTS messages ("
                       + "id INT AUTO_INCREMENT PRIMARY KEY, "
                       + "sender VARCHAR(50) NOT NULL, "
                       + "content TEXT NOT NULL, "
                       + "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                       + ")";
            try (Statement s = c.createStatement()) {
                s.executeUpdate(sql);
            }
            System.out.println("Connected to Database: " + dbUrl);
        } catch (SQLException e) {
            System.err.println("Database Warning: " + e.getMessage());
        }
    }

    static class MessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            String method = exchange.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equalsIgnoreCase(method)) {
                try {
                    Connection c = getConnection();
                    String sql = "SELECT id, sender, content, sent_at FROM messages ORDER BY id DESC LIMIT 50";
                    List<Map<String, Object>> list = new ArrayList<>();
                    try (Statement stmt = c.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", rs.getInt("id"));
                            m.put("sender", rs.getString("sender"));
                            m.put("content", rs.getString("content"));
                            Timestamp ts = rs.getTimestamp("sent_at");
                            m.put("formattedTime", ts != null ? new SimpleDateFormat("hh:mm a").format(ts) : "Now");
                            list.add(m);
                        }
                    }
                    Collections.reverse(list);
                    sendJson(exchange, 200, toJson(list));
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body = readBody(exchange);
                    String sender = extractJsonField(body, "sender");
                    String content = extractJsonField(body, "content");
                    if (sender == null || sender.trim().isEmpty()) sender = "Anonymous";
                    if (content == null || content.trim().isEmpty()) {
                        sendJson(exchange, 400, "{\"error\":\"Content cannot be empty\"}");
                        return;
                    }

                    Connection c = getConnection();
                    String sql = "INSERT INTO messages (sender, content) VALUES (?, ?)";
                    int newId = 0;
                    try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, sender);
                        ps.setString(2, content);
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) newId = rs.getInt(1);
                        }
                    }

                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", newId);
                    resp.put("sender", sender);
                    resp.put("content", content);
                    resp.put("formattedTime", new SimpleDateFormat("hh:mm a").format(new Date()));
                    sendJson(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else if ("DELETE".equalsIgnoreCase(method)) {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String[] parts = path.split("/");
                    Integer targetId = null;
                    if (parts.length > 3 && !parts[3].isEmpty()) {
                        try { targetId = Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) {}
                    }
                    if (targetId == null) {
                        sendJson(exchange, 400, "{\"error\":\"Missing message id\"}");
                        return;
                    }
                    Connection c = getConnection();
                    String sql = "DELETE FROM messages WHERE id = ?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setInt(1, targetId);
                        ps.executeUpdate();
                    }
                    sendJson(exchange, 200, "{\"success\":true,\"message\":\"Deleted message\"}");
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String[] parts = path.split("/");
                    Integer targetId = null;
                    if (parts.length > 3 && !parts[3].isEmpty()) {
                        try { targetId = Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) {}
                    }
                    if (targetId == null) {
                        sendJson(exchange, 400, "{\"error\":\"Missing message id\"}");
                        return;
                    }
                    String body = readBody(exchange);
                    String content = extractJsonField(body, "content");
                    if (content == null || content.trim().isEmpty()) {
                        sendJson(exchange, 400, "{\"error\":\"Content cannot be empty\"}");
                        return;
                    }
                    Connection c = getConnection();
                    String sql = "UPDATE messages SET content = ? WHERE id = ?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        ps.setString(1, content);
                        ps.setInt(2, targetId);
                        ps.executeUpdate();
                    }
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", targetId);
                    resp.put("content", content);
                    sendJson(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class PollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            int afterId = 0;
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "afterId".equalsIgnoreCase(pair[0])) {
                        try {
                            afterId = Integer.parseInt(pair[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            try {
                Connection c = getConnection();
                String sql = "SELECT id, sender, content, sent_at FROM messages WHERE id > ? ORDER BY id ASC";
                List<Map<String, Object>> list = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, afterId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", rs.getInt("id"));
                            m.put("sender", rs.getString("sender"));
                            m.put("content", rs.getString("content"));
                            Timestamp ts = rs.getTimestamp("sent_at");
                            m.put("formattedTime", ts != null ? new SimpleDateFormat("hh:mm a").format(ts) : "Now");
                            list.add(m);
                        }
                    }
                }
                sendJson(exchange, 200, toJson(list));
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                Connection c = getConnection();
                int total = 0;
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM messages")) {
                    if (rs.next()) total = rs.getInt(1);
                }

                List<String> senders = new ArrayList<>();
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("SELECT DISTINCT sender FROM messages ORDER BY id DESC LIMIT 10")) {
                    while (rs.next()) {
                        senders.add(rs.getString(1));
                    }
                }

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalMessages", total);
                stats.put("activeUsers", senders);
                stats.put("dbStatus", "Online");
                stats.put("serverTime", new SimpleDateFormat("hh:mm:ss a").format(new Date()));
                sendJson(exchange, 200, toJson(stats));
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCors(exchange);
            sendJson(exchange, 200, "{\"status\":\"UP\",\"database\":\"CONNECTED\",\"server\":\"StandaloneUiServer\"}");
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File[] searchDirs = new File[] {
                new File("resources/static"),
                new File("src/main/resources/static"),
                new File("../resources/static")
            };

            File targetFile = null;
            for (File dir : searchDirs) {
                File f = new File(dir, path.startsWith("/") ? path.substring(1) : path);
                if (f.exists() && f.isFile()) {
                    targetFile = f;
                    break;
                }
            }

            if (targetFile == null) {
                // If not found, try fallback to index.html for SPA routing
                for (File dir : searchDirs) {
                    File f = new File(dir, "index.html");
                    if (f.exists() && f.isFile()) {
                        targetFile = f;
                        break;
                    }
                }
            }

            if (targetFile == null || !targetFile.exists()) {
                String notFound = "<h1>404 File Not Found</h1><p>Looked for: " + path + "</p>";
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
                return;
            }

            String contentType = "text/plain";
            String lower = targetFile.getName().toLowerCase();
            if (lower.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (lower.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (lower.endsWith(".js")) contentType = "application/javascript; charset=UTF-8";
            else if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".svg")) contentType = "image/svg+xml";
            else if (lower.endsWith(".ico")) contentType = "image/x-icon";
            else if (lower.endsWith(".json")) contentType = "application/json";

            byte[] bytes = Files.readAllBytes(targetFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String clHeader = exchange.getRequestHeaders().getFirst("Content-Length");
        if (clHeader != null) {
            try {
                int len = Integer.parseInt(clHeader.trim());
                byte[] data = new byte[len];
                int totalRead = 0;
                while (totalRead < len) {
                    int r = is.read(data, totalRead, len - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                return new String(data, 0, totalRead, StandardCharsets.UTF_8);
            } catch (NumberFormatException ignored) {}
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int read;
        while (is.available() > 0 && (read = is.read(buf)) != -1) {
            baos.write(buf, 0, read);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String pattern = "\"" + field + "\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return unescapeJson(m.group(1));
        }
        return null;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(l.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) {
                if (i++ > 0) sb.append(",");
                sb.append("\"").append(escapeJson(e.getKey())).append("\":").append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
