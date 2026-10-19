package com.ray.client;

import com.ray.client.controller.ThirdPartyMcpController;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ThirdPartyMcpControllerTest {

    @Test
    void routesCallsToTheQualifiedClientAndKeepsAllTextBlocks() throws Exception {
        McpSyncClient filesystem = mock(McpSyncClient.class);
        McpSyncClient github = mock(McpSyncClient.class);
        when(filesystem.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("first"),
                        new McpSchema.TextContent("second")), false));
        when(github.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("repository")), false));
        try (var context = context(filesystem, github)) {
            MockMvc mvc = mvc(context);
            mvc.perform(post("/api/third-party/filesystem/call").param("toolName", "list_directory")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"path\":\"D:/demo\"}"))
                    .andExpect(status().isOk()).andExpect(content().string("first\nsecond"));
            mvc.perform(post("/api/third-party/github/call").param("toolName", "search_repositories")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"mcp\"}"))
                    .andExpect(status().isOk()).andExpect(content().string("repository"));
            verify(filesystem).callTool(new McpSchema.CallToolRequest("list_directory", Map.of("path", "D:/demo")));
            verify(github).callTool(new McpSchema.CallToolRequest("search_repositories", Map.of("query", "mcp")));
        }
    }

    @Test
    void listsToolsForBothServers() throws Exception {
        McpSyncClient filesystem = mock(McpSyncClient.class);
        McpSyncClient github = mock(McpSyncClient.class);
        when(filesystem.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(
                McpSchema.Tool.builder().name("list_directory").description("List files").build()), null));
        when(github.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(
                McpSchema.Tool.builder().name("search_repositories").description("Search repos").build()), null));
        try (var context = context(filesystem, github)) {
            mvc(context).perform(get("/api/third-party/filesystem/tools"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("list_directory：List files"));
            mvc(context).perform(get("/api/third-party/github/tools"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("search_repositories：Search repos"));
        }
    }

    @Test
    void disabledServerDoesNotPreventControllerStartupAndReturns503() throws Exception {
        try (var context = context(null, null)) {
            for (String server : List.of("filesystem", "github")) {
                mvc(context).perform(get("/api/third-party/" + server + "/tools"))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.detail").value(server
                                + " MCP 未启用，请设置 mcp.third-party." + server + ".enabled=true"));
                mvc(context).perform(post("/api/third-party/" + server + "/call").param("toolName", "test")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                        .andExpect(status().isServiceUnavailable());
            }
        }
    }

    @Test
    void toolErrorsReturn502WithTheServerMessage() throws Exception {
        McpSyncClient filesystem = mock(McpSyncClient.class);
        when(filesystem.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("Access denied")), true));
        try (var context = context(filesystem, null)) {
            mvc(context).perform(post("/api/third-party/filesystem/call").param("toolName", "read_file")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("Access denied"));
        }
    }

    @Test
    void blankToolNameReturns400WithoutCallingServer() throws Exception {
        McpSyncClient filesystem = mock(McpSyncClient.class);
        try (var context = context(filesystem, null)) {
            mvc(context).perform(post("/api/third-party/filesystem/call").param("toolName", " ")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(filesystem);
        }
    }

    private AnnotationConfigApplicationContext context(McpSyncClient filesystem, McpSyncClient github) {
        var context = new AnnotationConfigApplicationContext();
        if (filesystem != null) {
            context.registerBean("filesystemMcpClient", McpSyncClient.class, () -> filesystem);
        }
        if (github != null) {
            context.registerBean("githubMcpClient", McpSyncClient.class, () -> github);
        }
        context.register(ThirdPartyMcpController.class);
        context.refresh();
        return context;
    }

    private MockMvc mvc(AnnotationConfigApplicationContext context) {
        return MockMvcBuilders.standaloneSetup(context.getBean(ThirdPartyMcpController.class)).build();
    }
}
