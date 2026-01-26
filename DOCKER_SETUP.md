# Docker Hub Setup for GitHub Actions

## Prerequisites

- A [Docker Hub](https://hub.docker.com/) account
- A GitHub repository with this project

## Step 1: Create Docker Hub Access Token

1. Go to [Docker Hub](https://hub.docker.com/) and sign in
2. Click your username (top right) → **Account Settings**
3. Go to **Security** → **Access Tokens**
4. Click **New Access Token**
5. Enter a description (e.g., `GitHub Actions - lundedev-core`)
6. Select **Read, Write, Delete** permissions
7. Click **Generate**
8. **Copy the token** (you won't see it again!)

## Step 2: Add Secrets to GitHub Repository

1. Go to your GitHub repository
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret** and add:

| Secret Name | Value |
|-------------|-------|
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_TOKEN` | The access token from Step 1 |

## Step 3: Push to Trigger the Build

The workflow triggers on:
- Push to `main` branch
- Tags starting with `v` (e.g., `v1.0.0`)
- Pull requests to `main` (builds but doesn't push)

### Automatic Tagging

| Trigger | Docker Tags |
|---------|-------------|
| Push to `main` | `latest`, `main` |
| Tag `v1.2.3` | `1.2.3`, `1.2` |
| Pull request | Build only (no push) |

## Step 4: Run the Docker Image

```bash
docker run -d \
  -p 8080:8080 \
  -e GOOGLE_CLIENT_ID=your-client-id \
  -e GOOGLE_CLIENT_SECRET=your-client-secret \
  yourusername/lundedev-core:latest
```

Then open http://localhost:8080/swagger-ui.html

## About the Docker Image

Uses [Google's Distroless](https://github.com/GoogleContainerTools/distroless) base image:
- **Minimal** - No shell, package manager, or other OS utilities
- **Secure** - Reduced attack surface
- **Small** - Only contains the JRE and your app

## Multi-Architecture Support

The workflow builds for both:
- `linux/amd64` (Intel/AMD)
- `linux/arm64` (Apple Silicon, AWS Graviton)

## Useful Commands

```bash
# Pull the latest image
docker pull yourusername/lundedev-core:latest

# Check health
curl http://localhost:8080/actuator/health

# View logs
docker logs <container-id>

# Stop container
docker stop <container-id>
```
