package com.zeroverse.auth.service;

import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.dto.SigninRequest;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.blog.repository.BlogRepository;
import com.zeroverse.domain.category.entity.Category;
import com.zeroverse.domain.category.repository.CategoryRepository;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class AuthService {
    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                      BlogRepository blogRepository,
                      CategoryRepository categoryRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.blogRepository = blogRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USER_005);
        }

        // Check if nickname already exists
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.USER_006);
        }

        // Validate password strength (must contain alphanumeric and special character)
        if (!isStrongPassword(request.password())) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }

        // Create user with encrypted password
        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.create(
            request.email(),
            encodedPassword,
            request.name(),
            request.nickname(),
            request.birth_date()
        );

        User savedUser = userRepository.save(user);

        // Create default blog
        String urlSlug = request.nickname();
        if (blogRepository.existsByUrlSlug(urlSlug)) {
            // Add -2 suffix if slug is taken
            urlSlug = urlSlug + "-2";
            if (blogRepository.existsByUrlSlug(urlSlug)) {
                // Try -3, -4, etc.
                for (int i = 3; i <= 100; i++) {
                    String candidate = request.nickname() + "-" + i;
                    if (!blogRepository.existsByUrlSlug(candidate)) {
                        urlSlug = candidate;
                        break;
                    }
                }
            }
        }

        Blog blog = new Blog(savedUser, savedUser.getNickname() + "의 블로그", urlSlug, false);
        Blog savedBlog = blogRepository.save(blog);

        // Create default category
        Category category = Category.createDefault(savedBlog);
        categoryRepository.save(category);

        return savedUser;
    }

    public User signin(SigninRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_002));

        if (user.isSuspended()) {
            throw new BusinessException(ErrorCode.AUTH_003);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_002);
        }

        return user;
    }

    private boolean isStrongPassword(String password) {
        boolean hasAlpha = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
        return hasAlpha && hasDigit && hasSpecial;
    }
}
