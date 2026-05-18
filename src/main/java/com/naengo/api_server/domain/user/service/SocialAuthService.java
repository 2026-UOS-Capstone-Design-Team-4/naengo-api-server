package com.naengo.api_server.domain.user.service;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.SocialLoginRequest;
import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.entity.UserProfile;
import com.naengo.api_server.domain.user.repository.UserProfileRepository;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.global.auth.JwtTokenProvider;
import com.naengo.api_server.global.auth.oauth.KakaoOAuthClient;
import com.naengo.api_server.global.auth.oauth.OAuthUserInfo;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 로그인 처리 서비스.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>클라이언트가 보낸 소셜 액세스 토큰으로 제공자 API 호출 → 사용자 정보 획득</li>
 *   <li>provider + providerId로 기존 계정 조회</li>
 *   <li>기존 계정 없으면 신규 생성 (이메일 충돌 시 예외)</li>
 *   <li>자체 JWT 발급 후 반환</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoOAuthClient kakaoOAuthClient;

    @Transactional
    public AuthResponse kakaoLogin(SocialLoginRequest request) {
        OAuthUserInfo userInfo = kakaoOAuthClient.getUserInfo(request.getAccessToken());
        return processLogin(AuthProvider.KAKAO, userInfo);
    }

    /**
     * 소셜 사용자 정보로 계정을 찾거나 생성하고 자체 JWT를 발급한다.
     */
    private AuthResponse processLogin(AuthProvider provider, OAuthUserInfo userInfo) {
        // 1. 동일 제공자 + 동일 providerId로 기존 계정 조회
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(
                provider, userInfo.providerId()
        );

        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (user.isBlocked()) {
                throw new CustomException(ErrorCode.USER_BLOCKED);
            }
        } else {
            // 2. 동일 이메일이 다른 방식(LOCAL 또는 다른 소셜)으로 이미 가입되었는지 확인
            if (userRepository.existsByEmail(userInfo.email())) {
                throw new CustomException(ErrorCode.EMAIL_PROVIDER_CONFLICT);
            }

            // 3. 신규 소셜 사용자 등록
            user = userRepository.save(
                    User.builder()
                            .email(userInfo.email())
                            .nickname(generateUniqueNickname(provider))
                            .provider(provider)
                            .providerId(userInfo.providerId())
                            .build()
            );
            // 마이페이지 진입 시 프로필 row 부재 방지 — 신규 소셜 가입 즉시 빈 프로필 생성
            userProfileRepository.save(UserProfile.empty(user.getUserId()));
        }

        // 4. 자체 JWT 발급
        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getRole());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .accessToken(token)
                .build();
    }

    /**
     * 제공자 접두어 + UUID 8자리로 충돌 가능성이 낮은 닉네임을 생성한다.
     * 예) kakao_a1b2c3d4
     */
    private String generateUniqueNickname(AuthProvider provider) {
        String prefix = provider.name().toLowerCase() + "_";
        String candidate;
        do {
            candidate = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (userRepository.existsByNickname(candidate));
        return candidate;
    }
}
