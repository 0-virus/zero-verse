package com.zeroverse.domain.upload.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.upload.UploadPurpose;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import com.zeroverse.security.jwt.JwtAuthenticationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 M4 multipart upload와 권한이 적용된 binary read API. */
@RestController
@RequestMapping("/api/v1/uploads")
@Profile({"local", "test"})
@Tag(name = "Uploads", description = "인증된 로컬 이미지 업로드와 권한 있는 이미지 읽기")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "이미지 업로드",
            description = "실제 이미지 바이트를 로컬 저장소에 저장하고 metadata를 반환한다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "성공", useReturnTypeSchema = true)
    public ResponseEntity<ApiResponse<UploadResponse>> upload(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestPart(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "purpose", required = false) String purposeValue) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }
        UploadPurpose purpose = UploadPurpose.from(purposeValue);
        if (purpose == null) {
            throw new BusinessException(ErrorCode.UPLOAD_004);
        }
        UploadResponse response = uploadService.upload(principal.userId(), file, purpose);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}/content")
    @Operation(
            summary = "이미지 binary 읽기",
            description = "현재 profile/post 연결과 글 접근권한을 매 요청 재평가한다.")
    public ResponseEntity<byte[]> read(
            @PathVariable("id") String idValue,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        rejectInvalidBearer(request);
        UUID id = parseId(idValue);
        Long viewerId = principal == null ? null : principal.userId();
        ImageContent content = uploadService.read(id, viewerId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.size());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(content.bytes(), headers, HttpStatus.OK);
    }

    private static UUID parseId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equals(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return id;
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
    }

    /** 공개 GET에서도 명시된 잘못된/만료 Bearer를 익명 요청으로 강등하지 않는다. */
    private static void rejectInvalidBearer(HttpServletRequest request) {
        Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_CODE);
        if (error instanceof ErrorCode errorCode) {
            throw new BusinessException(errorCode);
        }
    }
}
