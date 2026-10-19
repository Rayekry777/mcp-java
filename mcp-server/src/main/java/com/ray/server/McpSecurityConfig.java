package com.ray.server;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class McpSecurityConfig {

    @Bean
    public FilterRegistrationBean<McpApiKeyFilter> mcpApiKeyFilter(
            @Value("${mcp.security.api-key:}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("请配置 mcp.security.api-key 或环境变量 MCP_API_KEY");
        }

        FilterRegistrationBean<McpApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpApiKeyFilter(apiKey));
        registration.addUrlPatterns("/sse", "/mcp/*");   // 只拦截 MCP 端点
        registration.setOrder(1);
        registration.setName("mcpApiKeyFilter");
        return registration;
    }
}
