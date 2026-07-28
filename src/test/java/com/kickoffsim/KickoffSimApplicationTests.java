package com.kickoffsim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KickoffSimApplicationTests {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext.isActive()).isTrue();
    }

    @Test
    void main_startsContextAndSetsSofiaTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Sofia"));
        try (ConfigurableApplicationContext context = SpringApplication.run(
                KickoffSimApplication.class, "--server.port=0")) {
            assertThat(context.isActive()).isTrue();
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Europe/Sofia");
        }
    }

}
