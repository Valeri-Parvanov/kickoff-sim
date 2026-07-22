package com.kickoffsim;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class KickoffSimApplication {

    public static void main(String[] args) {
        AppLauncher.run(args);
    }
}
