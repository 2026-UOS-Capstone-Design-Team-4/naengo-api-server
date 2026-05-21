package com.naengo.api_server.domain.user.service;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.LoginRequest;
import com.naengo.api_server.domain.user.dto.SignUpRequest;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.entity.UserProfile;
import com.naengo.api_server.domain.user.repository.UserProfileRepository;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.global.auth.JwtTokenProvider;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자체 가입/로그인 (옵션 A — DBv5 정합).
 * email 컬럼이 없으므로 로그인 식별자는 {@code username}. userId 타입은 {@code Integer}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        User saved = userRepository.save(user);

        // 마이페이지 진입 시 프로필 row 부재 방지 — 가입 즉시 빈 프로필 생성
        userProfileRepository.save(UserProfile.empty(saved.getUserId()));

        String token = jwtTokenProvider.generateToken(saved.getUserId(), saved.getRole());

        return AuthResponse.builder()
                .userId(saved.getUserId())
                .nickname(saved.getNickname())
                .role(saved.getRole())
                .accessToken(token)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 차단 여부 확인
        if (user.isBlocked()) {
            throw new CustomException(ErrorCode.USER_BLOCKED);
        }

        // 소셜 전용 계정 (password 없음) → 일반 로그인 불가
        if (user.getPasswordHash() == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS); // 식별자/비번 구분 안 함 (보안)
        }

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getRole());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .accessToken(token)
                .build();
    }
}
