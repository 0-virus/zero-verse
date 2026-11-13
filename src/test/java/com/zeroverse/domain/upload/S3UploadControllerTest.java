package com.zeroverse.domain.upload;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.common.exception.GlobalExceptionHandler;
import com.zeroverse.domain.upload.controller.S3UploadController;
import com.zeroverse.domain.upload.service.S3UploadService;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.security.ZeroverseUserPrincipal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class S3UploadControllerTest {

    private S3UploadService uploadService;
    private MockMvc mockMvc;
    private ZeroverseUserPrincipal principal;

    @BeforeEach
    void setUp() {
        uploadService = mock(S3UploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new S3UploadController(uploadService))
                .addFilters(new SecurityContextPersistenceFilter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        principal = new ZeroverseUserPrincipal(7L, UserRole.USER);
    }

    @Test
    void mapsMalformedCompleteIdToValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/{id}/complete", "not-a-uuid")
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        verifyNoInteractions(uploadService);
    }

    @Test
    void mapsNonCanonicalContentIdToValidationError() throws Exception {
        String nonCanonicalId = UUID.randomUUID().toString().toUpperCase(Locale.ROOT);

        mockMvc.perform(get("/api/v1/uploads/{id}/content", nonCanonicalId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"));

        verifyNoInteractions(uploadService);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
