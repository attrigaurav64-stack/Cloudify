-- ==============================================================
-- Chat Application Database Setup (MySQL / TiDB)
-- Course: 24CSH-301 Project Based Learning in Java
-- ==============================================================

CREATE DATABASE IF NOT EXISTS chatapp_db;
USE chatapp_db;

CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Test message (optional)
INSERT INTO messages (sender, content) VALUES ('System', 'Welcome to JDBC Chat!');
