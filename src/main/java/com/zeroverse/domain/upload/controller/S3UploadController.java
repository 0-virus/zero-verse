package com.zeroverse.domain.upload.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadRequest;
import com.zeroverse.domain.upload.dto.UploadDtos.PresignedUploadResponse;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.service.S3UploadService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct S3 upload and canonical binary API when the S3 storage backend is selected. */
@RestController
@RequestMapping("/api/v1/uploads")
@ConditionalOnProperty(
        prefix = "zeroverse.upload",
        name = "backend",
        havingValue = "s3")
@Tag(name = "Uploads", description = "private S3 direct upload and permission-checked image read")
public class S3UploadController {

    private final S3UploadService uploadService;

    public S3UploadController(S3UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(path = "/presigned-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "S3 presigned PUT 발급",
            description = "서버 UUID key를 사용하는 5분 private S3 PUT intent를 발급한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<PresignedUploadResponse>> presign(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @Valid @RequestBody PresignedUploadRequest request) {
        requirePrincipal(principal);
        PresignedUploadResponse response = uploadService.presign(principal.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/complete")
    @Operation(
            summary = "S3 upload 검증 완료",
            description = "현재 소유자의 bounded S3 bytes를 검증한 뒤 canonical metadata를 만든다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UploadResponse>> complete(
            @PathVariable("id") String idValue,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal) {
        requirePrincipal(principal);
        UploadResponse response = uploadService.complete(principal.userId(), parseId(idValue));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "canonical image binary read", description = "현재 profile/post ACL을 매 요청 재평가한다.")
    public ResponseEntity<byte[]> read(
            @PathVariable("id") String idValue,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        rejectInvalidBearer(request);
        Long viewerId = principal == null ? null : principal.userId();
        ImageContent content = uploadService.read(parseId(idValue), viewerId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.size());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    private static void requirePrincipal(ZeroverseUserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
    }

    private static UUID parseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return id;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
    }

    private static void rejectInvalidBearer(HttpServletRequest request) {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        if (error instanceof ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }
    }
}
