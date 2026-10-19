package com.ray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.client.service.LocalMcpClientService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;


import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMcpClientServiceTest {

    @Test
    void missingJarFailsBeforeStartingProcess(@TempDir Path directory) {
        var service = new LocalMcpClientService(new ObjectMapper());
        Path missingJar = directory.resolve("missing.jar");
        ReflectionTestUtils.setField(service, "serverJar", missingJar.toString());
        assertThatThrownBy(service::connect)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missingJar.toString())
                .hasMessageContaining("MCP_LOCAL_SERVER_JAR");
    }
}
