package com.ray.client;

import com.ray.client.config.RemoteMcpClientConfig;
import com.ray.client.controller.RemoteMcpController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteMcpClientConfigTest {

    @Test
    void remoteConnectionIsOptionalWithoutAnSseServer() {
        new ApplicationContextRunner()
                .withUserConfiguration(RemoteMcpClientConfig.class, RemoteMcpController.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("remoteToolsClient");
                    assertThat(context).doesNotHaveBean(RemoteMcpController.class);
                });
    }
}
