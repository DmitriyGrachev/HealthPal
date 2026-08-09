package com.fit.fitnessapp.exception;

import com.fit.fitnessapp.ai.RateLimitInterceptor;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import com.fit.fitnessapp.auth.infrastructure.utils.TokenFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiErrorHandlerWebTest.ErrorEndpoints.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiErrorHandlerWebTest.ErrorEndpoints.class)
class ApiErrorHandlerWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RateLimitInterceptor rateLimitInterceptor;
    @MockitoBean private TokenFilter tokenFilter;

    @Test
    void validationErrorsUseApiErrorContract() throws Exception {
        mockMvc.perform(post("/errors/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.path").value("/errors/validation"));
    }

    @Test
    void forbiddenErrorsUseApiErrorContract() throws Exception {
        mockMvc.perform(get("/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void missingFatSecretConnectionUsesConflict() throws Exception {
        mockMvc.perform(get("/errors/fatsecret-missing"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FATSECRET_NOT_CONNECTED"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void aiUnavailableUsesServiceUnavailable() throws Exception {
        mockMvc.perform(get("/errors/ai-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("External AI service is temporarily unavailable"));
    }

    @Test
    void externalApiFailureUsesBadGateway() throws Exception {
        mockMvc.perform(get("/errors/external-api"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EXTERNAL_API_FAILURE"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("External provider request failed"));
    }

    @RestController
    static class ErrorEndpoints {
        @PostMapping("/errors/validation")
        void validation(@Valid @RequestBody ValidationPayload payload) {
        }

        @GetMapping("/errors/forbidden")
        void forbidden() {
            throw new AccessDeniedException("not allowed");
        }

        @GetMapping("/errors/fatsecret-missing")
        void missingFatSecret() {
            throw new IllegalArgumentException("User not connected to FatSecret");
        }

        @GetMapping("/errors/ai-unavailable")
        void aiUnavailable() {
            throw new AiUnavailableException("All AI providers are unavailable", new RuntimeException("provider down"));
        }

        @GetMapping("/errors/external-api")
        void externalApi() {
            throw new RuntimeException("Failed to fetch data from FatSecret", new RuntimeException("upstream down"));
        }
    }

    record ValidationPayload(@NotBlank String name) {
    }
}
