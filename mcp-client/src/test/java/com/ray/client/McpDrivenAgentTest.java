package com.ray.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ray.client.service.McpClientManager;
import com.ray.client.service.McpDrivenAgent;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class McpDrivenAgentTest {

    @Test
    void callbackPreservesSchemaAndRoutesTypedJsonArguments() throws Exception {
        var mapper = new ObjectMapper();
        var manager = mock(McpClientManager.class);
        var builder = mock(ChatClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        when(manager.getAllTools()).thenReturn(List.of(McpSchema.Tool.builder()
                .name("filesystem__read_file").description("Read a file")
                .inputSchema(new JacksonMcpJsonMapper(mapper),
                        "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}")
                .build()));
        when(manager.callTool("filesystem__read_file", Map.of("path", "D:/demo.txt"))).thenReturn("file content");

        new McpDrivenAgent(builder, manager, mapper);
        var callbacks = ArgumentCaptor.forClass(ToolCallback[].class);
        verify(builder).defaultToolCallbacks(callbacks.capture());
        var callback = callbacks.getValue()[0];
        assertThat(mapper.readTree(callback.getToolDefinition().inputSchema()).path("required").get(0).asText())
                .isEqualTo("path");
        assertThat(callback.call("{\"path\":\"D:/demo.txt\"}")).isEqualTo("file content");
        verify(manager).callTool("filesystem__read_file", Map.of("path", "D:/demo.txt"));
    }
}
