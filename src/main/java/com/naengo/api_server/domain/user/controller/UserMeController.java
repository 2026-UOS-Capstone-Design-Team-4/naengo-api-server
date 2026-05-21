package com.naengo.api_server.domain.user.controller;

import com.naengo.api_server.domain.user.dto.PasswordChangeRequest;
import com.naengo.api_server.domain.user.dto.UserInputUpdateRequest;
import com.naengo.api_server.domain.user.dto.UserMeResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesResponse;
import com.naengo.api_server.domain.user.dto.UserPreferencesUpdateRequest;
import com.naengo.api_server.domain.user.dto.UserProfileResponse;
import com.naengo.api_server.domain.user.dto.UserUpdateRequest;
import com.naengo.api_server.domain.user.service.UserMeService;
import com.naengo.api_server.global.auth.AuthCookieFactory;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserMeController {

    private final UserMeService userMeService;
    private final AuthCookieFactory authCookieFactory;

    /** 본인 마이페이지 조회 (`SPEC-20260503-04`). */
    @GetMapping
    public UserMeResponse getMe() {
        return userMeService.getMe(currentUserId());
    }

    /** 본인 닉네임 수정 (`SPEC-20260503-05`). */
    @PatchMapping
    public UserMeResponse updateMe(@Valid @RequestBody UserUpdateRequest request) {
        return userMeService.updateMe(currentUserId(), request);
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

    /** 내 프로필 조회 — user_input 만 (api-3.json `GET /api/v1/users/me/profile`). */
    @GetMapping("/profile")
    public UserProfileResponse getProfile() {
        return userMeService.getProfile(currentUserId());
    }

    /** 내 프로필 수정 — user_input 전체 교체 (api-3.json `PATCH /api/v1/users/me/profile`). */
    @PatchMapping("/profile")
    public UserProfileResponse updateProfile(@Valid @RequestBody UserInputUpdateRequest request) {
        return userMeService.replaceUserInput(currentUserId(), request);
    }

    /** 확장 — AI 분석 포함 선호도 조회 (api-3.json 외, `GET /api/v1/users/me/preferences`). */
    @GetMapping("/preferences")
    public UserPreferencesResponse getPreferences() {
        return userMeService.getPreferences(currentUserId());
    }

    /** 확장 — 직접 입력 선호도(쿠킹스킬·조리시간·인분 등) 부분 갱신 (`PATCH /api/v1/users/me/preferences`). */
    @PatchMapping("/preferences")
    public UserPreferencesResponse updatePreferences(
            @Valid @RequestBody UserPreferencesUpdateRequest request) {
        return userMeService.updatePreferences(currentUserId(), request);
    }

    private Integer currentUserId() {
        Integer userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
