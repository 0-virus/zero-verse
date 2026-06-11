package com.zeroverse.domain.user.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.user.dto.UserMeResponse;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new UserMeResponse(
                user.getId(),
                user.getRole().name(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getBirthDate(),
                user.getBio(),
                user.getProfileImageUrl()
        );
    }
}
