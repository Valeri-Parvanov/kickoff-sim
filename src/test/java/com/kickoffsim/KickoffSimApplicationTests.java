package com.kickoffsim;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KickoffSimApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void run_startsContextAndSetsSofiaTimeZone() {
        ConfigurableApplicationContext context = AppLauncher.run(new String[]{"--server.port=0"});
        try {
            assertThat(context.isActive()).isTrue();
            assertThat(TimeZone.getDefault().getID()).isEqualTo("Europe/Sofia");
        } finally {
            context.close();
        }
    }

}
