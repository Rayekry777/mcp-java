package com.ray.client.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "mcp.remote", name = "enabled", havingValue = "true")
public class RemoteMcpClientConfig {

    @Bean(destroyMethod = "close")
    public McpSyncClient remoteToolsClient(
            @Value("${mcp.remote.base-url:http://localhost:8090}") String baseUrl,
            @Value("${mcp.remote.sse-endpoint:/sse}") String sseEndpoint,
            @Value("${mcp.remote.api-key:}") String apiKey) {
        // SSE 传输层：只需要传 Server 的 URL
        HttpClientSseClientTransport transport =
                HttpClientSseClientTransport.builder(baseUrl)
                        .sseEndpoint(sseEndpoint)
                        .connectTimeout(Duration.ofSeconds(5))
                        .customizeRequest(request -> {
                            if (!apiKey.isBlank()) {
                                request.header("X-API-Key", apiKey);
                            }
                        })
                        .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("ray-agent", "1.0.0"))
                .initializationTimeout(Duration.ofSeconds(15))
                .build();

        // initialize() 内部自动完成握手通知，不需要额外调用
        McpSchema.InitializeResult result;
        try {
            result = client.initialize();
        } catch (RuntimeException ex) {
            try {
                client.close();
            } catch (RuntimeException closeException) {
                ex.addSuppressed(closeException);
            }
            throw new IllegalStateException("远程 MCP SSE 连接失败：" + baseUrl + sseEndpoint
                    + "。请先启动 SSE Server；仅使用 stdio 时设置 MCP_REMOTE_ENABLED=false", ex);
        }
        log.info("[MCP] 已连接远程 Server：{} v{}",
                result.serverInfo().name(), result.serverInfo().version());

        return client;
    }
}
