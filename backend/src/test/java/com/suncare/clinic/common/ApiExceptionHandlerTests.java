package com.suncare.clinic.common;

import com.suncare.clinic.common.controller.HealthController;
import com.suncare.clinic.common.exception.ApiException;
import com.suncare.clinic.common.exception.ApiExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(), new TestController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsValidationErrorForBlankField() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void hidesJsonParserDetails() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message").value("Dữ liệu JSON không hợp lệ."));
    }

    @Test
    void returnsKnownBusinessError() throws Exception {
        mockMvc.perform(post("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
    }

    @Test
    void keepsHealthEndpointWorking() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @RestController
    static class TestController {
        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody TestRequest request) {
            return request.name();
        }

        @PostMapping("/test/conflict")
        void conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_EXISTS", "Đã tồn tại.");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
