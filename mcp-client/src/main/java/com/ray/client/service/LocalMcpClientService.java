package com.ray.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.local", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalMcpClientService {

    private final ObjectMapper objectMapper;   // Spring 自动装配的 Jackson ObjectMapper
    @Value("${mcp.local.server-jar:mcp-server/target/mcp-server-1.0.0.jar}")
    private String serverJar;
    private McpSyncClient client;


    @PostConstruct
    public void connect() {
        Path jar = Path.of(serverJar).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar) || !Files.isReadable(jar)) {
            throw new IllegalStateException("本地 MCP Server jar 不存在或不可读：" + jar
                    + "。请先打包 mcp-server，或通过 MCP_LOCAL_SERVER_JAR 设置正确路径");
        }
        int maxRetry = 3;
        for (int i = 1; i <= maxRetry; i++) {
            McpSyncClient attempt = null;
            try {
                StdioClientTransport transport = new StdioClientTransport(
                        ServerParameters.builder("java")
                                .args("-jar", jar.toString(), "--spring.profiles.active=stdio")
                                .build(),
                        new JacksonMcpJsonMapper(objectMapper));
                attempt = McpClient.sync(transport)
                        .clientInfo(new McpSchema.Implementation("ray-agent", "1.0.0"))
                        .build();
                attempt.initialize();
                client = attempt;
                log.info("[MCP] 连接成功（第 {} 次尝试）", i);
                return;
            } catch (Exception e) {
                if (attempt != null) {
                    try {
                        attempt.close();
                    } catch (RuntimeException closeException) {
                        e.addSuppressed(closeException);
                    }
                }
                log.warn("[MCP] 第 {} 次连接失败：{}", i, e.getMessage());
                if (i == maxRetry) throw new RuntimeException("MCP Server 连接失败，已重试 " + maxRetry + " 次", e);
            }
        }
    }
    /**
     * 列出 Server 提供的所有工具
     */
    public List<McpSchema.Tool> listTools() {
        McpSchema.ListToolsResult result = client.listTools();
        return result.tools();
    }

    /**
     * 调用指定工具
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, arguments));

        if (Boolean.TRUE.equals(result.isError())) {
            String errorMsg = result.content().stream()
                    .filter(c -> c instanceof McpSchema.TextContent)
                    .map(c -> ((McpSchema.TextContent) c).text())
                    .findFirst().orElse("未知错误");
            log.error("[MCP] 工具 {} 执行失败：{}", toolName, errorMsg);
            return "工具执行出错，请检查参数是否正确";
        }

        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst()
                .orElse("");
    }

    /**
     * 读取资源（需要 Server 实现了对应的 Resource）
     */
    public String readResource(String uri) {
        McpSchema.ReadResourceResult result = client.readResource(
                new McpSchema.ReadResourceRequest(uri));

        return result.contents().stream()
                .filter(c -> c instanceof McpSchema.TextResourceContents)
                .map(c -> ((McpSchema.TextResourceContents) c).text())
                .findFirst()
                .orElse("");
    }

    @PreDestroy
    public void disconnect() {
        if (client != null) {
            client.close();
            log.info("[MCP] 已断开本地 Server 连接");
        }
    }
}
