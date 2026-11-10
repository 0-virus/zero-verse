package com.zeroverse.domain.notification.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.notification.dto.NotificationDtos;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationItem;
import com.zeroverse.domain.notification.dto.NotificationDtos.NotificationPage;
import com.zeroverse.domain.notification.service.NotificationService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M8 알림 목록·관리 API. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "알림 이력과 읽음 관리")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "알림 목록 조회", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<NotificationPage>> list(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request,
            @RequestParam(required = false) String read,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        int pageValue = parseInt(page, 0, "page");
        int sizeValue = parseInt(size, 20, "size");
        NotificationDtos.ReadFilter readFilter = parseRead(read);
        return noStore(ResponseEntity.ok(ApiResponse.success(
                notificationService.list(request, principal, readFilter, pageValue, sizeValue))));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "알림 개별 읽음 처리", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "현재 권한으로 재평가한 알림", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "USER_003 또는 NOT_002",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001 또는 NOT_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<NotificationItem>> read(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        validateId(notificationId);
        return noStore(ResponseEntity.ok(ApiResponse.success(
                notificationService.read(request, principal, notificationId))));
    }

    @PutMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> readAll(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        notificationService.readAll(request, principal);
        return noStore(ResponseEntity.ok(ApiResponse.empty()));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "알림 삭제", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "USER_003 또는 NOT_002",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001 또는 NOT_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            HttpServletRequest request) {
        validateId(notificationId);
        notificationService.delete(request, principal, notificationId);
        return noStore(ResponseEntity.ok(ApiResponse.empty()));
    }

    private static NotificationDtos.ReadFilter parseRead(String raw) {
        if (raw == null) {
            return NotificationDtos.ReadFilter.ALL;
        }
        try {
            return NotificationDtos.ReadFilter.fromQuery(raw);
        } catch (IllegalArgumentException exception) {
            throw invalid("read 값이 올바르지 않습니다.");
        }
    }

    private static int parseInt(String raw, int defaultValue, String name) {
        if (raw == null) return defaultValue;
        if (raw.isBlank()) throw invalid(name + "은 비어 있을 수 없습니다.");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw invalid(name + " 값이 올바르지 않습니다.");
        }
    }

    private static void validateId(Long id) {
        if (id == null || id < 1) {
            throw invalid("notificationId 값이 올바르지 않습니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_001, message);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(ResponseEntity<ApiResponse<T>> response) {
        return ResponseEntity.status(response.getStatusCode())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(response.getBody());
    }
}
