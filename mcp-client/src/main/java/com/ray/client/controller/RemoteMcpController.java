package com.ray.client.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/remote-mcp")
@ConditionalOnProperty(prefix = "mcp.remote", name = "enabled", havingValue = "true")
public class RemoteMcpController {

    private final McpSyncClient remoteToolsClient;

    public RemoteMcpController(@Qualifier("remoteToolsClient") McpSyncClient remoteToolsClient) {
        this.remoteToolsClient = remoteToolsClient;
    }

    @GetMapping("/tools")
    public List<String> listTools() {
        return remoteToolsClient.listTools().tools().stream()
                .map(t -> t.name() + "：" + t.description())
                .toList();
    }

    @PostMapping("/call")
    public String callTool(
            @RequestParam String toolName,
            @RequestBody Map<String, Object> args) {
        McpSchema.CallToolResult result = remoteToolsClient.callTool(
                new McpSchema.CallToolRequest(toolName, args));
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst().orElse("（无返回内容）");
    }
}
