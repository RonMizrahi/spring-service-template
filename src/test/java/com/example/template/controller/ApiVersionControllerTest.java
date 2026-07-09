package com.example.template.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.template.config.WebConfig;

/**
 * Web-layer slice test for ApiVersionController: only the MVC infrastructure loads, not the full
 * context. WebConfig is excluded because it wires application interceptors this slice doesn't need.
 */
@WebMvcTest(controllers = ApiVersionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class))
class ApiVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void v1Status_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.version").value("1.0"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @WithMockUser
    void v2Status_reportsRealUptime() throws Exception {
        mockMvc.perform(get("/api/v2/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.uptimeMs").exists());
    }
}
