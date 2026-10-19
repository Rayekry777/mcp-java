package com.ray.client.controller;

import com.ray.client.service.McpClientManager;
import com.ray.client.service.McpDrivenAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpAgentController {

    private final McpClientManager mcpManager;
    private final McpDrivenAgent agent;

    /** 查看当前 Agent 能用哪些工具 */
    @GetMapping("/tools")
    public List<String> listAllTools() {
        return mcpManager.getAllTools().stream()
                .map(t -> t.name() + "：" + t.description())
                .toList();
    }

    /** 和 Agent 对话，Agent 会自动调用 MCP 工具 */
    @PostMapping("/chat")
    public String chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (!StringUtils.hasText(message)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        return agent.chat(message);
    }
}
