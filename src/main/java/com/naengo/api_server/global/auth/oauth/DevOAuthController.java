package com.naengo.api_server.global.auth.oauth;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.SocialLoginRequest;
import com.naengo.api_server.domain.user.service.SocialAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ⚠️ 개발·테스트 전용 OAuth 헬퍼 컨트롤러.
 *
 * <p>모바일 앱에서는 SDK가 액세스 토큰을 직접 발급하므로 이 엔드포인트가 필요 없다.
 * 로컬 개발 환경에서 브라우저로 카카오 OAuth 흐름을 완주하고 싶을 때 사용한다.
 *
 * <p>활성 프로파일이 "local" 일 때만 빈으로 등록된다.
 *
 * <h3>사용 순서</h3>
 * <ol>
 *   <li>카카오 개발자 콘솔에 Redirect URI 등록:
 *       {@code http://localhost:8080/oauth/kakao/test-callback}</li>
 *   <li>브라우저에서 아래 URL 방문 → 카카오 로그인:
 *       {@code GET /oauth/kakao/authorize}</li>
 *   <li>카카오가 code 를 붙여 {@code /oauth/kakao/test-callback} 으로 리다이렉트</li>
 *   <li>서버가 code 를 액세스 토큰으로 교환 → 자체 JWT 발급 → JSON 반환</li>
 * </ol>
 */
@Slf4j
@RestController
@Profile("local")   // local 프로파일에서만 활성화
@RequiredArgsConstructor
public class DevOAuthController {

    private final KakaoTokenClient kakaoTokenClient;
    private final SocialAuthService socialAuthService;

    @Value("${oauth.kakao.auth-url}")
    private String kakaoAuthUrl;

    @Value("${oauth.kakao.rest-api-key:}")
    private String restApiKey;

    @Value("${oauth.kakao.redirect-uri}")
    private String redirectUri;

    /**
     * 브라우저를 카카오 인가 페이지로 안내하는 편의 URL.
     * 브라우저에서 직접 열면 카카오 로그인 화면으로 이동한다.
     *
     * GET http://localhost:8080/oauth/kakao/authorize
     */
    @GetMapping("/oauth/kakao/authorize")
    public ResponseEntity<String> authorize() {
        String url = kakaoAuthUrl
                + "?client_id=" + restApiKey
                + "&redirect_uri=" + redirectUri
                + "&response_type=code";

        // 브라우저용 리다이렉트 HTML
        String html = """
                <!DOCTYPE html>
                <html><head><meta http-equiv="refresh" content="0; url=%s"></head>
                <body>
                  <p>카카오 로그인 페이지로 이동 중...</p>
                  <a href="%s">바로 이동하기</a>
                </body></html>
                """.formatted(url, url);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(html);
    }

    /**
     * 카카오가 인가 코드를 전달하는 콜백 URL.
     * 인가 코드 → 액세스 토큰 교환 → 자체 JWT 발급
     *
     * GET http://localhost:8080/oauth/kakao/test-callback?code={인가코드}
     */
    @GetMapping("/oauth/kakao/test-callback")
    public ResponseEntity<AuthResponse> kakaoCallback(@RequestParam String code) {

        log.info("[Dev] 카카오 인가 코드 수신: {}...", code.substring(0, Math.min(8, code.length())));

        // 1. 인가 코드 → 카카오 액세스 토큰
        String kakaoAccessToken = kakaoTokenClient.exchangeCodeForToken(code);
        log.info("[Dev] 카카오 액세스 토큰 발급 성공");

        // 2. 카카오 액세스 토큰 → 우리 서버 JWT
        SocialLoginRequest request = new SocialLoginRequest(kakaoAccessToken);
        AuthResponse response = socialAuthService.kakaoLogin(request);

        log.info("[Dev] 자체 JWT 발급 성공 — userId={}, nickname={}",
                response.getUserId(), response.getNickname());

        return ResponseEntity.ok(response);
    }
}
