package com.ray.server;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        // 日志位置按 Server 所在目录解析，不受客户端/IDEA 的工作目录影响。
        if (System.getProperty("mcp.server.log-dir") == null) {
            Path directory = new ApplicationHome(McpServerApplication.class).getDir().toPath().normalize();
            if (directory.endsWith(Path.of("target", "classes"))) {
                directory = directory.getParent();
            }
            if (directory.getFileName() != null && directory.getFileName().toString().equals("target")) {
                directory = directory.getParent();
            }
            System.setProperty("mcp.server.log-dir", directory.toString());
        }
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider businessToolCallbacks(BusinessTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
