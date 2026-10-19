package com.ray.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ThirdPartyMcpProperties.class)
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyMcpConfig {

    private final ObjectMapper objectMapper;
    private final ThirdPartyMcpProperties properties;

    @Bean(name = "filesystemMcpClient", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "mcp.third-party.filesystem", name = "enabled", havingValue = "true", matchIfMissing = true)
    public McpSyncClient filesystemClient() {
        List<String> directories = properties.getFilesystem().getAllowedDirectories();
        Assert.notEmpty(directories, "filesystem 至少需要一个授权目录");
        List<String> args = new ArrayList<>(List.of("-y", "@modelcontextprotocol/server-filesystem"));
        for (String directory : directories) {
            Assert.hasText(directory, "filesystem 授权目录不能为空");
            Path path = Path.of(directory).toAbsolutePath().normalize();
            Assert.isTrue(Files.isDirectory(path), "filesystem 授权目录不存在或不是目录：" + path);
            args.add(path.toString());
        }
        return connect("filesystem", properties.getNpxCommand(), args, Map.of());
    }

    @Bean(name = "githubMcpClient", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "mcp.third-party.github", name = "enabled", havingValue = "true")
    public McpSyncClient githubClient(
            @Value("${GITHUB_TOKEN:${GITHUB_PERSONAL_ACCESS_TOKEN:}}") String token) {
        Assert.hasText(token, "启用 GitHub MCP 前请在 IDEA 环境变量中设置 GITHUB_TOKEN");
        String command = StringUtils.hasText(properties.getGithub().getCommand())
                ? properties.getGithub().getCommand() : properties.getNpxCommand();
        return connect("github", command, properties.getGithub().getArgs(),
                Map.of("GITHUB_PERSONAL_ACCESS_TOKEN", token));
    }

    private McpSyncClient connect(String name, String command, List<String> args, Map<String, String> env) {
        Assert.hasText(command, "MCP 启动命令不能为空");
        Assert.notNull(args, "MCP 启动参数不能为空");
        Assert.isTrue(properties.getInitializationTimeout() != null
                        && properties.getInitializationTimeout().isPositive(), "MCP 初始化超时必须大于 0");
        Assert.isTrue(properties.getRequestTimeout() != null
                        && properties.getRequestTimeout().isPositive(), "MCP 请求超时必须大于 0");

        // Windows 的 npx.cmd 是批处理文件，通过 cmd.exe 启动。
        String lowerCommand = command.toLowerCase(Locale.ROOT);
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                && (lowerCommand.endsWith(".cmd") || lowerCommand.endsWith(".bat"))) {
            List<String> windowsArgs = new ArrayList<>(List.of("/c", command));
            windowsArgs.addAll(args);
            command = "cmd.exe";
            args = windowsArgs;
        }

        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(command).args(args).env(env).build(),
                new JacksonMcpJsonMapper(objectMapper));
        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("ray-agent", "1.0.0"))
                .initializationTimeout(properties.getInitializationTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .build();
        try {
            client.initialize();
            log.info("[MCP] 已连接 {} Server，工具数：{}", name, client.listTools().tools().size());
            return client;
        } catch (RuntimeException ex) {
            // Bean 创建失败时 Spring 不会调用 destroyMethod，必须在这里释放子进程。
            try {
                client.close();
            } catch (RuntimeException closeException) {
                ex.addSuppressed(closeException);
            }
            throw new IllegalStateException("连接 " + name + " MCP Server 失败，请检查启动命令、环境和超时配置", ex);
        }
    }
}
