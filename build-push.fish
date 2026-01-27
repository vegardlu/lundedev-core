#!/usr/bin/env fish

# Fast local build and push to Docker Hub
# Usage: ./build-push.fish [tag]
# Example: ./build-push.fish        → pushes as :latest
# Example: ./build-push.fish v1.0.0 → pushes as :v1.0.0 and :latest

set DOCKER_IMAGE "vegardlunde/lundedev-core"
set TAG (if test (count $argv) -gt 0; echo $argv[1]; else; echo "latest"; end)

echo "🔨 Building JAR..."
./gradlew bootJar --quiet
if test $status -ne 0
    echo "❌ Gradle build failed"
    exit 1
end
echo "✅ JAR built"

echo "🐳 Building Docker image (linux/amd64 for Ubuntu Server)..."
docker build --platform linux/amd64 -t $DOCKER_IMAGE:$TAG .
if test $status -ne 0
    echo "❌ Docker build failed"
    exit 1
end
echo "✅ Docker image built: $DOCKER_IMAGE:$TAG"

echo "🚀 Pushing to Docker Hub..."
docker push $DOCKER_IMAGE:$TAG
if test $status -ne 0
    echo "❌ Docker push failed"
    exit 1
end

# Also tag and push as latest if a specific tag was provided
if test "$TAG" != "latest"
    docker tag $DOCKER_IMAGE:$TAG $DOCKER_IMAGE:latest
    docker push $DOCKER_IMAGE:latest
    echo "✅ Pushed: $DOCKER_IMAGE:$TAG and $DOCKER_IMAGE:latest"
else
    echo "✅ Pushed: $DOCKER_IMAGE:latest"
end

echo "🎉 Done!"
