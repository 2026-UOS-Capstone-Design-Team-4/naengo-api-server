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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {

        // 중복 검사
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        User saved = userRepository.save(user);

        // 마이페이지 진입 시 프로필 row 부재로 인한 비정상 흐름 방지 — 가입 즉시 빈 프로필 생성
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

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        // 차단 여부 확인
        if (user.isBlocked()) {
            throw new CustomException(ErrorCode.USER_BLOCKED);
        }

        // 소셜 로그인 전용 계정은 비밀번호 없음 → 일반 로그인 불가
        if (user.getPasswordHash() == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS); // 이메일/비번 구분 안 함 (보안)
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
