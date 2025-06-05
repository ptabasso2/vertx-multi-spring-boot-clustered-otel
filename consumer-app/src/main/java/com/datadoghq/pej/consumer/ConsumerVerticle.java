package com.datadoghq.pej.consumer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.core.MultiMap;
import org.springframework.stereotype.Component;

@Component
public class ConsumerVerticle extends AbstractVerticle {

  private final OpenTelemetry openTelemetry;
  private final Tracer tracer;

  public ConsumerVerticle(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.tracer = openTelemetry.getTracer(ConsumerVerticle.class.getName(), "1.0.0");
  }

  @Override
  public void start() {
    // CONSUMER - listens for messages and extracts trace context
    vertx
        .eventBus()
        .<JsonObject>consumer(
            "consumer.message",
            message -> {
              JsonObject messageBody = message.body();

              System.out.println("Consumer received message: " + messageBody.encodePrettily());

              // Extract trace context from message headers
              Context extractedContext = Context.current();
              if (message.headers() != null) {
                extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                    .extract(Context.current(), message.headers(), new TextMapGetter<MultiMap>() {
                      @Override
                      public Iterable<String> keys(MultiMap carrier) {
                        return carrier.names();
                      }

                      @Override
                      public String get(MultiMap carrier, String key) {
                        return carrier.get(key);
                      }
                    });
              }

              // Create a span with the extracted context as parent
              Span consumerSpan;
              try (Scope scope = extractedContext.makeCurrent()) {
                consumerSpan = tracer
                    .spanBuilder("consumer.process_message")
                    .setAttribute("message.address", "consumer.message")
                    .setAttribute("service.name", "consumer-app")
                    .startSpan();
              }

              try (Scope scope = consumerSpan.makeCurrent()) {
                String messageType = messageBody.getString("messageType", "UNKNOWN");
                Object payload = messageBody.getValue("payload");
                Long timestamp = messageBody.getLong("timestamp");

                consumerSpan.setAttribute("message.type", messageType);
                consumerSpan.setAttribute("message.timestamp", timestamp != null ? timestamp : 0L);

                if (payload != null) {
                  consumerSpan.setAttribute("message.payload", payload.toString());
                  System.out.println("Consumer processing payload: " + payload);
                }

                // Process the message
                processMessage(messageBody, consumerSpan);

                // Create reply with trace context
                JsonObject reply =
                    new JsonObject()
                        .put("messageType", "CONSUMER_REPLY")
                        .put(
                            "payload",
                            "Message processed successfully by consumer at "
                                + System.currentTimeMillis())
                        .put("originalMessageType", messageType)
                        .put(
                            "processingTime",
                            System.currentTimeMillis() - (timestamp != null ? timestamp : 0L));

                // Send reply with trace context
                message.reply(reply);

                consumerSpan.setAttribute("reply.sent", true);
                consumerSpan.setAttribute("reply.type", "CONSUMER_REPLY");
                consumerSpan.setStatus(StatusCode.OK);

                System.out.println(
                    "Consumer sent reply with trace context: " + reply.encodePrettily());

              } catch (Exception e) {
                consumerSpan.setStatus(
                    StatusCode.ERROR, "Failed to process message: " + e.getMessage());
                consumerSpan.recordException(e);
                consumerSpan.setAttribute("reply.sent", false);

                // Send error reply with trace context
                JsonObject errorReply =
                    new JsonObject()
                        .put("messageType", "CONSUMER_ERROR")
                        .put("payload", "Error processing message: " + e.getMessage())
                        .put("error", true);

                message.reply(errorReply);

                System.err.println("Error processing message: " + e.getMessage());
              } finally {
                consumerSpan.end();
              }
            });

    System.out.println(
        "ConsumerVerticle started and listening for messages with context propagation");
  }

  private void processMessage(JsonObject messageBody, Span parentSpan) {
    // Create a child span for business logic processing
    Span processSpan =
        tracer
            .spanBuilder("consumer.process_business_logic")
            .setParent(Context.current().with(parentSpan))
            .startSpan();

    try (Scope scope = processSpan.makeCurrent()) {
      processSpan.setAttribute("processing.step", "validation");

      String messageType = messageBody.getString("messageType");
      if (messageType == null) {
        throw new IllegalArgumentException("Message type is required");
      }

      processSpan.setAttribute("processing.step", "business_logic");
      processSpan.setAttribute("message.type", messageType);

      // Simulate processing time
      try {
        Thread.sleep(50); // Simulate work
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Processing interrupted", e);
      }

      processSpan.setAttribute("processing.step", "completed");
      processSpan.setAttribute("processing.duration_ms", 50);
      processSpan.setStatus(StatusCode.OK);

      System.out.println("Message processing completed for type: " + messageType);

    } catch (Exception e) {
      processSpan.setStatus(StatusCode.ERROR, "Processing failed: " + e.getMessage());
      processSpan.recordException(e);
      throw e;
    } finally {
      processSpan.end();
    }
  }
}
