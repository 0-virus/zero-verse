package com.zeroverse.auth.service;

import com.zeroverse.auth.dto.RegisterRequest;
import com.zeroverse.auth.dto.SigninRequest;
import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.common.util.SlugGenerator;
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

        // Note: Password strength validation is done by @Pattern on RegisterRequest DTO
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

        // Create default blog with normalized slug (PRD §4.5)
        String urlSlug = SlugGenerator.generateUnique(
            request.nickname(),
            blogRepository::existsByUrlSlug
        );

        Blog blog = new Blog(savedUser, savedUser.getNickname() + "의 블로그", urlSlug, false);
        Blog savedBlog = blogRepository.save(blog);

        // Create default category
        Category category = Category.createDefault(savedBlog);
        categoryRepository.save(category);

        return savedUser;
    }

    public User signin(SigninRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_001));

        // Verify password first (before checking suspended status)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }

        // Only check suspended status after successful authentication
        if (user.isSuspended()) {
            throw new BusinessException(ErrorCode.USER_003);
        }

        return user;
    }
}
