#!/bin/bash

# Docker Setup Script for Vert.x Clustered Applications
# vertx-multi-spring-boot-clustered/docker-setup.sh

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🐋 Vert.x Clustered Applications - Docker Setup${NC}"
echo "=================================================="

# Function to print section headers
print_section() {
    echo -e "\n${YELLOW}$1${NC}"
    echo "----------------------------------------"
}

# Check prerequisites
print_section "Checking Prerequisites"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker and Docker Compose are available${NC}"

# Clean up any existing containers/networks
print_section "Cleaning Up Previous Deployments"
echo "Stopping and removing any existing containers..."
docker-compose down 2>/dev/null || echo "No existing containers to remove"

# Check for network conflicts
echo "Checking for network conflicts..."
if docker network ls | grep -q "172\.25\.0\.0"; then
    echo "Found existing network on 172.25.0.0 subnet, removing..."
    docker network prune -f
fi

echo -e "${GREEN}✅ Cleanup completed${NC}"

# Build JAR files
print_section "Building JAR Files"

echo "Building consumer application..."

if [ -f "./consumer-app/gradlew" ]; then
    (cd consumer-app && ./gradlew clean build --no-daemon)
else
    (cd consumer-app && gradle clean build)
fi

if ! ls consumer-app/build/libs/consumer-app-*.jar 1> /dev/null 2>&1; then
    echo -e "${RED}❌ Consumer JAR file not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Consumer JAR built successfully${NC}"



echo "Building producer application..."

if [ -f "./producer-app/gradlew" ]; then
    (cd producer-app && ./gradlew clean build --no-daemon)
else
    (cd producer-app && gradle clean build)
fi

if ! ls producer-app/build/libs/producer-app-*.jar 1> /dev/null 2>&1; then
    echo -e "${RED}❌ Producer JAR file not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Producer JAR built successfully${NC}"



# Check for DataDog Java Agent
print_section "Checking DataDog Java Agent"

DD_AGENT_FILE="dd-java-agent.jar"

if [ -f "$DD_AGENT_FILE" ]; then
    echo -e "${GREEN}✅ DataDog Java agent found: $DD_AGENT_FILE${NC}"
else
    echo "DataDog Java agent not found, downloading..."
    if command -v wget &> /dev/null; then
        wget -O "$DD_AGENT_FILE" 'https://dtdg.co/latest-java-tracer'
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ DataDog Java agent downloaded successfully${NC}"
        else
            echo -e "${RED}❌ Failed to download DataDog Java agent${NC}"
            exit 1
        fi
    else
        echo -e "${RED}❌ wget is not installed. Please install wget or download the agent manually${NC}"
        echo "Download URL: https://dtdg.co/latest-java-tracer"
        exit 1
    fi
fi

echo -e "${GREEN}✅ Using console logging (Docker will capture output)${NC}"

# Build Docker images
print_section "Building Docker Images"

echo "Building Docker images with Docker Compose..."
docker-compose build

echo -e "${GREEN}✅ Docker images built successfully${NC}"

# Start services
print_section "Starting Services"

echo "Starting applications with Docker Compose..."
docker-compose up -d

echo "Waiting for services to be ready..."
sleep 10

# Check service status
print_section "Checking Service Status"

echo "Container status:"
docker-compose ps

echo -e "\nWaiting for applications to fully start..."
sleep 20

# Test the deployment
print_section "Testing Deployment"

echo "Testing health endpoint..."
if curl -f -s http://localhost:8080/hello > /dev/null; then
    echo -e "${GREEN}✅ Health check passed${NC}"
else
    echo -e "${RED}❌ Health check failed${NC}"
    echo "Checking logs..."
    docker-compose logs --tail=20 producer-app
fi

echo -e "\nTesting greeting endpoint..."
greeting_response=$(curl -s http://localhost:8080/greet/Docker || echo "FAILED")
if [[ "$greeting_response" != "FAILED" ]]; then
    echo -e "${GREEN}✅ Greeting endpoint works: $greeting_response${NC}"
else
    echo -e "${RED}❌ Greeting endpoint failed${NC}"
fi

echo -e "\nTesting producer-consumer communication..."
produce_response=$(curl -s http://localhost:8080/produce || echo "FAILED")
if [[ "$produce_response" != "FAILED" ]]; then
    echo -e "${GREEN}✅ Producer-Consumer communication works: $produce_response${NC}"
else
    echo -e "${RED}❌ Producer-Consumer communication failed${NC}"
fi

# Show final status
print_section "Deployment Complete"

echo -e "${GREEN}🎉 Deployment completed successfully!${NC}"
echo ""
echo "📋 Summary:"
echo "  • Consumer App: Running in container 'vertx-consumer'"
echo "  • Producer App: Running in container 'vertx-producer' on port 8080"
echo "  • Network: Custom bridge network 'vertx-cluster'"
echo "  • Logs: Available in ./logs/ directory"
echo ""
echo "🔧 Useful Commands:"
echo "  • View logs: docker-compose logs -f"
echo "  • Stop services: docker-compose down"
echo "  • Restart: docker-compose restart"
echo ""
echo "🌐 Test Endpoints:"
echo "  • Health: curl http://localhost:8080/hello"
echo "  • Greeting: curl http://localhost:8080/greet/YourName"
echo "  • Produce: curl http://localhost:8080/produce"
echo ""
echo "📊 Monitor:"
echo "  • Container status: docker-compose ps"
echo "  • Resource usage: docker stats"
echo "  • Networks: docker network ls"

print_section "Log Tail (Last 10 Lines)"
echo "Producer logs:"
docker-compose logs --tail=10 producer-app
echo -e "\nConsumer logs:"
docker-compose logs --tail=10 consumer-app