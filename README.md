# Vert.x distributed tracing with Datadog and the OpenTelemetry API

This project demonstrates distributed tracing across microservices using **Vert.x**, **Spring Boot**, **Hazelcast clustering**, and **Datadog** with **OpenTelemetry API** for trace context propagation.

## Architecture overview

```
[HTTP Request] → [Producer application] → [Event bus + Trace injection] → [Consumer application]
                       ↓                                                      ↓
                [Datadog Agent] ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← ← [Datadog Agent]
                       ↓                                                      ↓
                            [Datadog backend - Unified trace view]
```

## Prerequisites

### Required software
- Java 17+
- Docker & Docker compose
- Git client
- Datadog account with a valid API key.
- wget (for downloading DataDog Java agent)
- Your favorite text editor or IDE (Ex IntelliJ, VScode...)


### Clone the repository

```bash
[root@pt-instance-2:~/]$ git clone https://github.com/ptabasso2/vertx-multi-spring-boot-clustered-otel 
[root@pt-instance-6:~/]$ cd vertx-multi-spring-boot-clustered-otel
````


## Project structure

```
vertx-multi-spring-boot-clustered-otel/
├── dd-java-agent.jar                    # ← Datadog java agent (auto-downloaded)
├── docker-compose.yml                   # ← Container orchestration
├── producer-app/                        # ← HTTP API + Message producer
│   ├── src/main/java/com/datadoghq/pej/producer/
│   │   ├── ProducerApplication.java     # ← OpenTelemetry bean configuration
│   │   ├── ProducerVerticle.java        # ← Trace context injection
│   │   ├── GreetingVerticle.java
│   │   └── HttpServerVerticle.java
│   ├── build.gradle.kts                 # ← OpenTelemetry API dependency
│   └── Dockerfile                       # ← Datadog agent integration
└── consumer-app/                        # ← Message consumer
    ├── src/main/java/com/datadoghq/pej/consumer/
    │   ├── ConsumerApplication.java     # ← OpenTelemetry bean configuration
    │   └── ConsumerVerticle.java        # ← Trace context extraction
    ├── build.gradle.kts                 # ← OpenTelemetry API dependency
    └── Dockerfile                       # ← Datadog agent integration
```

## Key implementation highlights

### 1. OpenTelemetry bean configuration
Both applications configure OpenTelemetry to use Datadog agent:

```java
@Bean
public OpenTelemetry openTelemetry() {
    // Datadog agent provides the implementation
    return GlobalOpenTelemetry.get();
}
```

### 2. Trace context injection (Producer)
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

### 3. Trace context extraction (Consumer)
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

### 4. Datadog agent integration
Applications run with the Datadog java agent for automatic instrumentation and the custom instrumentation part is done using the OpenTelemetry API:

```docker-compose.yml
...
    environment:
      # Spring Boot profiles
      - SPRING_PROFILES_ACTIVE=docker
      # JVM settings for containerized environment
      - JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
      # Hazelcast network configuration
      - HAZELCAST_NETWORK_JOIN_MULTICAST_ENABLED=false
      - HAZELCAST_NETWORK_JOIN_TCP_IP_ENABLED=true
      - HAZELCAST_NETWORK_JOIN_TCP_IP_MEMBERS=consumer-app,producer-app
      - JAVA_TOOL_OPTIONS=-javaagent:/app/dd-java-agent.jar -Ddd.agent.host=dd-agent-dogfood-jmx -Ddd.service=consumer-app -Ddd.env=dev -Ddd.version=12 -Ddd.trace.otel.enabled=true -Ddd.trace.sample.rate=1 -Ddd.logs.injection=true -Ddd.profiling.enabled=true -XX:FlightRecorderOptions=stackdepth=256 -Ddd.tags=env:dev
...
```

The `JAVA_TOOL_OPTIONS` env variable contains the necessary options to configure the Datadog java agent. 

### 5. Span creation with attributes
Custom spans include business context for better observability:

```java
Span span = tracer.spanBuilder("producer.send_message")
    .setAttribute("message.destination", "consumer.message")
    .setAttribute("message.type", "PRODUCER_MESSAGE")
    .startSpan();
```

## 🚀 How to build

### 1. Set up the environment (Automated)


```bash
# Make sure to export your API key before running the script. Run the setup script (handles agent download + build)

[root@pt-instance-2:~/vertx/vertx-multi-spring-boot-clustered-otel]$ export DD_API_KEY=<your API key>
[root@pt-instance-2:~/vertx/vertx-multi-spring-boot-clustered-otel]$ chmod +x docker-setup.sh
[root@pt-instance-2:~/vertx/vertx-multi-spring-boot-clustered-otel]$ ./docker-setup.sh
````

### 2. Manual build process
```bash
# Download the Datadog java agent
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

## 🧪 How to test

### 1. Verify services are running
```bash
# Check container status
docker-compose ps

# Expected output:
[root@pt-instance-2:~/vertx/vertx-multi-spring-boot-clustered-otel]$ docker-compose ps
        Name                      Command                   State                                                  Ports                                          
------------------------------------------------------------------------------------------------------------------------------------------------------------------
dd-agent-dogfood-jmx   /bin/entrypoint.sh               Up (healthy)     0.0.0.0:8125->8125/tcp,:::8125->8125/tcp, 8125/udp,                                      
                                                                         0.0.0.0:8126->8126/tcp,:::8126->8126/tcp                                                 
vertx-consumer         sh -c java $JAVA_OPTS -jar ...   Up (healthy)   8081/tcp                                                                                 
vertx-producer         sh -c java $JAVA_OPTS -jar ...   Up (healthy)     0.0.0.0:8080->8080/tcp,:::8080->8080/tcp         
```

### 2. Test basic functionality
```bash
# Health check
curl http://localhost:8080/hello
# Expected: "Hello from Vert.x (Clustered)"

# Greeting service
curl http://localhost:8080/greet/Alice
# Expected: "Hi Alice, this is a Vert.x-powered greeting!"
```

### 3. Test distributed tracing
```bash
# Trigger producer-consumer communication with tracing
curl http://localhost:8080/produce
# Expected: "Triggered producer verticle: Message triggered successfully"
```

### 4. Verify trace propagation in the containers logs

**Producer logs should show:**
```
Triggering message from producer...
Injected trace context into message: {
  "messageType" : "PRODUCER_MESSAGE",
  "payload" : "Hello from Producer!",
  "timestamp" : 1748713257621,
  "x-datadog-trace-id" : "8353280935377654141",
  "x-datadog-parent-id" : "4454626395321405223",
  "x-datadog-sampling-priority" : "2",
  "x-datadog-tags" : "_dd.p.dm=-3,_dd.p.tid=683b3f2900000000",
  "traceparent" : "00-683b3f290000000073ecd0c0ce3a517d-3dd2031adc360f27-01",
  "tracestate" : "dd=s:2;p:3dd2031adc360f27;t.dm:-3;t.tid:683b3f2900000000"
}
Traced reply sent: Message triggered successfully
Producer received reply from consumer: {"messageType":"CONSUMER_REPLY","payload":"Message processed successfully by consumer at 1748713257678","originalMessageType":"PRODUCER_MESSAGE","processingTime":57,"x-datadog-trace-id":"8353280935377654141","x-datadog-parent-id":"7356796356717382617","x-datadog-sampling-priority":"2","x-datadog-tags":"_dd.p.dm=-3,_dd.p.tid=683b3f2900000000","traceparent":"00-683b3f290000000073ecd0c0ce3a517d-6618974ef7070bd9-01","tracestate":"dd=t.dm:-3;t.tid:683b3f2900000000"}
```

**Consumer logs should show:**

```
Consumer received message: {
  "messageType" : "PRODUCER_MESSAGE",
  "payload" : "Hello from Producer!",
  "timestamp" : 1748713257621,
  "x-datadog-trace-id" : "8353280935377654141",
  "x-datadog-parent-id" : "4454626395321405223",
  "x-datadog-sampling-priority" : "2",
  "x-datadog-tags" : "_dd.p.dm=-3,_dd.p.tid=683b3f2900000000",
  "traceparent" : "00-683b3f290000000073ecd0c0ce3a517d-3dd2031adc360f27-01",
  "tracestate" : "dd=s:2;p:3dd2031adc360f27;t.dm:-3;t.tid:683b3f2900000000"
}
Consumer processing payload: Hello from Producer!
Message processing completed for type: PRODUCER_MESSAGE
Consumer sent reply with trace context: {
  "messageType" : "CONSUMER_REPLY",
  "payload" : "Message processed successfully by consumer at 1748713257678",
  "originalMessageType" : "PRODUCER_MESSAGE",
  "processingTime" : 57,
  "x-datadog-trace-id" : "8353280935377654141",
  "x-datadog-parent-id" : "7356796356717382617",
  "x-datadog-sampling-priority" : "2",
  "x-datadog-tags" : "_dd.p.dm=-3,_dd.p.tid=683b3f2900000000",
  "traceparent" : "00-683b3f290000000073ecd0c0ce3a517d-6618974ef7070bd9-01",
  "tracestate" : "dd=t.dm:-3;t.tid:683b3f2900000000"
}
```

### 5. View traces in Datadog

1. **Open the Datadog UI**: Navigate to APM → Traces in the UI
2. **Search for services**: Look for `producer-app` and `consumer-app`
3. **View Distributed trace**: Click on a trace to see the full request flow

<p align="left">
  <img src="img/vertx1.png" width="650" />
</p>



<p align="left">
  <img src="img/vertx2.png" width="650" />
</p>


**Expected trace structure:**
```
natty.request (Root Span - HTTP Request: GET /produce)
├── vertx.route_handler (Producer handling)
├── producer.send_message (Event bus send)
...
└── consumer.process_message (Consumer processing)
    └── consumer.process_business_logic (Consumer business logic)
```

**Expected trace attributes:**
- **Service names**: `producer-service`, `consumer-service`
- **Operation names**: `http.produce`, `producer.send_message`, `consumer.process_message`
- **Custom attributes**: `message.type`, `message.destination`, `reply.success`
- **Trace ID**: Same across all spans (e.g., `4bf92f3577b34da6a3ce929d0e0e4736`)

## 🔧 Debugging

### Check Datadog agent connectivity
```bash
# Verify agent is receiving traces
curl http://localhost:8126/info
# Should return Datadog agent info

# Check agent logs
docker logs datadog-agent | grep -i trace
```

### View application logs
```bash
# Producer logs
docker-compose logs -f producer-app

# Consumer logs  
docker-compose logs -f consumer-app

# All logs
docker-compose logs -f
```

### Common issues

1. **No Traces in Datadog**: Check agent connectivity and API key
2. **Broken traces**: Verify trace context injection/extraction
3. **Missing spans**: Check that spans are properly ended in `finally` blocks
4. **Wrong service names**: Ex verify `DD_SERVICE` environment variables

## Expected Datadog metrics

Once running successfully, you should see:

- **Services**: `producer-app`, `consumer-app` in the Datadog APM UI
- **Throughput**: Request rate metrics for `/produce` endpoint
- **Latency**: P50, P95, P99 latency distributions
- **Error rate**: Success/failure rates for distributed operations
- **Service map**: Visual representation of producer → consumer communication

## Success criteria

✅ **Containers running**: Both services start without errors  
✅ **HTTP endpoints**: All endpoints respond correctly  
✅ **Event Bus communication**: Producer and consumer exchange messages  
✅ **Trace propagation**: Same trace ID appears in both services  
✅ **Datadog integration**: Traces visible in Datadog APM UI (Trace explorer)  
✅ **Distributed trace**: Complete request flow from HTTP → Producer → Consumer

When all success criteria are met, a functional distributed tracing setup showing end-to-end request flows across this microservices architecture will be in place.