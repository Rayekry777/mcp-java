package com.ray.client.controller;

import com.ray.client.service.LocalMcpClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/local-mcp")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "mcp.local", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LocalMcpController {

    private final LocalMcpClientService localMcpClientService;

    @GetMapping("/tools")
    public List<String> listTools() {
        return localMcpClientService.listTools().stream()
                .map(t -> t.name() + "：" + t.description())
                .toList();
    }

    @GetMapping("/call")
    public String callTool(
            @RequestParam String tool,
            @RequestParam Map<String, Object> args) {
        return localMcpClientService.callTool(tool, args);
    }

    @GetMapping("/resource")
    public String readResource(@RequestParam String uri) {
        return localMcpClientService.readResource(uri);
    }
}
