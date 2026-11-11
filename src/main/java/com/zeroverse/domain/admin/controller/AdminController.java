package com.zeroverse.domain.admin.controller;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.admin.dto.AdminDtos;
import com.zeroverse.domain.admin.service.AdminService;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.entity.UserStatus;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;
import java.util.Set;

/** M9 관리자 사용자 목록·상세·변경 API. */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin", description = "관리자 사용자 관리")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @Operation(summary = "관리자 사용자 목록", security = @SecurityRequirement(name = "bearerAuth"))
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
                responseCode = "403", description = "ADMIN_001 또는 USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserListData>> list(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort,
            @RequestParam MultiValueMap<String, String> parameters) {
        Set<String> supported = Set.of("keyword", "role", "status", "page", "size", "sort");
        if (parameters.entrySet().stream().anyMatch(entry ->
                !supported.contains(entry.getKey()) || entry.getValue().size() != 1)) {
            throw invalid("지원하지 않거나 중복된 query 값입니다.");
        }
        rejectSort(sort);
        AdminDtos.AdminUserListData data = adminService.list(
                principal.userId(), keyword, parseRole(role), parseStatus(status),
                parseInt(page, 0, "page"), parseInt(size, 20, "size"));
        return noStore(ResponseEntity.ok(ApiResponse.success(data)));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "관리자 사용자 상세", security = @SecurityRequirement(name = "bearerAuth"))
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
                responseCode = "403", description = "ADMIN_001 또는 USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserDetail>> get(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long userId) {
        return noStore(ResponseEntity.ok(ApiResponse.success(
                adminService.get(principal.userId(), userId))));
    }

    @PutMapping("/{userId}/role")
    @Operation(summary = "사용자 역할 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001 또는 ADMIN_002",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "ADMIN_001 또는 USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserDetail>> changeRole(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody AdminDtos.RoleChangeRequest request) {
        return noStore(ResponseEntity.ok(ApiResponse.success(
                adminService.changeRole(principal.userId(), userId, request.role()))));
    }

    @PutMapping("/{userId}/status")
    @Operation(summary = "사용자 상태 변경", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001 또는 ADMIN_002",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "ADMIN_001 또는 USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AdminDtos.AdminUserDetail>> changeStatus(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody AdminDtos.StatusChangeRequest request) {
        return noStore(ResponseEntity.ok(ApiResponse.success(
                adminService.changeStatus(principal.userId(), userId, request.status()))));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "사용자 soft delete", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400", description = "VALIDATION_001 또는 ADMIN_002",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401", description = "AUTH_002 또는 AUTH_004",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "ADMIN_001 또는 USER_003",
                content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "USER_001",
                content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long userId) {
        adminService.delete(principal.userId(), userId);
        return noStore(ResponseEntity.ok(ApiResponse.empty()));
    }

    private static UserRole parseRole(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            throw invalid("role 값이 올바르지 않습니다.");
        }
        try {
            return UserRole.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw invalid("role 값이 올바르지 않습니다.");
        }
    }

    private static UserStatus parseStatus(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isBlank()) {
            throw invalid("status 값이 올바르지 않습니다.");
        }
        try {
            return UserStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw invalid("status 값이 올바르지 않습니다.");
        }
    }

    private static int parseInt(String raw, int defaultValue, String name) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            throw invalid(name + "은 비어 있을 수 없습니다.");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw invalid(name + " 값이 올바르지 않습니다.");
        }
    }

    private static void rejectSort(String sort) {
        if (sort != null) {
            throw invalid("sort 값이 올바르지 않습니다.");
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
