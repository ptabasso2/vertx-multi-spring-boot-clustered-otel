package com.datadoghq.pej.producer;

import io.vertx.core.AbstractVerticle;

public class GreetingVerticle extends AbstractVerticle {
    @Override
    public void start() {
        vertx.eventBus().<String>consumer("greeting", message -> {
            String name = message.body();
            message.reply("Hi " + name + ", this is a Vert.x-powered greeting!");
        });
        
        System.out.println("GreetingVerticle started and listening for greeting requests");
    }
}
