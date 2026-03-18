#!/bin/bash
set -e

# Wait for MinIO to be ready
echo "Waiting for MinIO to be ready..."
until curl -s http://minio:9000/minio/health/live > /dev/null; do
  sleep 1
done

echo "MinIO is ready!"

# Set up MinIO client alias
mc alias set minio http://minio:9000 minioadmin minioadmin

# Create the cloudbeats bucket if it doesn't exist
if mc ls minio/cloudbeats > /dev/null 2>&1; then
  echo "Bucket 'cloudbeats' already exists"
else
  echo "Creating bucket 'cloudbeats'..."
  mc mb minio/cloudbeats
  echo "Bucket 'cloudbeats' created successfully"
fi

# Set bucket versioning
mc version enable minio/cloudbeats

echo "MinIO initialization complete!"

