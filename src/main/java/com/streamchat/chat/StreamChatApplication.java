package com.streamchat.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class StreamChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamChatApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
