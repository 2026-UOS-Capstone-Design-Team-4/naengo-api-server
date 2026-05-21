package com.naengo.api_server.domain.user.service;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.SocialLoginRequest;
import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.entity.UserIdentity;
import com.naengo.api_server.domain.user.entity.UserProfile;
import com.naengo.api_server.domain.user.repository.UserIdentityRepository;
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
 * 소셜 로그인 처리 (옵션 A — DBv5 / UserIdentity 정합).
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>클라이언트 소셜 액세스 토큰 → 제공자 API 호출 → 사용자 정보</li>
 *   <li>{@code (provider, providerUserId)} 로 {@code user_identities} 조회</li>
 *   <li>link 있으면 → 해당 user 로그인. 없으면 username 충돌 검사 후
 *       user + user_identity 동시 생성</li>
 *   <li>자체 JWT 발급</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserProfileRepository userProfileRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoOAuthClient kakaoOAuthClient;

    @Transactional
    public AuthResponse kakaoLogin(SocialLoginRequest request) {
        OAuthUserInfo userInfo = kakaoOAuthClient.getUserInfo(request.getAccessToken());
        return processLogin(AuthProvider.KAKAO, userInfo);
    }

    private AuthResponse processLogin(AuthProvider provider, OAuthUserInfo userInfo) {
        String providerName = provider.name();

        // 1. (provider, providerUserId) 로 기존 link 조회
        Optional<UserIdentity> existingLink = userIdentityRepository
                .findByProviderAndProviderUserId(providerName, userInfo.providerId());

        User user;
        if (existingLink.isPresent()) {
            user = userRepository.findById(existingLink.get().getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (user.isBlocked()) {
                throw new CustomException(ErrorCode.USER_BLOCKED);
            }
        } else {
            // 2. username 충돌 검사 — 소셜 사용자의 username (받은 이메일 또는 placeholder)
            // 이 이미 자체 가입자에게 사용됐는지. user_identities.email 은 별도 컬럼이라 검사 대상 아님.
            String username = userInfo.email();
            if (username != null && userRepository.existsByUsername(username)) {
                throw new CustomException(ErrorCode.EMAIL_PROVIDER_CONFLICT);
            }

            // 3. user + user_identity 동시 생성 (같은 트랜잭션)
            user = userRepository.save(
                    User.builder()
                            .username(username)
                            .nickname(generateUniqueNickname(provider))
                            .build()
            );
            userIdentityRepository.save(
                    UserIdentity.builder()
                            .userId(user.getUserId())
                            .email(userInfo.email())
                            .provider(providerName)
                            .providerUserId(userInfo.providerId())
                            .build()
            );
            // 마이페이지 진입 시 프로필 row 부재 방지
            userProfileRepository.save(UserProfile.empty(user.getUserId()));
        }

        // 4. JWT 발급
        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getRole());

        return AuthResponse.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .accessToken(token)
                .build();
    }

    /** 제공자 접두어 + UUID 8자리. 예: kakao_a1b2c3d4 */
    private String generateUniqueNickname(AuthProvider provider) {
        String prefix = provider.name().toLowerCase() + "_";
        String candidate;
        do {
            candidate = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        } while (userRepository.existsByNickname(candidate));
        return candidate;
    }
}
