package com.zeroverse.domain.category.controller.api;

import com.zeroverse.auth.security.ZeroverseUserPrincipal;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.category.service.CategoryService;
import com.zeroverse.dto.category.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/blogs/{blogId}/categories")
@Tag(name = "Category", description = "카테고리 관리 API")
public class CategoryApiController {
    private final CategoryService categoryService;

    public CategoryApiController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ZeroverseUserPrincipal)) {
            return null;
        }
        return ((ZeroverseUserPrincipal) authentication.getPrincipal()).getUserId();
    }

    @GetMapping
    @Operation(summary = "카테고리 트리 조회", description = "블로그의 카테고리를 계층 구조로 조회합니다. 공개 API입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그를 찾을 수 없음")
    })
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getCategories(
        @PathVariable Long blogId,
        @RequestParam(defaultValue = "false") boolean includeDrafts
    ) {
        Long userId = getCurrentUserId();
        List<CategoryTreeResponse> categories = categoryService.getActiveTree(blogId, includeDrafts, userId);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @PostMapping
    @Operation(summary = "카테고리 생성", description = "새 카테고리를 생성합니다. 블로그 소유자 전용입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 비즈니스 로직 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그 또는 부모 카테고리를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 카테고리명 또는 순서")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
        @PathVariable Long blogId,
        @Valid @RequestBody CreateCategoryRequest request
    ) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        CategoryResponse response = categoryService.createCategory(blogId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "카테고리 수정", description = "카테고리 정보를 수정합니다. 블로그 소유자 전용입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 비즈니스 로직 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그 또는 카테고리를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "기본/잠금 카테고리 변경 불가 또는 중복")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
        @PathVariable Long blogId,
        @PathVariable Long categoryId,
        @Valid @RequestBody UpdateCategoryRequest request
    ) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        CategoryResponse response = categoryService.updateCategory(blogId, categoryId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "카테고리 삭제", description = "카테고리를 삭제합니다. 하위 카테고리도 함께 삭제됩니다. 블로그 소유자 전용입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그 또는 카테고리를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "기본/잠금 카테고리 삭제 불가")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<DeleteCategoryResponse>> deleteCategory(
        @PathVariable Long blogId,
        @PathVariable Long categoryId
    ) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        DeleteCategoryResponse response = categoryService.deleteCategory(blogId, categoryId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/order")
    @Operation(summary = "카테고리 순서 변경", description = "같은 부모 아래 카테고리들의 순서를 변경합니다. 블로그 소유자 전용입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "순서 변경 성공", content = @Content(mediaType = "application/json")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력값 검증 실패 또는 순서 요청 오류"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필수"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "블로그 또는 카테고리를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "기본/잠금 카테고리 순서 변경 불가")
    })
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> reorderCategories(
        @PathVariable Long blogId,
        @Valid @RequestBody ReorderCategoriesRequest request
    ) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.AUTH_004);
        }

        List<CategoryResponse> response = categoryService.reorderCategories(blogId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
