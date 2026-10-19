package com.ray.client.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/third-party")
public class ThirdPartyMcpController {

    private final ObjectProvider<McpSyncClient> filesystemMcpClient;
    private final ObjectProvider<McpSyncClient> githubMcpClient;

    public ThirdPartyMcpController(
            @Qualifier("filesystemMcpClient") ObjectProvider<McpSyncClient> filesystemMcpClient,
            @Qualifier("githubMcpClient") ObjectProvider<McpSyncClient> githubMcpClient) {
        this.filesystemMcpClient = filesystemMcpClient;
        this.githubMcpClient = githubMcpClient;
    }

    @GetMapping("/filesystem/tools")
    public List<String> filesystemTools() {
        return listTools(requireClient(filesystemMcpClient, "filesystem"));
    }

    @PostMapping("/filesystem/call")
    public String callFilesystem(@RequestParam("toolName") String toolName,
                                 @RequestBody Map<String, Object> args) {
        return callTool(requireClient(filesystemMcpClient, "filesystem"), toolName, args);
    }

    @GetMapping("/github/tools")
    public List<String> githubTools() {
        return listTools(requireClient(githubMcpClient, "github"));
    }

    @PostMapping("/github/call")
    public String callGithub(@RequestParam("toolName") String toolName,
                             @RequestBody Map<String, Object> args) {
        return callTool(requireClient(githubMcpClient, "github"), toolName, args);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleRequestError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
    }

    private McpSyncClient requireClient(ObjectProvider<McpSyncClient> provider, String name) {
        McpSyncClient client = provider.getIfAvailable();
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    name + " MCP 未启用，请设置 mcp.third-party." + name + ".enabled=true");
        }
        return client;
    }

    private List<String> listTools(McpSyncClient client) {
        return client.listTools().tools().stream()
                .map(tool -> tool.name() + "：" + tool.description())
                .toList();
    }

    private String callTool(McpSyncClient client, String toolName, Map<String, Object> args) {
        if (toolName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "toolName 不能为空");
        }
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, args));
        String text = result.content() == null ? "" : result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .collect(Collectors.joining("\n"));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    text.isEmpty() ? "工具调用失败" : text);
        }
        return text.isEmpty() ? "（无返回内容）" : text;
    }
}
