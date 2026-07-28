package com.kickoffsim.config;

import com.kickoffsim.client.NotificationClient;
import com.kickoffsim.web.SseEmitterRegistry;
import com.kickoffsim.web.SsePushingNotificationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Bean
    public NotificationClient notificationClient(
            @Value("${notifications.service.url}") String url,
            @Value("${notifications.service.connect-timeout-millis:500}") int connectTimeoutMillis,
            @Value("${notifications.service.read-timeout-millis:1500}") int readTimeoutMillis,
            ObjectMapper objectMapper,
            SseEmitterRegistry sseEmitterRegistry) {
        NotificationClient rawClient = Feign.builder()
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .options(new Request.Options(
                        connectTimeoutMillis, TimeUnit.MILLISECONDS,
                        readTimeoutMillis, TimeUnit.MILLISECONDS,
                        true))
                .logger(new Slf4jLogger(NotificationClient.class))
                .logLevel(Logger.Level.BASIC)
                .target(NotificationClient.class, url);
        return new SsePushingNotificationClient(rawClient, sseEmitterRegistry);
    }
}
