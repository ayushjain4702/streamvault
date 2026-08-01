# StreamVault

A microservices-based video streaming platform built with **Spring Boot**, **Apache Kafka**, **AWS S3**, **Redis**, and **FFmpeg HLS** encoding.

```
Add Movie → Upload Video → Encode (FFmpeg) → Stream (HLS)
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              streamvault-player.html (HLS.js)               │
└──────────────────────────┬──────────────────────────────────┘
                           │
     ┌─────────────────────┼─────────────────────┐
     ▼                     ▼                     ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│   Content   │   │    Video    │   │  Encoding   │   │  Streaming  │
│   Service   │   │   Service   │   │   Service   │   │   Service   │
│    :8081    │   │    :8082    │   │    :8083    │   │    :8084    │
└──────┬──────┘   └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
       │                 │                 │                 │
       ▼                 ▼                 ▼                 ▼
    MySQL              AWS S3            FFmpeg             Redis
                           ▲                 ▲
                           └──── Kafka ──────┘
                         video.uploaded
                         video.encoded
```

| Service | Port | Responsibility |
|---------|------|----------------|
| **Content Service** | 8081 | Movie catalog (CRUD, search, genre filter) |
| **Video Service** | 8082 | Raw video upload to S3, publishes Kafka events |
| **Encoding Service** | 8083 | FFmpeg HLS transcoding (1080p–360p) |
| **Streaming Service** | 8084 | Secure HLS playback via S3 proxy + Redis cache |

---

## Tech Stack

| Category | Technologies |
|----------|-------------|
| Backend | Java 17, Spring Boot 4.1, Spring Data JPA |
| Messaging | Apache Kafka |
| Storage | AWS S3, MySQL 8 |
| Cache | Redis |
| Encoding | FFmpeg (HLS adaptive bitrate) |
| Frontend | HTML, HLS.js |
| Infrastructure | Docker Compose |

---

## Prerequisites

- Java 17+
- Maven
- Docker & Docker Compose
- FFmpeg (included in project at `ffmpeg-8.1.2-essentials_build/`)
- AWS account with S3 bucket

---

## Quick Start

### 1. Set environment paths

Copy the example env file and add your AWS credentials:

```powershell
copy .env.example .env
# Edit .env with your AWS access key, secret key, and S3 bucket name
. .\setup-env.ps1
```

This sets `FFMPEG_PATH`, `TEMP_DIR`, and `STREAMING_BASE_URL` for your current session. Keep this terminal open when starting services.

### 2. Start infrastructure

```bash
docker-compose up -d
```

Starts MySQL, Kafka, Zookeeper, and Redis.

### 3. Start all services

Run each in a **separate terminal** (run `setup-env.ps1` in each, or set paths once system-wide):

```bash
cd content-service   && mvn spring-boot:run
cd video-service     && mvn spring-boot:run
cd encoding-service  && mvn spring-boot:run
cd streaming-service && mvn spring-boot:run
```

### 4. Add a movie

```bash
curl -X POST http://localhost:8081/api/v1/movies \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Inception\",\"description\":\"A mind-bending thriller\",\"genre\":\"SCIFI\",\"director\":\"Christopher Nolan\",\"releaseYear\":2010,\"rating\":8.8,\"durationMinutes\":148}"
```

### 5. Upload a video

```bash
curl -X POST http://localhost:8082/api/v1/videos/upload/{movieId} \
  -F "file=@/path/to/video.mp4"
```

### 6. Play video

Open `streamvault-player.html` in your browser, enter the `movieId`, and click **Play**.

Wait for encoding to finish (check encoding-service logs) before streaming.

---

## API Endpoints

### Content Service — `:8081`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/movies` | Add movie |
| GET | `/api/v1/movies` | List all movies |
| GET | `/api/v1/movies/{id}` | Get movie by ID |
| GET | `/api/v1/movies/genre/{genre}` | Filter by genre |
| GET | `/api/v1/movies/search?title=` | Search by title |

### Video Service — `:8082`

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/videos/upload/{movieId}` | Upload raw video |

### Streaming Service — `:8084`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/stream/{movieId}` | Get streaming URL |
| GET | `/api/v1/stream/{movieId}/playlist?path=` | HLS playlist proxy |
| GET | `/api/v1/stream/{movieId}/segment?path=` | Video segment proxy |

---

## Project Structure

```
STREAMVAULT/
├── docker-compose.yml
├── streamvault-player.html
├── PROJECT_DOCUMENTATION.md      # Detailed architecture & interview guide
├── content-service/              # Movie catalog (MySQL)
├── video-service/                # Video upload (S3 + Kafka)
├── encoding-service/             # FFmpeg HLS encoding
└── streaming-service/            # Secure playback (Redis + S3 proxy)
```

---

## Key Features

- **Event-driven architecture** — Kafka decouples upload, encoding, and streaming
- **Multi-quality HLS encoding** — 1080p, 720p, 480p, 360p via FFmpeg
- **Secure streaming** — Private S3 bucket with proxy layer (no direct S3 access)
- **Redis caching** — Playlist keys and streaming URLs cached for performance
- **Adaptive bitrate** — HLS.js auto-switches quality based on bandwidth

---

## Documentation

For a full deep-dive (architecture diagrams, Kafka flows, FFmpeg pipeline, interview Q&A), see **[PROJECT_DOCUMENTATION.md](PROJECT_DOCUMENTATION.md)**.

---

## Resume Highlight

> Built **StreamVault**, a microservices-based video streaming platform using Spring Boot, Apache Kafka, AWS S3, Redis, and FFmpeg HLS encoding with adaptive bitrate playback.

---

## License

This project is for educational and portfolio purposes.
