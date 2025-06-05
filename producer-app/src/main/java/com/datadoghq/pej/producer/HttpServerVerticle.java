package com.datadoghq.pej.producer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class HttpServerVerticle extends AbstractVerticle {

    @Override
    public void start() {
        createRouter().onSuccess(router -> {
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(8080, result -> {
                    if (result.succeeded()) {
                        System.out.println("Producer HTTP server started on port 8080");
                    } else {
                        System.err.println("Failed to start HTTP server: " + result.cause());
                    }
                });
        });
    }

    private Future<Router> createRouter() {
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/hello").handler(ctx -> {
            ctx.response().end("Hello from Vert.x (Clustered)");
        });

        router.get("/greet/:name").handler(ctx -> {
            String name = ctx.pathParam("name");
            vertx.eventBus()
                    .<String>request("greeting", name)
                    .onSuccess(reply -> ctx.response().end(reply.body()))
                    .onFailure(err -> ctx.response().setStatusCode(500).end("Error: " + err.getMessage()));

        });

        router.get("/produce").handler(ctx -> {
            // REQUEST - sends a message and expects a reply
            DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);
            vertx.eventBus()
                    .<String>request("producer.trigger", "", options)
                    .onSuccess(reply -> {
                        ctx.response().end("Triggered producer verticle: " + reply.body());
                    })
                    .onFailure(err -> {
                        ctx.response().setStatusCode(500).end("Producer not available: " + err.getMessage());
                    });
        });

        return Future.succeededFuture(router);
    }
}
