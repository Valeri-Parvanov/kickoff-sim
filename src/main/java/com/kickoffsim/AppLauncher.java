package com.kickoffsim;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.TimeZone;

class AppLauncher {

    static ConfigurableApplicationContext run(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Sofia"));
        return SpringApplication.run(KickoffSimApplication.class, args);
    }
}
