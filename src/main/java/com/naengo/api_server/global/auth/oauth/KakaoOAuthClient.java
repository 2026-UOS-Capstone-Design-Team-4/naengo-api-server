package com.naengo.api_server.global.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 카카오 API를 호출하여 액세스 토큰으로 사용자 정보를 조회하는 클라이언트.
 *
 * <p>흐름: 클라이언트 앱 → (카카오 토큰) → 우리 서버 → 카카오 API 검증 → OAuthUserInfo 반환
 */
@Slf4j
@Component
public class KakaoOAuthClient {

    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient = RestClient.create();

    /**
     * 카카오 액세스 토큰으로 사용자 정보 조회.
     *
     * @param accessToken 클라이언트가 카카오로부터 발급받은 액세스 토큰
     * @return 카카오 사용자 고유 ID와 이메일
     */
    public OAuthUserInfo getUserInfo(String accessToken) {
        try {
            KakaoApiResponse response = restClient.get()
                    .uri(USER_INFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoApiResponse.class);

            if (response == null || response.id() == null) {
                throw new CustomException(ErrorCode.SOCIAL_AUTH_FAILED);
            }

            String providerId = String.valueOf(response.id());
            String email = resolveEmail(response, providerId);

            return new OAuthUserInfo(providerId, email);

        } catch (RestClientException e) {
            log.warn("카카오 API 호출 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.SOCIAL_AUTH_FAILED);
        }
    }

    /**
     * 카카오가 이메일 제공을 거부한 경우 placeholder 이메일을 생성한다.
     */
    private String resolveEmail(KakaoApiResponse response, String providerId) {
        if (response.kakaoAccount() != null && response.kakaoAccount().email() != null) {
            return response.kakaoAccount().email();
        }
        // 이메일 미동의 사용자: 내부 식별용 placeholder
        return "kakao_" + providerId + "@social.naengo.com";
    }

    // ─── 카카오 API 응답 내부 DTO ───────────────────────────────────────────

    private record KakaoApiResponse(
            // 카카오의 user id 는 64bit. 우리 user_id(SERIAL/Integer) 와 별개이며
            // providerUserId 로 String 변환되므로 여기선 Long 으로 받아야 overflow 없음.
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount
    ) {
        private record KakaoAccount(
                String email
        ) {}
    }
}
