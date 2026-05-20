package com.naengo.api_server.domain.user.service;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.SocialLoginRequest;
import com.naengo.api_server.domain.user.entity.AuthProvider;
import com.naengo.api_server.domain.user.entity.SocialAccount;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.entity.UserProfile;
import com.naengo.api_server.domain.user.repository.SocialAccountRepository;
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
 * 소셜 로그인 처리 서비스 (V5 — social_accounts 분리 후).
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>클라이언트가 보낸 소셜 액세스 토큰으로 제공자 API 호출 → 사용자 정보 획득</li>
 *   <li>{@code (provider, providerUserId)} 로 {@code social_accounts} 에서 기존 link 조회</li>
 *   <li>link 있으면 → 해당 user 로그인. 없으면 이메일 충돌 검사 후 user + social_account 동시 생성</li>
 *   <li>자체 JWT 발급 후 반환</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
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
        Optional<SocialAccount> existingLink = socialAccountRepository
                .findByProviderAndProviderUserId(providerName, userInfo.providerId());

        User user;
        if (existingLink.isPresent()) {
            user = userRepository.findById(existingLink.get().getUserId())
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (user.isBlocked()) {
                throw new CustomException(ErrorCode.USER_BLOCKED);
            }
        } else {
            // 2. 이메일 충돌 검사 — 동일 이메일이 LOCAL 또는 다른 소셜로 이미 가입
            if (userInfo.email() != null && userRepository.existsByEmail(userInfo.email())) {
                throw new CustomException(ErrorCode.EMAIL_PROVIDER_CONFLICT);
            }

            // 3. user + social_account 동시 생성 (같은 트랜잭션)
            user = userRepository.save(
                    User.builder()
                            .email(userInfo.email())
                            .nickname(generateUniqueNickname(provider))
                            .build()
            );
            socialAccountRepository.save(
                    SocialAccount.builder()
                            .userId(user.getUserId())
                            .provider(providerName)
                            .providerUserId(userInfo.providerId())
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
