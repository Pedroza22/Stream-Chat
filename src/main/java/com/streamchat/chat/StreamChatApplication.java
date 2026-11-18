package com.streamchat.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication(scanBasePackages = "com.streamchat")
@EntityScan(basePackages = "com.streamchat")
@EnableJpaRepositories(basePackages = "com.streamchat")
@EnableScheduling
public class StreamChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreamChatApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
