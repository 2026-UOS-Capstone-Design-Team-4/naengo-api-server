package com.naengo.api_server.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // ─── Auth ───────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다."),
    EMAIL_PROVIDER_CONFLICT(HttpStatus.CONFLICT, "해당 이메일로 이미 가입된 계정이 있습니다. 기존 로그인 방식을 이용해주세요."),

    // ─── User ────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단된 사용자입니다."),

    // ─── Recipe ──────────────────────────────────────────
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 레시피입니다."),
    RECIPE_NOT_APPROVED(HttpStatus.FORBIDDEN, "승인되지 않은 레시피입니다."),

    // ─── Common ──────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    // 정책: ErrorCode 는 "사용되는 시점에 추가" 한다. 미리 선언해두지 않는다.
    //   - Like/Scrap (Step 3) 은 토글 설계이므로 ALREADY_* 에러가 필요 없다
    //   - Chat (Step 5) 의 NOT_FOUND 류는 해당 도메인 구현 시 추가

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
