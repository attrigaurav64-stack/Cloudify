# Commands Cheat Sheet - Cloudify (Spring Boot 3 Backend & Web UI)

---

## 🚀 1) Run the Cloudify Web App & Spring Boot Backend

### Option A: Maven Command / Wrapper (Standard Spring Boot)
From the project root:
```powershell
.\mvnw.cmd spring-boot:run
```
*(Or if you have Maven installed: `mvn spring-boot:run`)*  
*(Or double-click `run-spring-boot.bat`)*

- **Cloudify Web UI**: [http://localhost:8080](http://localhost:8080)
- **REST API Base**: [http://localhost:8080/api/messages](http://localhost:8080/api/messages)
- **Health Check**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
- **WebSocket STOMP**: `ws://localhost:8080/ws-chat`

---

### Option B: Zero-Maven Instant Launcher (Pure JDK + JDBC)
If Maven is not yet downloaded on your system:
```powershell
# Compile
javac -cp "lib/*" -d bin src/chat_app/web/StandaloneUiServer.java src/chat_app/Client.java src/main/java/com/chatapp/client/TerminalClient.java

# Run
java -cp "bin;lib/*" chat_app.web.StandaloneUiServer
```
*(Or simply double-click `run-ui.bat`)*

---

## 💻 2) Run Terminal JDBC Clients (Simultaneous with Cloudify Web)

Open separate terminal windows to chat alongside the Web UI:

### Terminal 1:
```powershell
java -cp "bin;lib/*" com.chatapp.client.TerminalClient User1
```
*(Or legacy: `java -cp "bin;lib/*" chat_app.Client User1`)*

### Terminal 2:
```powershell
java -cp "bin;lib/*" com.chatapp.client.TerminalClient User2
```

---

## 📡 3) Backend REST API Endpoints Quick Reference

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/messages` | Get recent 50 messages |
| `GET` | `/api/messages/poll?afterId={id}` | Incrementally poll new messages |
| `POST` | `/api/messages` | Send message `{"sender":"...","content":"..."}` |
| `GET` | `/api/messages/search?query={q}` | Keyword search in messages |
| `DELETE`| `/api/messages/{id}` | Delete a message by ID |
| `GET` | `/api/stats` | Active members & total message metrics |
| `GET` | `/api/health` | System heartbeat & database status |
| `GET` | `/actuator/health` | Spring Boot production health indicator |
