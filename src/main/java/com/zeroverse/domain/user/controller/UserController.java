package com.zeroverse.domain.user.controller;

import com.zeroverse.common.response.ApiResponse;
import com.zeroverse.domain.user.dto.UserMeResponse;
import com.zeroverse.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(userService.getMe(userId));
    }
}
