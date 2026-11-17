# Stream-Chat

A real-time group chat backend built with Spring Boot 3, WebSockets (STOMP), Spring Security (JWT), and PostgreSQL.

## Requirements

- Java 21
- Maven 3.9+
- Docker (for running PostgreSQL locally)

## Running PostgreSQL with Docker

```bash
docker run --name streamchat-db \
  -e POSTGRES_DB=streamchat \
  -e POSTGRES_USER=streamchat \
  -e POSTGRES_PASSWORD=streamchat \
  -p 5432:5432 \
  -d postgres:16
```

## Running the Application

```bash
mvn spring-boot:run
```

Or with custom environment variables:

```bash
DB_URL=jdbc:postgresql://localhost:5432/streamchat \
DB_USERNAME=streamchat \
DB_PASSWORD=streamchat \
JWT_SECRET=your-base64-encoded-secret \
mvn spring-boot:run
```

## Running Tests

```bash
mvn test
```

## Main Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v1/auth/register | Register a new user |
| POST | /api/v1/auth/login | Login and get JWT |
| POST | /api/v1/groups | Create a group |
| POST | /api/v1/groups/{groupId}/members | Add a member to a group |
| POST | /api/v1/groups/{groupId}/messages | Send a message |
| GET  | /api/v1/groups/{groupId}/messages | Get message history |

WebSocket endpoint: `ws://localhost:8080/ws` (STOMP over SockJS)

## Package Structure

```
com.streamchat.chat
├── application
│   ├── dto/           # Commands and request/response DTOs
│   ├── port/
│   │   ├── in/        # Use case interfaces
│   │   └── out/       # Repository and event publisher ports
│   └── usecase/       # Use case implementations
├── domain
│   ├── exception/     # Domain exceptions
│   ├── model/         # Domain entities (ChatGroup, ChatMessage, User)
│   └── valueobject/   # Value objects (UserId, GroupId, MessageId)
└── infrastructure
    ├── persistence/   # JPA repositories, adapters, Outbox
    ├── rest/          # REST controllers, GlobalExceptionHandler
    ├── security/      # JWT, Spring Security config
    └── websocket/     # WebSocket config, controllers, interceptors
```
