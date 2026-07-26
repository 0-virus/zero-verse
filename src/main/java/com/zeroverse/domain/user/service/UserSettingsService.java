package com.zeroverse.domain.user.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.support.RegisterConstraintMapper;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.ChangePasswordResponse;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UserProfileResponse;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 설정 유스케이스(FR-SETTINGS-01·02).
 *
 * <p>프로필 수정 시 nickname unique는 자신을 제외한 다른 사용자만 검사한다(self-exclusion).
 * 동시 변경은 DB unique 제약이 최종 방어선이고, 그 예외를 도메인 오류로 매핑한다.
 *
 * <p><b>절대로 password를 응답·로그에 넣지 않는다.</b>
 */
@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSettingsService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 프로필을 조회한다(FR-SETTINGS-01).
     *
     * @param userId 인증된 사용자 ID(principal)
     * @return 프로필 정보
     * @throws BusinessException USER_001(404) 사용자 없음
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getBirthDate(),
                user.getBio(),
                user.getProfileImageUrl());
    }

    /**
     * 프로필을 수정한다(FR-SETTINGS-01).
     *
     * <p>name, nickname, bio, birthDate, profileImageUrl을 변경할 수 있다. nickname을 바꿔도
     * Blog slug는 건드리지 않는다.
     *
     * <p>nickname 중복은 먼저 자신을 제외한 다른 사용자로 확인(선조회)하고, 동시 변경은 DB
     * unique 제약이 잡는다. 그 DataIntegrityViolationException을 USER_002로 매핑한다.
     *
     * @param userId 인증된 사용자 ID(principal)
     * @param request 수정 요청
     * @return 수정된 프로필
     * @throws BusinessException USER_001(404) 사용자 없음, USER_002(409) nickname 중복,
     *     VALIDATION_001(400) 필드 검증 실패(엔티티 수준)
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        // 중복 검사는 self-exclusion 쿼리 하나로 끝낸다. "바뀌었을 때만 검사한다"는 앞단
        // 비교를 덧붙이면 방어가 두 겹이 되어, 정작 쿼리가 자기 자신을 제외하는지는 아무도
        // 검증하지 못한다(앞단 비교가 먼저 걸러 쿼리에 도달하지 않는다).
        if (userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(request.nickname(), userId)) {
            throw new BusinessException(ErrorCode.USER_002);
        }

        try {
            user.updateProfile(
                    request.name(),
                    request.nickname(),
                    request.bio(),
                    request.birthDate(),
                    request.profileImageUrl());
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 nickname 변경 시 DB unique 제약 위반. 선조회를 둘 다 통과한 경우다.
            // 정확한 제약을 파악하여 매핑한다(M1 RegisterConstraintMapper 재사용).
            ErrorCode mapped = RegisterConstraintMapper.map(e);
            throw new BusinessException(mapped != null ? mapped : ErrorCode.COMMON_500);
        }

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getBirthDate(),
                user.getBio(),
                user.getProfileImageUrl());
    }

    /**
     * 비밀번호를 변경한다(FR-SETTINGS-02).
     *
     * <p>현재 비밀번호를 검증한 후 새 비밀번호로 변경한다. 새 비밀번호 정책은 회원가입과
     * 동일하다(8~64자, 영문·숫자·특수문자).
     *
     * <p><b>절대로 password를 응답·로그에 넣지 않는다.</b>
     *
     * @param userId 인증된 사용자 ID(principal)
     * @param request 비밀번호 변경 요청
     * @return 성공 응답(빈 DTO)
     * @throws BusinessException USER_001(404) 사용자 없음, USER_005(400) 현재 비밀번호 불일치
     */
    @Transactional
    public ChangePasswordResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));

        // 현재 비밀번호 대조. 불일치 시 USER_005(400).
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USER_005);
        }

        // 새 비밀번호를 BCrypt로 인코딩하여 저장. 엔티티는 이미 인코딩된 해시만 받는다.
        String encodedNewPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedNewPassword);
        userRepository.saveAndFlush(user);

        return new ChangePasswordResponse();
    }
}
