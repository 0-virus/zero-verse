package com.zeroverse.domain.universe.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationshipSnapshot;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationItem;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestItem;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.security.ZeroverseUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 유니버스 관계 API(FR-UNI-01~12). 모든 엔드포인트는 인증이 필요하다. */
@RestController
@RequestMapping("/api/v1/universe")
@Tag(name = "Universe", description = "유니버스 신청·관계·차단")
@SecurityRequirement(name = "bearerAuth")
public class UniverseController {

    private final UniverseService universeService;

    public UniverseController(UniverseService universeService) {
        this.universeService = universeService;
    }

    @PostMapping("/requests/{targetUserId}")
    @Operation(summary = "유니버스 신청")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "신청 생성", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UNI_001 또는 VALIDATION_001", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003 또는 UNI_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001 또는 UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_002 또는 UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<UniverseRequestResponse>> request(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long targetUserId) {
        UniverseRequestResponse response = universeService.request(principal.userId(), targetUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/requests/received")
    @Operation(summary = "받은 유니버스 신청 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRequestItem>>> received(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listReceived(principal.userId(), nickname, page, size)));
    }

    @GetMapping("/requests/sent")
    @Operation(summary = "보낸 유니버스 신청 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRequestItem>>> sent(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listSent(principal.userId(), nickname, page, size)));
    }

    @PostMapping("/requests/{requestId}/accept")
    @Operation(summary = "유니버스 신청 수락")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003 또는 UNI_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<UniverseRelationshipSnapshot>> accept(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success(universeService.accept(principal.userId(), requestId)));
    }

    @PostMapping("/requests/{requestId}/reject")
    @Operation(summary = "유니버스 신청 거절")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> reject(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long requestId) {
        universeService.reject(principal.userId(), requestId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @DeleteMapping("/requests/{requestId}")
    @Operation(summary = "보낸 유니버스 신청 취소")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> cancel(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long requestId) {
        universeService.cancel(principal.userId(), requestId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "유니버스 관계 삭제")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> deleteFollowing(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long targetUserId) {
        universeService.deleteFollowing(principal.userId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/following")
    @Operation(summary = "내가 발견한 사람 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRelationItem>>> following(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listFollowing(principal.userId(), nickname, page, size)));
    }

    @GetMapping("/followers")
    @Operation(summary = "나를 발견한 사람 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRelationItem>>> followers(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listFollowers(principal.userId(), nickname, page, size)));
    }

    @GetMapping("/friends")
    @Operation(summary = "친구 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRelationItem>>> friends(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listFriends(principal.userId(), nickname, page, size)));
    }

    @PostMapping("/blocks/{targetUserId}")
    @Operation(summary = "사용자 차단")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "UNI_001", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> block(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long targetUserId) {
        universeService.block(principal.userId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @DeleteMapping("/blocks/{targetUserId}")
    @Operation(summary = "차단 해제")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "USER_003", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "UNI_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "UNI_005", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long targetUserId) {
        universeService.unblock(principal.userId(), targetUserId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @GetMapping("/blocks")
    @Operation(summary = "차단 목록")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<PageResponse<UniverseRelationItem>>> blocks(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @RequestParam(required = false) String nickname,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                universeService.listBlocks(principal.userId(), nickname, page, size)));
    }

    @GetMapping("/relationship/{targetUserId}")
    @Operation(summary = "대상 사용자 관계 snapshot")
    @Parameter(name = "targetUserId", in = ParameterIn.PATH, required = true)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "AUTH_004", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "USER_001", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<UniverseRelationshipSnapshot>> relationship(
            @AuthenticationPrincipal ZeroverseUserPrincipal principal,
            @PathVariable Long targetUserId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(universeService.snapshot(principal.userId(), targetUserId)));
    }
}
