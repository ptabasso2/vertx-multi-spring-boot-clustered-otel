package com.datadoghq.pej.consumer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ConsumerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ConsumerApplication.class).run(args);
    }

    @Bean
    public OpenTelemetry openTelemetry() {
        // For DataDog agent approach
        return GlobalOpenTelemetry.get();
    }

    @Bean
    public ApplicationRunner runner(ConsumerVerticle consumerVerticle) {  // Autowire the verticle directly
        return args -> {
            VertxOptions options = new VertxOptions()
                    .setWorkerPoolSize(20)
                    .setEventLoopPoolSize(4)
                    .setTracingOptions(new OpenTelemetryOptions())
                    .setHAEnabled(true)
                    .setQuorumSize(1);

            Vertx.clusteredVertx(options)
                    .onSuccess(vertx -> {
                        System.out.println("Consumer - Clustered Vert.x instance created with tracing");

                        // Use the autowired ConsumerVerticle
                        vertx.deployVerticle(consumerVerticle)
                                .onSuccess(id -> System.out.println("ConsumerVerticle deployed: " + id))
                                .onFailure(err -> {
                                    System.err.println("Failed to deploy ConsumerVerticle: " + err.getMessage());
                                    err.printStackTrace();
                                });
                    })
                    .onFailure(err -> {
                        System.err.println("Failed to start clustered Vert.x: " + err.getMessage());
                        err.printStackTrace();
                    });
        };
    }
}