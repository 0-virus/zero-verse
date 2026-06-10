package com.zeroverse.domain.auth.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.auth.dto.AuthResponse;
import com.zeroverse.domain.auth.dto.LoginRequest;
import com.zeroverse.domain.auth.dto.LoginResponse;
import com.zeroverse.domain.auth.dto.RegisterRequest;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.entity.UserRole;
import com.zeroverse.domain.user.repository.UserRepository;
import com.zeroverse.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = new User();
        user.setRole(UserRole.USER);
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setNickname(request.nickname());
        user.setBirthDate(request.birthDate());

        User savedUser = userRepository.save(user);

        return new AuthResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getNickname()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationMs() / 1000,
                user.getId(),
                user.getEmail(),
                user.getNickname()
        );
    }
}
