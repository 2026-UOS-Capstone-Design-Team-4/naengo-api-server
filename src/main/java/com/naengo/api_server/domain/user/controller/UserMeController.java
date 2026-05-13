package com.naengo.api_server.domain.user.controller;

import com.naengo.api_server.domain.user.dto.PasswordChangeRequest;
import com.naengo.api_server.domain.user.dto.UserMeResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesUpdateRequest;
import com.naengo.api_server.domain.user.dto.UserUpdateRequest;
import com.naengo.api_server.domain.user.service.UserMeService;
import com.naengo.api_server.global.auth.AuthCookieFactory;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserMeController {

    private final UserMeService userMeService;
    private final AuthCookieFactory authCookieFactory;

    /** 본인 마이페이지 조회 (`SPEC-20260503-04`). */
    @GetMapping
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.ok(userMeService.getMe(currentUserId())));
    }

    /** 본인 닉네임 수정 (`SPEC-20260503-05`). */
    @PatchMapping
    public ResponseEntity<ApiResponse<UserMeResponse>> updateMe(
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userMeService.updateMe(currentUserId(), request)));
    }

    /** 비밀번호 변경 (`SPEC-20260503-06`). LOCAL provider 한정. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userMeService.changePassword(currentUserId(), request);
        return ResponseEntity.noContent().build();
    }

    /** 회원 탈퇴 (익명화, `SPEC-20260503-07`). 쿠키 동시 만료. */
    @DeleteMapping
    public ResponseEntity<Void> withdraw() {
        userMeService.withdraw(currentUserId());
        ResponseCookie expired = authCookieFactory.expire();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .build();
    }

    /** 선호도 조회 (`SPEC-20260504-04`). */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> getPreferences() {
        return ResponseEntity.ok(ApiResponse.ok(userMeService.getPreferences(currentUserId())));
    }

    /** 선호도 갱신 — 직접 입력 영역만 (`SPEC-20260504-05`). */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> updatePreferences(
            @Valid @RequestBody UserPreferencesUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userMeService.updatePreferences(currentUserId(), request)));
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
