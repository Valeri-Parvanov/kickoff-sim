package com.kickoffsim.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    @Test
    void home_returnsIndex() {
        assertThat(new HomeController().home()).isEqualTo("index");
    }
}
