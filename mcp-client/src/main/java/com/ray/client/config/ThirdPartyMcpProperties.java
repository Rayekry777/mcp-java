package com.ray.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@ConfigurationProperties(prefix = "mcp.third-party")
public class ThirdPartyMcpProperties {

    // 子进程继承 IDEA/Java 进程的 PATH；IDEA 找不到 npx 时可配置绝对路径。
    private String npxCommand;
    private Duration initializationTimeout = Duration.ofSeconds(120);
    private Duration requestTimeout = Duration.ofSeconds(60);
    private Filesystem filesystem = new Filesystem();
    private Github github = new Github();

    public String getNpxCommand() {
        return StringUtils.hasText(npxCommand) ? npxCommand
                : System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                ? "npx.cmd" : "npx";
    }

    @Getter
    @Setter
    public static class Filesystem {
        private boolean enabled = true;
        private List<String> allowedDirectories = List.of(System.getProperty("user.dir"));
    }

    @Getter
    @Setter
    public static class Github {
        private boolean enabled = false;
        // 留空则使用 npxCommand，也可改为官方 github-mcp-server 可执行文件。
        private String command;
        private List<String> args = List.of("-y", "@modelcontextprotocol/server-github");
    }
}
