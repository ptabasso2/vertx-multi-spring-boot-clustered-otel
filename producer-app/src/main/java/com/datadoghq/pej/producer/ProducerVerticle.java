package com.datadoghq.pej.producer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.DeliveryOptions;import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;import org.springframework.stereotype.Component;

@Component
public class ProducerVerticle extends AbstractVerticle {

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    // TextMapSetter for injecting trace context into JsonObject
    private static final TextMapSetter<JsonObject> SETTER = JsonObject::put;

    public ProducerVerticle(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(ProducerVerticle.class.getName(), "1.0.0");
    }

    @Override
    public void start() {
        // CONSUMER - listens for incoming messages
        vertx.eventBus().<String>consumer("producer.trigger", message -> {
            // Create a span for the trigger handling
            Span triggerSpan = tracer.spanBuilder("producer.handle_trigger")
                    .setAttribute("event.bus.address", "producer.trigger")
                    .setAttribute("message.body", message.body())
                    .startSpan();

            try (Scope scope = triggerSpan.makeCurrent()) {
                triggerMessage();
                DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);

                // Reply back to confirm the action
                String replyMessage = "Message triggered successfully";
                message.reply(replyMessage, options);

                triggerSpan.setAttribute("reply.message", replyMessage);
                triggerSpan.setAttribute("reply.success", true);
                triggerSpan.setStatus(StatusCode.OK);

                System.out.println("Traced reply sent: " + replyMessage);

            } catch (Exception e) {
                triggerSpan.setAttribute("reply.success", false);
                triggerSpan.setAttribute("error.message", e.getMessage());
                triggerSpan.setStatus(StatusCode.ERROR, "Failed to handle trigger: " + e.getMessage());
                triggerSpan.recordException(e);
                throw e;
            } finally {
                triggerSpan.end();
            }
        });

        System.out.println("ProducerVerticle started and listening for triggers with context propagation");
    }

    public void triggerMessage() {
        // Create a span for message production
        Span produceSpan = tracer.spanBuilder("producer.send_message")
                .setAttribute("message.destination", "consumer.message")
                .setAttribute("message.type", "PRODUCER_MESSAGE")
                .startSpan();

        try (Scope scope = produceSpan.makeCurrent()) {
            System.out.println("Triggering message from producer...");

            // Create message payload
            JsonObject messagePayload = new JsonObject()
                    .put("messageType", "PRODUCER_MESSAGE")
                    .put("payload", "Hello from Producer!")
                    .put("timestamp", System.currentTimeMillis());

            // Send message with trace context to consumer
            vertx.eventBus().<JsonObject>request("consumer.message", messagePayload)
                    .onSuccess(reply -> {
                        try (Scope replyScope = produceSpan.makeCurrent()) {
                            System.out.println("Producer received reply from consumer: " + reply.body());

                            produceSpan.setAttribute("reply.received", true);
                            if (reply.body() instanceof JsonObject) {
                                JsonObject replyJson = reply.body();
                                produceSpan.setAttribute("reply.type", replyJson.getString("messageType", ""));
                                produceSpan.setAttribute("reply.payload", replyJson.getString("payload", ""));
                            }

                            produceSpan.setStatus(StatusCode.OK);
                        }
                    })
                    .onFailure(err -> {
                        try (Scope errorScope = produceSpan.makeCurrent()) {
                            System.err.println("Producer failed to get reply from consumer: " + err.getMessage());

                            produceSpan.setStatus(StatusCode.ERROR, "Failed to get reply: " + err.getMessage());
                            produceSpan.recordException(err);
                            produceSpan.setAttribute("reply.received", false);
                        }
                    });

        } catch (Exception e) {
            produceSpan.setStatus(StatusCode.ERROR, "Failed to send message: " + e.getMessage());
            produceSpan.recordException(e);
            throw e;
        } finally {
            produceSpan.end();
        }
    }
}