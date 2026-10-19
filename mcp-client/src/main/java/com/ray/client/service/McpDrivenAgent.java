package com.ray.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class McpDrivenAgent {

    private final ChatClient chatClient;
    private final McpClientManager mcpManager;
    private final ObjectMapper objectMapper;

    public McpDrivenAgent(ChatClient.Builder builder, McpClientManager mcpManager, ObjectMapper objectMapper) {
        this.mcpManager = mcpManager;
        this.objectMapper = objectMapper;

        // 启动时从所有 MCP Server 拉取工具列表，转成 ToolCallback 注入 ChatClient
        List<ToolCallback> mcpToolCallbacks = buildMcpToolCallbacks();
        log.info("[Agent] 从 MCP Server 加载了 {} 个工具", mcpToolCallbacks.size());

        this.chatClient = builder
                .defaultSystem("""
                        你是一个智能助手，请使用当前提供的 MCP 工具完成用户请求。
                        工具名格式：serverName__toolName，例如 filesystem__read_text_file。
                        filesystem 是文件系统，github 是 GitHub，remote 是 SSE 业务服务，local 是 stdio 业务服务。
                        仅调用实际提供的工具；工具返回失败时如实说明，不要声称操作成功。
                        """)
                .defaultToolCallbacks(mcpToolCallbacks.toArray(new ToolCallback[0]))
                .build();
    }

    private List<ToolCallback> buildMcpToolCallbacks() {
        return mcpManager.getAllTools().stream()
                .<ToolCallback>map(tool -> {
                    String schemaJson;
                    try {
                        schemaJson = tool.inputSchema() == null
                                ? "{\"type\":\"object\",\"properties\":{}}"
                                : objectMapper.writeValueAsString(tool.inputSchema());
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("无法序列化 MCP 工具参数：" + tool.name(), e);
                    }
                    ToolDefinition definition = ToolDefinition.builder()
                            .name(tool.name())
                            .description(tool.description() == null ? tool.name() : tool.description())
                            .inputSchema(schemaJson)
                            .build();
                    return new ToolCallback() {
                        @Override
                        public ToolDefinition getToolDefinition() {
                            return definition;
                        }

                        @Override
                        public String call(String toolInput) {
                            try {
                                Map<String, Object> args = objectMapper.readValue(toolInput, new TypeReference<>() {});
                                return mcpManager.callTool(tool.name(), args);
                            } catch (Exception e) {
                                log.error("[Agent] 工具 {} 调用失败：{}", tool.name(), e.getMessage());
                                return "工具调用失败：" + e.getMessage();
                            }
                        }
                    };
                })
                .toList();
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
