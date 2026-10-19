package com.ray.client;

import com.ray.client.controller.McpAgentController;
import com.ray.client.service.McpClientManager;
import com.ray.client.service.McpDrivenAgent;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpAgentControllerTest {

    @Test
    void rejectsMissingAndBlankMessagesBeforeCallingModel() throws Exception {
        var agent = mock(McpDrivenAgent.class);
        var mvc = MockMvcBuilders.standaloneSetup(new McpAgentController(mock(McpClientManager.class), agent)).build();
        for (String body : new String[]{"{}", "{\"message\":null}", "{\"message\":\" \"}"}) {
            mvc.perform(post("/api/mcp/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(agent);
    }

    @Test
    void validMessageReachesAgent() throws Exception {
        var agent = mock(McpDrivenAgent.class);
        when(agent.chat("hello")).thenReturn("reply");
        var mvc = MockMvcBuilders.standaloneSetup(new McpAgentController(mock(McpClientManager.class), agent)).build();
        mvc.perform(post("/api/mcp/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(content().string("reply"));
    }
}
