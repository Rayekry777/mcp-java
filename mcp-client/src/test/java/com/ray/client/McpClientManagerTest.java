package com.ray.client;

import com.ray.client.service.LocalMcpClientService;
import com.ray.client.service.McpClientManager;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpClientManagerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(McpClientManager.class);

    @Test
    void sameToolNamesAreNamespacedAndRoutedBackToOriginalNames() {
        McpSyncClient filesystem = clientWithTool("search");
        McpSyncClient github = clientWithTool("search");
        var args = Map.<String, Object>of("query", "mcp");
        var request = new McpSchema.CallToolRequest("search", args);
        when(filesystem.callTool(request)).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("file1"), new McpSchema.TextContent("file2")), false));
        when(github.callTool(request)).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("repo")), false));
        runner.withBean("filesystemMcpClient", McpSyncClient.class, () -> filesystem)
                .withBean("githubMcpClient", McpSyncClient.class, () -> github)
                .run(context -> {
                    var manager = context.getBean(McpClientManager.class);
                    assertThat(manager.getAllTools()).extracting(McpSchema.Tool::name)
                            .containsExactly("filesystem__search", "github__search");
                    assertThat(manager.callTool("filesystem__search", args)).isEqualTo("file1\nfile2");
                    assertThat(manager.callTool("github__search", args)).isEqualTo("repo");
                    verify(filesystem).callTool(request);
                    verify(github).callTool(request);
                });
    }

    @Test
    void disabledServersAreOptionalAndUnknownToolsFailClearly() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var manager = context.getBean(McpClientManager.class);
            assertThat(manager.getAllTools()).isEmpty();
            assertThat(manager.callTool("github__search", Map.of()))
                    .contains("未找到 Server：github");
        });
    }

    @Test
    void includesLocalBusinessTools() {
        var local = mock(LocalMcpClientService.class);
        when(local.listTools()).thenReturn(List.of(McpSchema.Tool.builder().name("business").build()));
        when(local.callTool("business", Map.of())).thenReturn("local result");
        runner.withBean(LocalMcpClientService.class, () -> local).run(context -> {
            var manager = context.getBean(McpClientManager.class);
            assertThat(manager.getAllTools()).extracting(McpSchema.Tool::name).containsExactly("local__business");
            assertThat(manager.callTool("local__business", Map.of())).isEqualTo("local result");
        });
    }

    @Test
    void toolFailuresKeepTheServerErrorDetails() {
        var filesystem = clientWithTool("read_file");
        when(filesystem.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Access denied")), true));
        runner.withBean("filesystemMcpClient", McpSyncClient.class, () -> filesystem).run(context ->
                assertThat(context.getBean(McpClientManager.class).callTool("filesystem__read_file", Map.of()))
                        .isEqualTo("工具调用失败：Access denied"));
    }

    private McpSyncClient clientWithTool(String name) {
        var client = mock(McpSyncClient.class);
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(
                List.of(McpSchema.Tool.builder().name(name).description("Search").build()), null));
        return client;
    }
}
