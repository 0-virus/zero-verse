package com.zeroverse.domain.upload.dto;

import com.zeroverse.domain.upload.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 업로드 API의 JSON metadata와 binary read 결과. */
public final class UploadDtos {

    private UploadDtos() {}

    public record UploadResponse(
            UUID id,
            String imageUrl,
            String contentType,
            long size,
            UploadPurpose purpose) {}

    public record PresignedUploadRequest(
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @NotNull Long size,
            @NotNull UploadPurpose purpose,
            @NotBlank String checksumSha256) {}

    public record PresignedUploadResponse(
            UUID id,
            String uploadUrl,
            String method,
            Map<String, String> headers,
            Instant expiresAt) {}

    public record ImageContent(byte[] bytes, String contentType, long size) {}
}
