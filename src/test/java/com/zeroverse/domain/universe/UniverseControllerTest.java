package com.zeroverse.domain.universe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zeroverse.common.exception.GlobalExceptionHandler;
import com.zeroverse.common.response.PageResponse;
import com.zeroverse.domain.universe.controller.UniverseController;
import com.zeroverse.domain.universe.dto.UniverseDtos.Availability;
import com.zeroverse.domain.universe.dto.UniverseDtos.DirectionalRelation;
import com.zeroverse.domain.universe.dto.UniverseDtos.RelationshipSnapshotTarget;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRelationshipSnapshot;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestItem;
import com.zeroverse.domain.universe.dto.UniverseDtos.UniverseRequestResponse;
import com.zeroverse.domain.universe.dto.UniverseDtos.UserSummary;
import com.zeroverse.domain.universe.entity.UniverseStatus;
import com.zeroverse.domain.universe.service.UniverseService;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.security.ZeroverseUserPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UniverseControllerTest {

    private UniverseService universeService;
    private MockMvc mockMvc;
    private ZeroverseUserPrincipal principal;

    @BeforeEach
    void setUp() {
        universeService = mock(UniverseService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UniverseController(universeService))
                .addFilters(new SecurityContextPersistenceFilter())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        principal = new ZeroverseUserPrincipal(1L, UserRole.USER);
    }

    @Test
    @DisplayName("신청 생성은 201 공통 envelope을 반환한다")
    void requestReturnsCreatedEnvelope() throws Exception {
        when(universeService.request(1L, 2L)).thenReturn(new UniverseRequestResponse(
                10L, new UserSummary(2L, "target", null, "target-blog"),
                UniverseStatus.PENDING, LocalDateTime.of(2026, 11, 7, 12, 0)));

        mockMvc.perform(post("/api/v1/universe/requests/{targetUserId}", 2L)
                        .with(authentication(auth())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestId").value(10))
                .andExpect(jsonPath("$.data.user.nickname").value("target"));

        verify(universeService).request(1L, 2L);
    }

    @Test
    @DisplayName("받은 신청 목록은 page=0,size=20 기본값을 서비스에 전달한다")
    void receivedUsesDefaultPaging() throws Exception {
        PageResponse<UniverseRequestItem> page = new PageResponse<>(
                List.of(), 0, 20, 0L, 0, false, false);
        when(universeService.listReceived(1L, null, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/universe/requests/received")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items").isArray());

        verify(universeService).listReceived(1L, null, 0, 20);
    }

    @Test
    @DisplayName("관계 snapshot은 private no-store 캐시 헤더를 반환한다")
    void relationshipSnapshotIsPrivateAndNoStore() throws Exception {
        when(universeService.snapshot(1L, 2L)).thenReturn(new UniverseRelationshipSnapshot(
                new RelationshipSnapshotTarget(2L, "target", null, "target-blog"),
                Availability.AVAILABLE, DirectionalRelation.none(), DirectionalRelation.none()));

        mockMvc.perform(get("/api/v1/universe/relationship/{targetUserId}", 2L)
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("private"),
                        org.hamcrest.Matchers.containsString("no-store"))))
                .andExpect(jsonPath("$.data.availability").value("AVAILABLE"));
    }

    @Test
    @DisplayName("차단 경로는 200 빈 공통 envelope을 반환한다")
    void blockReturnsEmptyEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/universe/blocks/{targetUserId}", 2L)
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(universeService).block(1L, 2L);
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
    }
}
