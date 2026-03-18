# CloudBeats Docker Deployment Guide

This guide provides comprehensive instructions for deploying CloudBeats using Docker with PostgreSQL and MinIO.

## Project Structure

```
CloudBeatsJava/
├── Dockerfile                          # Docker image build configuration
├── docker-compose.yml                  # Docker Compose orchestration
├── .dockerignore                       # Docker build exclusions
├── .env.docker                         # Environment variables template
├── DOCKER.md                           # Docker setup documentation
├── pom.xml                             # Maven configuration (includes minio dependency)
├── src/
│   └── main/
│       ├── java/com/cloudbeats/services/
│       │   └── MinIOFileManagementService.java    # MinIO file storage service
│       └── resources/
│           ├── application.yml                    # Default configuration
│           └── application-docker.yml             # Docker profile configuration
```

## Components

### 1. **PostgreSQL Database**
- Image: `postgres:16-alpine`
- Database: `cloudbeats`
- Port: `5432`
- Persistent volume: `postgres_data`

### 2. **MinIO Object Storage**
- Image: `minio/minio:latest`
- Console: `http://localhost:9001`
- API Port: `9000`
- Credentials: `minioadmin:minioadmin`
- Persistent volume: `minio_data`
- Auto-initializes `cloudbeats` bucket

### 3. **CloudBeats Application**
- Built from `Dockerfile` using multi-stage build
- Spring Boot application with Java 21
- Ports: `8080` (HTTP)
- Spring Profile: `docker`
- File storage: MinIO

## Installation & Setup

### Prerequisites

- Docker Desktop (version 20.10+)
- Docker Compose (version 1.29+)
- 4GB RAM minimum for containers
- 2GB disk space for data volumes

### Step 1: Clone or Navigate to Project

```bash
cd /path/to/CloudBeatsJava
```

### Step 2: Configure Environment Variables

```bash
# Copy the environment template
cp .env.docker .env

# Edit .env with your OAuth credentials
nano .env
```

Required environment variables:
```env
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_CLIENT_SECRET=your_google_oauth_secret
DROPBOX_CLIENT_ID=your_dropbox_oauth_client_id
DROPBOX_CLIENT_SECRET=your_dropbox_oauth_secret
ONEDRIVE_CLIENT_ID=your_onedrive_oauth_client_id
ONEDRIVE_CLIENT_SECRET=your_onedrive_oauth_secret
```

### Step 3: Build and Start Services

```bash
# Build the application and start all services
docker-compose up -d

# Verify all services are running
docker-compose ps

# Expected output:
# NAME                    COMMAND                  SERVICE      STATUS
# cloudbeats-app          "java -jar app.jar..."   app          Up (healthy)
# cloudbeats-minio        "minio server /data..."  minio        Up (healthy)
# cloudbeats-minio-init   "/bin/sh -c /usr/..."    minio-init   Exited (0)
# cloudbeats-postgres     "docker-entrypoint..."   postgres     Up (healthy)
```

### Step 4: Access Services

After all services are running and healthy:

- **Application**: http://localhost:8080
- **MinIO Console**: http://localhost:9001 (minioadmin:minioadmin)
- **PostgreSQL**: localhost:5432 (cloudbeats:cloudbeats_password)

### Step 5: Verify Application Health

```bash
# Check application logs
docker-compose logs -f app

# Check MinIO bucket was created
docker-compose exec minio mc ls minio/cloudbeats

# Expected: Object name starts with "cloudbeats"
```

## File Management with MinIO

### Automatic Bucket Creation

The `minio-init` service automatically:
1. Waits for MinIO to be healthy
2. Creates the `cloudbeats` bucket
3. Enables versioning on the bucket
4. Exits successfully

### Upload Flow

1. Files uploaded through CloudBeats API
2. MinIOFileManagementService writes to MinIO bucket
3. Object names stored in database
4. Presigned URLs generated for file access (7-day expiration)

### File Storage Location

- Bucket: `cloudbeats`
- Path pattern: `{provider}/{type}/{filename}`
- Example: `artworks/user123/album-cover.jpg`

## Common Operations

### View Logs

```bash
# Application logs
docker-compose logs -f app

# PostgreSQL logs
docker-compose logs -f postgres

# MinIO logs
docker-compose logs -f minio

# All services
docker-compose logs -f
```

### Stop Services

```bash
# Stop without removing containers/volumes
docker-compose stop

# Stop and remove containers (keep volumes)
docker-compose down

# Stop and remove everything including volumes
docker-compose down -v
```

### Restart Services

```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart app
```

### Rebuild Application

```bash
# Rebuild Docker image without cache
docker-compose build --no-cache

# Start services
docker-compose up -d
```

### Database Operations

```bash
# Access PostgreSQL CLI
docker-compose exec postgres psql -U cloudbeats -d cloudbeats

# Backup database
docker-compose exec postgres pg_dump -U cloudbeats cloudbeats > backup.sql

# Restore database
docker-compose exec -T postgres psql -U cloudbeats cloudbeats < backup.sql
```

### MinIO Operations

```bash
# List all buckets
docker-compose exec minio mc ls minio

# List files in cloudbeats bucket
docker-compose exec minio mc ls minio/cloudbeats

# Remove an object
docker-compose exec minio mc rm minio/cloudbeats/path/to/file

# Monitor bucket usage
docker-compose exec minio mc du minio/cloudbeats
```

## Configuration Files

### Dockerfile

- Multi-stage build: Maven builder → Runtime
- Base image: `eclipse-temurin:21-jre-jammy`
- Health checks: Included
- Profile: `docker` automatically activated

### docker-compose.yml

Services defined:
- `postgres`: PostgreSQL database
- `minio`: MinIO object storage
- `minio-init`: Bucket initialization
- `app`: CloudBeats application

Health check dependencies ensure proper startup order.

### application-docker.yml

Configuration specific to Docker deployment:
- PostgreSQL connection: `jdbc:postgresql://postgres:5432/cloudbeats`
- MinIO endpoint: `http://minio:9000`
- Credentials: Default MinIO credentials
- Logging: INFO level (optimized for containers)
- JPA: Auto-update enabled

### .env.docker

Template for environment variables. Copy to `.env` and customize with your credentials.

## Troubleshooting

### Application Won't Start

```bash
# Check application logs
docker-compose logs app

# Common issues:
# - PostgreSQL not ready: Wait for health check
# - MinIO bucket not created: Check minio-init logs
# - Port already in use: Change ports in docker-compose.yml
```

### Database Connection Error

```bash
# Verify PostgreSQL is healthy
docker-compose ps postgres

# Check database exists
docker-compose exec postgres psql -U cloudbeats -c "SELECT 1;"

# Restart PostgreSQL
docker-compose restart postgres
```

### MinIO Issues

```bash
# Check MinIO health
docker-compose ps minio

# Check bucket exists
docker-compose exec minio mc ls minio/

# Recreate bucket
docker-compose exec minio mc mb minio/cloudbeats --ignore-existing
```

### Port Conflicts

Edit `docker-compose.yml` and change port mappings:

```yaml
services:
  postgres:
    ports:
      - "5433:5432"  # Changed from 5432
  minio:
    ports:
      - "9000:9000"
      - "9010:9001"  # Changed from 9001
  app:
    ports:
      - "8081:8080"  # Changed from 8080
```

### Data Persistence

Volumes are automatically created and persist data:
- `postgres_data`: Database files
- `minio_data`: Object storage files

To reset everything:
```bash
docker-compose down -v
docker-compose up -d
```

## Performance Tuning

### Resource Limits

Add to `docker-compose.yml` services:
```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      cpus: '1'
      memory: 1G
```

### Database Optimization

Edit `application-docker.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20
          fetch_size: 50
```

## Security Considerations

### Production Deployments

1. **Change Default Credentials**
   - Update MinIO credentials in docker-compose.yml
   - Update PostgreSQL password
   - Update JWT secret in application-docker.yml

2. **Use Environment Secrets**
   - Store sensitive data in Docker secrets
   - Use `.env.local` (not committed to git)

3. **Network Security**
   - Services communicate via internal network
   - Only app exposes ports externally
   - Consider reverse proxy (nginx) for HTTPS

4. **Database Backups**
   - Schedule regular PostgreSQL backups
   - Test restore procedures

5. **MinIO Security**
   - Enable TLS for MinIO
   - Use strong access credentials
   - Enable versioning (already enabled)

## Scaling

### Multiple Application Instances

```yaml
app:
  deploy:
    replicas: 3
  environment:
    # ... configuration
```

Use a load balancer (nginx, HAProxy) to distribute requests.

### Monitoring

Add monitoring stack (optional):
- Prometheus for metrics
- Grafana for visualization
- ELK stack for logging

## Maintenance

### Regular Tasks

```bash
# Weekly: Check disk usage
docker-compose exec minio mc du minio/cloudbeats

# Monthly: Backup database
docker-compose exec postgres pg_dump -U cloudbeats cloudbeats > backup-$(date +%Y%m%d).sql

# As needed: Clean unused Docker resources
docker system prune -a
```

### Updates

```bash
# Pull latest images
docker-compose pull

# Rebuild application
docker-compose build --no-cache

# Restart services
docker-compose down
docker-compose up -d
```

## Support

For issues or questions:

1. Check logs: `docker-compose logs [service]`
2. Review DOCKER.md for quick reference
3. Check application health: http://localhost:8080/actuator/health
4. Verify service connectivity: `docker-compose ps`

## Next Steps

1. Deploy to production
2. Set up monitoring and alerting
3. Implement backup strategy
4. Configure SSL/TLS certificates
5. Set up CI/CD pipeline

