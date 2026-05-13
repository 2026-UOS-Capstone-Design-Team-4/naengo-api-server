package com.naengo.api_server.domain.user.controller;

import com.naengo.api_server.domain.user.dto.AuthResponse;
import com.naengo.api_server.domain.user.dto.LoginRequest;
import com.naengo.api_server.domain.user.dto.SignUpRequest;
import com.naengo.api_server.domain.user.dto.SocialLoginRequest;
import com.naengo.api_server.domain.user.service.AuthService;
import com.naengo.api_server.domain.user.service.SocialAuthService;
import com.naengo.api_server.global.auth.AuthCookieFactory;
import com.naengo.api_server.global.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 자체 회원가입 / 로그인 / 소셜 로그인 / 로그아웃.
 *
 * <p>응답에 두 가지 인증 통로 모두 노출 (`docs/spec/auth-cookie.md`):
 * <ul>
 *   <li>Body 의 {@code accessToken} — 모바일 / 외부 API 호출용</li>
 *   <li>Set-Cookie 의 HttpOnly cookie — 브라우저 자동 동봉용</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final AuthCookieFactory authCookieFactory;

    // ─── 자체 회원가입 / 로그인 ───────────────────────────────────────────

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthResponse response = authService.signUp(request);
        return withAuthCookie(HttpStatus.CREATED, response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return withAuthCookie(HttpStatus.OK, response);
    }

    // ─── 소셜 로그인 ────────────────────────────────────────────────────
    // 클라이언트가 카카오·구글에서 받은 액세스 토큰을 전달하면,
    // 서버가 검증 후 자체 JWT를 발급한다.

    @PostMapping("/social/kakao")
    public ResponseEntity<ApiResponse<AuthResponse>> kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        AuthResponse response = socialAuthService.kakaoLogin(request);
        return withAuthCookie(HttpStatus.OK, response);
    }

    @PostMapping("/social/google")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        AuthResponse response = socialAuthService.googleLogin(request);
        return withAuthCookie(HttpStatus.OK, response);
    }

    /**
     * 로그아웃 — 쿠키 만료. stateless JWT 라 토큰 자체는 만료까지 유효 (블랙리스트 미적용).
     * 쿠키 없이 호출해도 200 (멱등). 인증 없이도 호출 가능 (`/api/auth/**` 는 permitAll).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie expired = authCookieFactory.expire();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withAuthCookie(HttpStatus status, AuthResponse response) {
        ResponseCookie cookie = authCookieFactory.build(response.getAccessToken());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response));
    }
}
