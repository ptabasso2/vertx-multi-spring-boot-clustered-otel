package com.datadoghq.pej.producer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProducerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(ProducerApplication.class).run(args);
    }

    @Bean
    public OpenTelemetry openTelemetry() {
        // For DataDog agent approach
        return GlobalOpenTelemetry.get();
    }

    @Bean
    public ApplicationRunner runner(ProducerVerticle producerVerticle) {  // Autowire the verticle directly
        return args -> {
            VertxOptions options = new VertxOptions()
                    .setWorkerPoolSize(20)
                    .setEventLoopPoolSize(4)
                    .setHAEnabled(true)
                    .setQuorumSize(1);

            Vertx.clusteredVertx(options)
                    .onSuccess(vertx -> {
                        System.out.println("Producer - Clustered Vert.x instance created with tracing");

                        // Use the autowired ProducerVerticle
                        vertx.deployVerticle(producerVerticle)
                                .onSuccess(id -> System.out.println("ProducerVerticle deployed: " + id));

                        vertx.deployVerticle(new GreetingVerticle())
                                .onSuccess(id -> System.out.println("GreetingVerticle deployed: " + id));
                        vertx.deployVerticle(new HttpServerVerticle())
                                .onSuccess(id -> System.out.println("HttpServerVerticle deployed: " + id));
                    })
                    .onFailure(err -> {
                        System.err.println("Failed to start clustered Vert.x: " + err.getMessage());
                        err.printStackTrace();
                    });
        };
    }
}