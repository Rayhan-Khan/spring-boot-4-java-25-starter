#!/bin/bash
# Pulls the latest code and (re)deploys the app with Docker Compose.
# Usage: ./deploy.sh [branch]   (default branch: main)

set -u

# Variables
BRANCH="${1:-main}"
CONTAINER_NAME="base_project_container"
DOCKER_COMPOSE_FILES=(-f docker-compose.yml)

# Pull latest code
echo "Pulling latest code from branch '$BRANCH'..."
git pull origin "$BRANCH" || { echo "git pull failed."; exit 1; }

if [ ! -f .env ]; then
    echo "Missing .env. Copy .env.example and fill in the required values first."
    exit 1
fi

if grep -Eiq '^[[:space:]]*FIREBASE_ENABLED[[:space:]]*=[[:space:]]*true([[:space:]]*(#.*)?)?$' .env; then
    if [ ! -f firebase-service-account.json ]; then
        echo "Firebase is enabled, but firebase-service-account.json is missing."
        exit 1
    fi
    DOCKER_COMPOSE_FILES+=(-f docker-compose.firebase.yml)
fi

# Step 1: Stop the running container, if any
echo "Checking if container is already running..."
if [ -n "$(docker ps -q -f name=$CONTAINER_NAME)" ]; then
    echo "Container is running. Stopping and removing it..."
    docker compose "${DOCKER_COMPOSE_FILES[@]}" down
else
    echo "Container is not running."
fi

# Step 2: Build the Docker image
echo "Building Docker image..."
docker compose "${DOCKER_COMPOSE_FILES[@]}" build || { echo "Build failed."; exit 1; }

# Step 3: Start the container
echo "Starting application with Docker Compose..."
if docker compose "${DOCKER_COMPOSE_FILES[@]}" up -d; then
    echo "Deployment successful. Application is running."
else
    echo "Deployment failed. Check Docker logs for more details."
    exit 1
fi

# Step 4: Remove dangling images left over from previous builds.
# Only affects untagged images; other applications' containers, volumes and networks are untouched.
docker image prune -f

# Optional: View container logs
# docker logs -f $CONTAINER_NAME
