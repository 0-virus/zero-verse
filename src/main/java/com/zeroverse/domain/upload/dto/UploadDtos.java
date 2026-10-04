package com.zeroverse.domain.upload.dto;

import com.zeroverse.domain.upload.UploadPurpose;
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

    public record ImageContent(byte[] bytes, String contentType, long size) {}
}
