package com.kickoffsim.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    @Test
    void restClientBuilder_returnsBuilderThatBuildsAWorkingRestClient() {
        RestClient.Builder builder = new RestClientConfig().restClientBuilder();

        assertThat(builder).isNotNull();
        assertThat(builder.build()).isNotNull();
    }
}
