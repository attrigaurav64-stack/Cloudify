package chat_app;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * Utility to clear old chat history and refresh the database table.
 */
public class ResetDatabase {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  Cloudify Database Refresh Utility");
        System.out.println("==================================================");

        Properties props = new Properties();
        try {
            try (InputStream is = new FileInputStream("db.properties")) {
                props.load(is);
            } catch (Exception e) {
                try (InputStream is = ResetDatabase.class.getResourceAsStream("/db.properties")) {
                    if (is != null) props.load(is);
                }
            }

            String url = props.getProperty("db.url", "jdbc:mysql://gateway01.ap-northeast-1.prod.aws.tidbcloud.com:4000/test?sslMode=VERIFY_IDENTITY");
            String user = props.getProperty("db.user", "37ZfgcHRHQJgXJk.root");
            String pass = props.getProperty("db.password", "6TlL6kPlTdDJoTve");

            System.out.println("Connecting to TiDB Cloud Database...");
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 Statement stmt = conn.createStatement()) {

                // Count existing messages
                int countBefore = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
                    if (rs.next()) countBefore = rs.getInt(1);
                }
                System.out.println("Found " + countBefore + " old messages in the database.");

                // Truncate or Delete messages
                System.out.println("Deleting old chat messages...");
                stmt.executeUpdate("DELETE FROM messages");
                try {
                    stmt.executeUpdate("ALTER TABLE messages AUTO_INCREMENT = 1");
                } catch (Exception ignored) {}

                // Insert a clean welcome message
                String welcomeSql = "INSERT INTO messages (sender, content, sent_at) VALUES ('Cloudify Bot', 'Welcome to Cloudify! Chat history has been refreshed.', NOW())";
                stmt.executeUpdate(welcomeSql);

                System.out.println("SUCCESS! Database refreshed. Ready for fresh chats.");
            }
        } catch (Exception e) {
            System.err.println("Error refreshing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
