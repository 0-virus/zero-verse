package com.zeroverse.domain.upload;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.common.exception.GlobalExceptionHandler;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.upload.controller.UploadController;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.mock.web.MockMultipartFile;

class UploadControllerTest {

    private UploadService uploadService;
    private MockMvc mockMvc;
    private ZeroverseUserPrincipal principal;

    @BeforeEach
    void setUp() {
        uploadService = mock(UploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(uploadService))
                .addFilters(new SecurityContextPersistenceFilter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        principal = new ZeroverseUserPrincipal(7L, UserRole.USER);
    }

    @Test
    void returnsCreatedEnvelopeForAnAuthenticatedMultipartUpload() throws Exception {
        UUID id = UUID.randomUUID();
        when(uploadService.upload(eq(7L), org.mockito.ArgumentMatchers.any(),
                eq(UploadPurpose.PROFILE_IMAGE)))
                .thenReturn(new UploadResponse(
                        id, "/api/v1/uploads/" + id + "/content", "image/png", 4,
                        UploadPurpose.PROFILE_IMAGE));

        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile(
                                "file", "avatar.png", "image/png", new byte[] {1, 2, 3, 4}))
                        .param("purpose", "PROFILE_IMAGE")
                        .with(authentication(auth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.imageUrl").value(
                        "/api/v1/uploads/" + id + "/content"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(4))
                .andExpect(jsonPath("$.data.purpose").value("PROFILE_IMAGE"));

        verify(uploadService).upload(eq(7L), org.mockito.ArgumentMatchers.any(),
                eq(UploadPurpose.PROFILE_IMAGE));
    }

    @Test
    void returnsBinaryHeadersAndNoStoreForContentRead() throws Exception {
        UUID id = UUID.randomUUID();
        when(uploadService.read(id, 7L))
                .thenReturn(new ImageContent("bytes".getBytes(StandardCharsets.UTF_8), "image/png", 5));

        mockMvc.perform(get("/api/v1/uploads/{id}/content", id).with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Length", "5"));
    }

    @Test
    void mapsAnUnknownPurposeToUploadBindingError() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile(
                                "file", "avatar.png", "image/png", new byte[] {1}))
                        .param("purpose", "UNKNOWN")
                        .with(authentication(auth())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UPLOAD_004"));
    }

    @Test
    void rejectsAnAnonymousMultipartUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile(
                                "file", "avatar.png", "image/png", new byte[] {1}))
                        .param("purpose", "PROFILE_IMAGE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void doesNotDowngradeAnInvalidBearerOnAnAnonymousContentRead() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/uploads/{id}/content", id)
                        .requestAttr(JwtAuthenticationFilter.ATTR_ERROR_CODE, ErrorCode.AUTH_002))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));

        verify(uploadService, org.mockito.Mockito.never()).read(eq(id), org.mockito.ArgumentMatchers.any());
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
