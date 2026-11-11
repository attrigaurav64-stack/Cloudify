-- ==============================================================
-- Cloudify Database Setup & Reset Script (MySQL / TiDB Cloud)
-- ==============================================================

-- Create table if not exists
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- How to clear old messages anytime:
-- TRUNCATE TABLE messages;
-- or
-- DELETE FROM messages;

-- Insert fresh welcome message:
INSERT INTO messages (sender, content, sent_at) 
VALUES ('Cloudify Bot', 'Welcome to Cloudify! Chat history has been refreshed.', NOW());
