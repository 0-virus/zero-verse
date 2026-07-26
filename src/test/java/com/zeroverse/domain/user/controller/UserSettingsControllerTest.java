package com.zeroverse.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.jwt.JwtProvider;
import com.zeroverse.support.MySqlTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 사용자 설정 컨트롤러 테스트(FR-SETTINGS-01·02). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSettingsControllerTest extends MySqlTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    // --- GET /api/v1/users/me (FR-SETTINGS-01) ---

    @Test
    @DisplayName("프로필을 조회할 수 있다")
    void getProfileSuccess() throws Exception {
        User user = persistUser("profile@test.com", "profileuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value("profile@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("profileuser"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    /**
     * `/me`가 <b>토큰 주인의</b> 프로필을 돌려주는지 확인한다. 사용자를 한 명만 만들면 그 한 명이
     * userId 1이라, 컨트롤러가 principal 대신 상수 1을 넘겨도 통과한다.
     */
    @Test
    @DisplayName("여러 사용자가 있을 때 /users/me는 토큰 주인의 프로필만 돌려준다")
    void getProfileReturnsOwnProfileOnly() throws Exception {
        persistUser("owner1@test.com", "ownerone");
        User second = persistUser("owner2@test.com", "ownertwo");
        String secondToken = jwtProvider.issueAccessToken(second, Instant.now()).token();

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(second.getId()))
                .andExpect(jsonPath("$.data.email").value("owner2@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("ownertwo"));
    }

    @Test
    @DisplayName("프로필 응답에 password가 없다")
    void profileResponseHasNoPassword() throws Exception {
        User user = persistUser("nopass@test.com", "nopasser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String response = mockMvc
                .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // JVM `assert`는 -ea 없이는 아무것도 검증하지 않는다. 단정은 항상 AssertJ로 한다.
        assertThat(response).doesNotContain("password");
    }

    // --- PUT /api/v1/users/me (FR-SETTINGS-01) ---

    @Test
    @DisplayName("프로필을 수정할 수 있다")
    void updateProfileSuccess() throws Exception {
        User user = persistUser("update@test.com", "updater");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body =
                objectMapper.writeValueAsString(
                        new com.zeroverse.domain.user.dto.UserSettingsDtos
                                .UpdateProfileRequest(
                                "새이름",
                                "newuser",
                                "새 소개",
                                LocalDate.of(2000, 1, 1),
                                "https://example.com/image.png"));

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("새이름"))
                .andExpect(jsonPath("$.data.nickname").value("newuser"))
                .andExpect(jsonPath("$.data.bio").value("새 소개"));
    }

    @Test
    @DisplayName("닉네임이 필수다")
    void nicknameIsRequired() throws Exception {
        User user = persistUser("req@test.com", "requser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos
                        .UpdateProfileRequest("이름", "", null, null, null));

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='nickname')]").exists());
    }

    @Test
    @DisplayName("닉네임 길이 2~20자 검증 (1자 거부)")
    void nicknameMinLength() throws Exception {
        User user = persistUser("min@test.com", "minuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos
                        .UpdateProfileRequest("이름", "a", null, null, null));

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='nickname')]").exists());
    }

    @Test
    @DisplayName("닉네임 중복 검증 (409)")
    void nicknameUniquenessViolation() throws Exception {
        User user1 = persistUser("dup1@test.com", "dupuser");
        persistUser("dup2@test.com", "otheruser");
        String token = jwtProvider.issueAccessToken(user1, Instant.now()).token();

        // user1이 "otheruser"로 변경 시도 → 중복이므로 409
        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos
                        .UpdateProfileRequest("이름", "otheruser", null, null, null));

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_002"))
                .andExpect(jsonPath("$.error.code").value("USER_002"));
    }

    @Test
    @DisplayName("닉네임을 변경해도 원래 닉네임으로는 다시 저장할 수 있다 (자신 제외)")
    void nicknameCanBeSameAsCurrent() throws Exception {
        User user = persistUser("same@test.com", "sameuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        // 같은 닉네임으로 수정 시도 → 성공해야 함
        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos
                        .UpdateProfileRequest("새이름", "sameuser", null, null, null));

        mockMvc.perform(
                        put("/api/v1/users/me")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("sameuser"));
    }

    // --- PUT /api/v1/users/me/password (FR-SETTINGS-02) ---

    @Test
    @DisplayName("비밀번호를 변경할 수 있다")
    void changePasswordSuccess() throws Exception {
        User user = persistUser("pass@test.com", "passuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest(
                        "Password123!", "NewPassword456!"));

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("비밀번호 변경 응답에 password가 없다")
    void changePasswordResponseHasNoPassword() throws Exception {
        User user = persistUser("pass2@test.com", "passuser2");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest(
                        "Password123!", "NewPassword456!"));

        String response = mockMvc
                .perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("password");
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 (400 USER_005)")
    void wrongCurrentPassword() throws Exception {
        User user = persistUser("wrong@test.com", "wronguser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest(
                        "WrongPassword123!", "NewPassword456!"));

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_005"))
                .andExpect(jsonPath("$.error.code").value("USER_005"));
    }

    @Test
    @DisplayName("새 비밀번호 정책 검증 (영문·숫자·특수문자 필수)")
    void newPasswordPolicyValidation() throws Exception {
        User user = persistUser("policy@test.com", "policyuser");
        String token = jwtProvider.issueAccessToken(user, Instant.now()).token();

        // 특수문자 없음
        String body = objectMapper.writeValueAsString(
                new com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest(
                        "Password123!", "NewPassword456"));

        mockMvc.perform(
                        put("/api/v1/users/me/password")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_001"))
                .andExpect(jsonPath("$.error.details[?(@.field=='newPassword')]").exists());
    }

    private User persistUser(String email, String nickname) {
        User user = User.register(
                email,
                passwordEncoder.encode("Password123!"),
                "테스터",
                nickname,
                LocalDate.of(1995, 1, 1));
        userRepository.saveAndFlush(user);
        return user;
    }
}
