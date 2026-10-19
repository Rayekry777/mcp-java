package com.ray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.client.config.ThirdPartyMcpConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ThirdPartyMcpConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ThirdPartyMcpConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void bothServersCanBeDisabledWithoutNodeOrToken() {
        runner.withPropertyValues("mcp.third-party.filesystem.enabled=false",
                        "mcp.third-party.github.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("filesystemMcpClient").doesNotHaveBean("githubMcpClient");
                });
    }

    @Test
    void enablingGithubWithoutTokenFailsWithAnActionableMessage() {
        runner.withPropertyValues("mcp.third-party.filesystem.enabled=false",
                        "mcp.third-party.github.enabled=true", "GITHUB_TOKEN=")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("启用 GitHub MCP 前请在 IDEA 环境变量中设置 GITHUB_TOKEN"));
    }

    @Test
    void nonexistentAllowedDirectoryFailsBeforeLaunchingNpx(@TempDir Path directory) {
        runner.withPropertyValues("mcp.third-party.filesystem.allowed-directories[0]=" + directory.resolve("missing"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("filesystem 授权目录不存在或不是目录：" + directory.resolve("missing")));
    }
}
