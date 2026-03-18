# CloudBeats Docker Setup

This document describes how to run CloudBeats using Docker and Docker Compose.

## Prerequisites

- Docker (version 20.10 or higher)
- Docker Compose (version 1.29 or higher)

## Services

The Docker setup includes the following services:

1. **PostgreSQL** - Main application database
2. **MinIO** - S3-compatible object storage for file management
3. **CloudBeats App** - Spring Boot application

## Quick Start

### 1. Configure Environment Variables

Copy the example environment file and update with your OAuth credentials:

```bash
cp .env.docker .env
```

Edit `.env` and add your OAuth credentials:
- Google OAuth2 credentials
- Dropbox OAuth2 credentials
- OneDrive OAuth2 credentials

### 2. Build and Start Services

```bash
docker-compose up -d
```

This will:
- Build the CloudBeats Docker image
- Start PostgreSQL container
- Start MinIO container
- Start the CloudBeats application container

### 3. Access Services

- **Application**: http://localhost:8080
- **MinIO Console**: http://localhost:9001
  - Username: `minioadmin`
  - Password: `minioadmin`
- **PostgreSQL**: localhost:5432

## Docker Compose Configuration

### Environment Variables

The following environment variables are used:

```yaml
SPRING_PROFILES_ACTIVE: docker          # Activates the docker profile
GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
DROPBOX_CLIENT_ID: ${DROPBOX_CLIENT_ID}
DROPBOX_CLIENT_SECRET: ${DROPBOX_CLIENT_SECRET}
ONEDRIVE_CLIENT_ID: ${ONEDRIVE_CLIENT_ID}
ONEDRIVE_CLIENT_SECRET: ${ONEDRIVE_CLIENT_SECRET}
```

### Volumes

- **postgres_data**: Persists PostgreSQL database
- **minio_data**: Persists MinIO storage

### Networks

All services communicate through the `cloudbeats-network` bridge network.

## Application Configuration (Docker Profile)

The Docker profile uses `application-docker.yml` which includes:

- **Database**: PostgreSQL (postgres:5432)
- **MinIO Settings**:
  - Endpoint: `http://minio:9000`
  - Access Key: `minioadmin`
  - Secret Key: `minioadmin`
  - Bucket: `cloudbeats`
- **File Management**: Uses `MinIOFileManagementService`

## Common Commands

### Start services

```bash
docker-compose up -d
```

### Stop services

```bash
docker-compose down
```

### Stop and remove volumes

```bash
docker-compose down -v
```

### View logs

```bash
docker-compose logs -f app
docker-compose logs -f postgres
docker-compose logs -f minio
```

### Rebuild application image

```bash
docker-compose build --no-cache
docker-compose up -d
```

## Health Checks

All services include health checks:

- PostgreSQL: Checks if database is accepting connections
- MinIO: Checks MinIO health endpoint
- Application: Uses Spring Boot health endpoint

## Troubleshooting

### Application can't connect to PostgreSQL

Check if PostgreSQL is running and healthy:
```bash
docker-compose ps
docker-compose logs postgres
```

### MinIO bucket not created

The bucket is created automatically when MinIO starts. If issues occur:

```bash
docker-compose exec minio mc alias set minio http://localhost:9000 minioadmin minioadmin
docker-compose exec minio mc mb minio/cloudbeats
```

### Port conflicts

If ports 8080, 5432, or 9000 are already in use, modify `docker-compose.yml`:

```yaml
ports:
  - "8081:8080"  # Map to different port
```

## File Storage

When using the Docker profile, files are stored in MinIO bucket `cloudbeats`. Access URLs are generated with presigned tokens that expire based on the configured duration.

## Profiles

- **dev**: LocalFileManagementService (local filesystem)
- **docker**: MinIOFileManagementService (MinIO object storage)

To use a different profile, modify `SPRING_PROFILES_ACTIVE` in the environment or docker-compose.yml.

