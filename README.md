# Vert.x Distributed Tracing with DataDog

This project demonstrates distributed tracing across microservices using **Vert.x**, **Spring Boot**, **Hazelcast clustering**, and **DataDog** with **OpenTelemetry API** for trace context propagation.

## 🏗️ Architecture Overview

```
[HTTP Request] → [Producer Service] → [Event Bus + Trace Injection] → [Consumer Service]
                       ↓                                                      ↓
                [DataDog Agent] ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← [DataDog Agent]
                       ↓                                                      ↓
                            [DataDog Backend - Unified Trace View]
```

## 📋 Prerequisites

### Required Software
- **Java 17+**
- **Docker & Docker Compose**
- **DataDog Agent** (running and accessible)
- **wget** (for downloading DataDog Java agent)

### DataDog Setup
1. **DataDog Account**: Active DataDog account with APM enabled
2. **DataDog Agent**: Running on your system (listening on port 8126)
   ```bash
   # Example DataDog agent setup
   docker run -d --name datadog-agent \
     -e DD_API_KEY=<your-api-key> \
     -e DD_APM_ENABLED=true \
     -e DD_APM_NON_LOCAL_TRAFFIC=true \
     -p 8126:8126 \
     -p 8125:8125/udp \
     datadog/agent:latest
   ```

## 📁 Project Structure

```
vertx-multi-spring-boot-clustered/
├── dd-java-agent.jar                    # ← DataDog Java agent (auto-downloaded)
├── docker-compose.yml                   # ← Container orchestration
├── producer-app/                        # ← HTTP API + Message Producer
│   ├── src/main/java/com/datadoghq/pej/producer/
│   │   ├── ProducerApplication.java     # ← OpenTelemetry bean configuration
│   │   ├── ProducerVerticle.java        # ← Trace context injection
│   │   ├── GreetingVerticle.java
│   │   └── HttpServerVerticle.java
│   ├── build.gradle.kts                 # ← OpenTelemetry API dependency
│   └── Dockerfile                       # ← DataDog agent integration
└── consumer-app/                        # ← Message Consumer
    ├── src/main/java/com/datadoghq/pej/consumer/
    │   ├── ConsumerApplication.java     # ← OpenTelemetry bean configuration
    │   └── ConsumerVerticle.java        # ← Trace context extraction
    ├── build.gradle.kts                 # ← OpenTelemetry API dependency
    └── Dockerfile                       # ← DataDog agent integration
```

## 🔍 Key Implementation Highlights

### 1. OpenTelemetry Bean Configuration
Both applications configure OpenTelemetry to use DataDog agent:

```java
@Bean
public OpenTelemetry openTelemetry() {
    // DataDog agent provides the implementation
    return GlobalOpenTelemetry.get();
}
```

### 2. Trace Context Injection (Producer)
Producer injects trace context into event bus messages:

```java
// Create message with business data
JsonObject messagePayload = new JsonObject()
    .put("messageType", "PRODUCER_MESSAGE")
    .put("payload", "Hello from Producer!");

// Inject current trace context into the message
openTelemetry.getPropagators()
    .getTextMapPropagator()
    .inject(Context.current(), messagePayload, SETTER);
```

### 3. Trace Context Extraction (Consumer)
Consumer extracts trace context and continues the distributed trace:

```java
// Extract trace context from incoming message
Context extractedContext = openTelemetry.getPropagators()
    .getTextMapPropagator()
    .extract(Context.current(), messageBody, GETTER);

// Create span with extracted context as parent
Span consumerSpan = tracer.spanBuilder("consumer.process_message")
    .setParent(extractedContext)
    .startSpan();
```

### 4. DataDog Agent Integration
Applications run with DataDog Java agent for automatic instrumentation:

```dockerfile
ENV JAVA_OPTS="-javaagent:dd-java-agent.jar"
ENV DD_SERVICE=producer-service
ENV DD_AGENT_HOST=datadog-agent
ENV DD_TRACE_AGENT_PORT=8126
```

### 5. Span Creation with Attributes
Custom spans include business context for better observability:

```java
Span span = tracer.spanBuilder("producer.send_message")
    .setAttribute("message.destination", "consumer.message")
    .setAttribute("message.type", "PRODUCER_MESSAGE")
    .startSpan();
```

## 🚀 How to Build

### 1. Download DataDog Agent (Automated)
```bash
# Run the setup script (handles agent download + build)
chmod +x docker-setup.sh
./docker-setup.sh
```

### 2. Manual Build Process
```bash
# Download DataDog Java agent
wget -O dd-java-agent.jar 'https://dtdg.co/latest-java-tracer'

# Build consumer application
cd consumer-app
./gradlew clean build
cd ..

# Build producer application  
cd producer-app
./gradlew clean build
cd ..

# Build and start Docker containers
docker-compose build
docker-compose up -d
```

## 🧪 How to Test

### 1. Verify Services are Running
```bash
# Check container status
docker-compose ps

# Expected output:
#   Name                 State           Ports
# vertx-consumer        Up             
# vertx-producer        Up             0.0.0.0:8080->8080/tcp
```

### 2. Test Basic Functionality
```bash
# Health check
curl http://localhost:8080/hello
# Expected: "Hello from Vert.x (Clustered)"

# Greeting service
curl http://localhost:8080/greet/Alice
# Expected: "Hi Alice, this is a Vert.x-powered greeting!"
```

### 3. Test Distributed Tracing
```bash
# Trigger producer-consumer communication with tracing
curl http://localhost:8080/produce
# Expected: "Triggered producer verticle: Message triggered successfully"
```

### 4. Verify Trace Propagation in Logs

**Producer logs should show:**
```
Triggering message from producer...
Injected trace context into message: {
  "messageType": "PRODUCER_MESSAGE",
  "payload": "Hello from Producer!",
  "timestamp": 1703123456789,
  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
}
Producer received reply from consumer: {...}
```

**Consumer logs should show:**
```
Consumer received message: {
  "messageType": "PRODUCER_MESSAGE",
  "payload": "Hello from Producer!",
  "traceparent": "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
}
Consumer processing payload: Hello from Producer!
Consumer sent reply with trace context: {...}
```

### 5. View Traces in DataDog

1. **Open DataDog APM**: Navigate to APM → Traces in your DataDog dashboard
2. **Search for Services**: Look for `producer-service` and `consumer-service`
3. **View Distributed Trace**: Click on a trace to see the full request flow

**Expected Trace Structure:**
```
http.produce (Root Span - HTTP Request)
├── producer.handle_trigger (Producer handling)
├── producer.send_message (Event bus send)
└── consumer.process_message (Consumer processing)
    └── consumer.process_business_logic (Business logic)
```

**Expected Trace Attributes:**
- **Service Names**: `producer-service`, `consumer-service`
- **Operation Names**: `http.produce`, `producer.send_message`, `consumer.process_message`
- **Custom Attributes**: `message.type`, `message.destination`, `reply.success`
- **Trace ID**: Same across all spans (e.g., `4bf92f3577b34da6a3ce929d0e0e4736`)

## 🔧 Debugging

### Check DataDog Agent Connectivity
```bash
# Verify agent is receiving traces
curl http://localhost:8126/info
# Should return DataDog agent info

# Check agent logs
docker logs datadog-agent | grep -i trace
```

### View Application Logs
```bash
# Producer logs
docker-compose logs -f producer-app

# Consumer logs  
docker-compose logs -f consumer-app

# All logs
docker-compose logs -f
```

### Common Issues

1. **No Traces in DataDog**: Check agent connectivity and API key
2. **Broken Trace Continuity**: Verify trace context injection/extraction
3. **Missing Spans**: Check that spans are properly ended in `finally` blocks
4. **Wrong Service Names**: Verify `DD_SERVICE` environment variables

## 📊 Expected DataDog Metrics

Once running successfully, you should see:

- **Services**: `producer-service`, `consumer-service` in DataDog APM
- **Throughput**: Request rate metrics for `/produce` endpoint
- **Latency**: P50, P95, P99 latency distributions
- **Error Rate**: Success/failure rates for distributed operations
- **Service Map**: Visual representation of producer → consumer communication

## 🎯 Success Criteria

✅ **Containers Running**: Both services start without errors  
✅ **HTTP Endpoints**: All endpoints respond correctly  
✅ **Event Bus Communication**: Producer and consumer exchange messages  
✅ **Trace Propagation**: Same trace ID appears in both services  
✅ **DataDog Integration**: Traces visible in DataDog APM dashboard  
✅ **Distributed Spans**: Complete request flow from HTTP → Producer → Consumer

When all success criteria are met, you'll have a fully functional distributed tracing setup showing end-to-end request flows across your microservices architecture! 🎉