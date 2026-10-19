package com.ray.client.service;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class McpClientManager {

    private final Map<String, McpSyncClient> clients;
    private final LocalMcpClientService localClient;

    public McpClientManager(
            @Qualifier("filesystemMcpClient") ObjectProvider<McpSyncClient> filesystem,
            @Qualifier("githubMcpClient") ObjectProvider<McpSyncClient> github,
            @Qualifier("remoteToolsClient") ObjectProvider<McpSyncClient> remote,
            ObjectProvider<LocalMcpClientService> local) {
        this.clients = new LinkedHashMap<>();
        McpSyncClient filesystemClient = filesystem.getIfAvailable();
        McpSyncClient githubClient = github.getIfAvailable();
        McpSyncClient remoteClient = remote.getIfAvailable();
        if (filesystemClient != null) {
            clients.put("filesystem", filesystemClient);
        }
        if (githubClient != null) {
            clients.put("github", githubClient);
        }
        if (remoteClient != null) {
            clients.put("remote", remoteClient);
        }
        this.localClient = local.getIfAvailable();
    }

    /**
     * 获取所有 Server 的工具列表，合并后供 Agent 使用
     * 工具名加上 serverName 前缀，避免不同 Server 的同名工具冲突
     */
    public List<McpSchema.Tool> getAllTools() {
        List<McpSchema.Tool> allTools = new ArrayList<>();
        clients.forEach((serverName, client) -> {
            try {
                client.listTools().tools().forEach(tool ->
                        // 用双下划线 __ 分隔 serverName 和 toolName
                        // OpenAI/DeepSeek 工具名只允许 ^[a-zA-Z0-9_-]+$，点号非法
                        allTools.add(McpSchema.Tool.builder()
                                .name(serverName + "__" + tool.name())   // filesystem__list_directory
                                .title(tool.title())
                                .description(tool.description())
                                .inputSchema(tool.inputSchema())
                                .outputSchema(tool.outputSchema())
                                .annotations(tool.annotations())
                                .meta(tool.meta())
                                .build())
                );
            } catch (Exception e) {
                log.error("[MCP] {} 工具列表获取失败：{}", serverName, e.getMessage());
            }
        });
        if (localClient != null) {
            localClient.listTools().forEach(tool -> allTools.add(McpSchema.Tool.builder()
                    .name("local__" + tool.name())
                    .title(tool.title())
                    .description(tool.description())
                    .inputSchema(tool.inputSchema())
                    .outputSchema(tool.outputSchema())
                    .annotations(tool.annotations())
                    .meta(tool.meta())
                    .build()));
        }
        return allTools;
    }

    /**
     * 按工具名路由到正确的 Server 执行
     * 格式：serverName__toolName，例如 filesystem__read_file
     */
    public String callTool(String qualifiedToolName, Map<String, Object> arguments) {
        String[] parts = qualifiedToolName == null ? new String[0] : qualifiedToolName.split("__", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return "工具名格式错误，应为 serverName__toolName，例如 filesystem__read_file";
        }

        String serverName = parts[0];
        String toolName   = parts[1];

        if ("local".equals(serverName) && localClient != null) {
            return localClient.callTool(toolName, arguments);
        }

        McpSyncClient client = clients.get(serverName);
        if (client == null) {
            return "未找到 Server：" + serverName + "，可用 Server：" + clients.keySet();
        }

        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest(toolName, arguments));

        String text = result.content() == null ? "" : result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining("\n"));

        if (Boolean.TRUE.equals(result.isError())) {
            log.warn("[MCP] {}__{}  执行失败", serverName, toolName);
            return "工具调用失败：" + (text.isBlank() ? "Server 未返回错误详情" : text);
        }

        return text.isEmpty() ? "（无返回内容）" : text;
    }
}
