# Cloudify &bull; Enterprise Spring Boot 3 Real-Time Chat Platform

> **Interface**: Cloudify Web (WhatsApp-Style Modern User Interface) & Terminal Console Edition  
> **Backend**: Spring Boot 3.3.5 (Spring MVC, Spring Data JPA, STOMP WebSocket, Bean Validation, Actuator)  
> **Live Web UI**: `http://localhost:8080`

A complete, enterprise-grade real-time chat platform featuring an ultra-clean, user-friendly **WhatsApp Web style interface** named **Cloudify**, coupled with a robust **Spring Boot 3 backend** and terminal clients. All interfaces communicate seamlessly through real-time push and cloud persistence.

---

## 🏛️ Enterprise Spring Boot 3 Backend Architecture

The backend adheres strictly to industry-standard layered architecture:

```text
src/main/java/com/chatapp/
│
├── ChatApplication.java                 # Spring Boot Main Entry Point
│
├── config/                              # Infrastructure & Protocol Configuration
│   ├── CorsConfig.java                  # Cross-Origin Resource Sharing (CORS)
│   └── WebSocketConfig.java             # STOMP / SockJS broker configuration
│
├── controller/                          # Presentation Layer (REST & WebSocket)
│   ├── MessageRestController.java       # Full REST API for message CRUD & stats
│   └── ChatWebSocketController.java     # Sub-millisecond real-time push broker
│
├── service/                             # Business Logic Layer
│   ├── ChatService.java                 # Service contract interface
│   └── impl/
│       └── ChatServiceImpl.java        # Message validation, DB operations, WebSocket push
│
├── repository/                          # Data Access Layer (Spring Data JPA)
│   └── MessageRepository.java           # JpaRepository queries, search & distinct senders
│
├── entity/                              # JPA Domain Model (Hibernate ORM)
│   └── Message.java                     # Maps to MySQL/TiDB `messages` table
│
├── dto/                                 # Data Transfer Objects & Validation
│   ├── ApiResponse.java                 # Standardized JSON response envelope
│   ├── ChatMessageDto.java              # Client-safe message transfer DTO
│   ├── ChatStatsDto.java                # Real-time room metrics & database health
│   └── MessageRequestDto.java           # Inbound message DTO with Jakarta Bean Validation
│
├── exception/                           # Error Handling Layer
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice for uniform JSON errors
│   └── ResourceNotFoundException.java   # Custom 404 domain exception
│
└── client/                              # Terminal Console Client
    └── TerminalClient.java              # Multithreaded JDBC terminal client
```

---

## 📡 REST API & WebSocket Documentation

### REST Endpoints (`/api`)

| Method | Endpoint | Description | Request Body | Response |
|---|---|---|---|---|
| `GET` | `/api/messages` | Fetch latest messages (default: 50) | None | `List<ChatMessageDto>` |
| `GET` | `/api/messages/poll?afterId={id}` | Incrementally poll messages after given ID | None | `List<ChatMessageDto>` |
| `POST` | `/api/messages` | Create and broadcast a new message | `{"sender":"...","content":"..."}` | `ChatMessageDto` (201 Created) |
| `GET` | `/api/messages/search?query={q}` | Keyword search in sender/content | None | `List<ChatMessageDto>` |
| `DELETE` | `/api/messages/{id}` | Delete a message by ID | None | `ApiResponse<Void>` |
| `GET` | `/api/stats` | Active users, total count, DB status | None | `ChatStatsDto` |
| `GET` | `/api/health` | Application heartbeat & database status | None | `Map<String, Object>` |
| `GET` | `/actuator/health` | Spring Boot Actuator health status | None | `HealthStatus` |

### WebSocket STOMP Protocol (`/ws-chat`)

- **STOMP Endpoint**: `/ws-chat` (with SockJS fallback enabled)
- **Broker Destination**: `/topic/public` (clients subscribe to receive live messages)
- **Application Prefix**: `/app`
  - Send message: send to `/app/chat.sendMessage`
  - User join event: send to `/app/chat.addUser`

---

## 🎨 Professional Web UI Features

- **Glassmorphic Dark Mode**: Modern slate & indigo theme inspired by Slack, Discord, and Linear.
- **Dynamic Avatars**: Unique initials and gradient colors hashed from sender names.
- **Live Search**: In-chat keyword filter for quick history lookups.
- **Audio Chime**: Web Audio API synth chimes on incoming messages (with mute switch).
- **Active Participants Panel**: Real-time listing of active chat members.
- **Dual Sync (WebSocket + Polling)**: Zero-lag WebSocket push + background HTTP polling fallback to ensure 100% synchronization with terminal clients.

---

## 🚀 How to Run

### 1. Spring Boot Backend & Web UI

#### Option A: One-Click Batch File
Double-click **`run-spring-boot.bat`** in the project folder.

#### Option B: Maven Command
```powershell
.\mvnw.cmd spring-boot:run
```
*(Or `mvn spring-boot:run` if Maven is installed)*

Open your browser at **[http://localhost:8080](http://localhost:8080)**.

---

### 2. Instant Zero-Maven Launcher (Pure JDK Fallback)
If Maven is not yet downloaded on your machine:
```powershell
run-ui.bat
```
*(Runs `StandaloneUiServer.java` on pure JDK with identical UI & REST API)*

---

### 3. Run Terminal Clients Alongside Web UI

Open separate terminal windows:

**Terminal 1:**
```powershell
java -cp "bin;lib/*" com.chatapp.client.TerminalClient User1
```

**Terminal 2:**
```powershell
java -cp "bin;lib/*" com.chatapp.client.TerminalClient User2
```

Messages sent in any terminal window will instantly appear in the Cloudify Web UI, and vice versa!
