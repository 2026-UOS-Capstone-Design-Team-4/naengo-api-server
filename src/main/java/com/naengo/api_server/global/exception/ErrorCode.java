package com.naengo.api_server.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인·공용 에러 정의. 응답의 {@code error.code} 로 직렬화된다.
 *
 * <p>4개 공용 HTTP 상태 (400/401/403/500) 의 code 는 naengo-ai 의
 * {@code docs/architecture/api/05-error-response.md} 표준어를 따라 명시적으로 설정한다.
 * 도메인 코드는 enum name() 을 그대로 사용한다 (예: {@code RECIPE_NOT_FOUND}).
 */
@Getter
public enum ErrorCode {

    // ─── Auth ───────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", "UNAUTHENTICATED"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.", "FORBIDDEN"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인 인증에 실패했습니다."),
    EMAIL_PROVIDER_CONFLICT(HttpStatus.CONFLICT, "해당 이메일로 이미 가입된 계정이 있습니다. 기존 로그인 방식을 이용해주세요."),

    // ─── User ────────────────────────────────────────────
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "차단된 사용자입니다."),
    SOCIAL_PASSWORD_NOT_ALLOWED(HttpStatus.FORBIDDEN, "소셜 로그인 사용자는 비밀번호를 변경할 수 없습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴된 사용자입니다."),

    // ─── Recipe ──────────────────────────────────────────
    RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 레시피입니다."),
    PENDING_RECIPE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 제출 레시피입니다."),
    PENDING_RECIPE_NOT_REVIEWABLE(HttpStatus.CONFLICT, "이미 승인 또는 반려된 레시피입니다."),
    PENDING_RECIPE_INCOMPLETE(HttpStatus.BAD_REQUEST, "승인에 필요한 필수 필드가 누락되어 있습니다."),

    // ─── Engagement (Like / Scrap) ───────────────────────
    ALREADY_LIKED(HttpStatus.CONFLICT, "이미 좋아요를 눌렀습니다."),
    NOT_LIKED(HttpStatus.CONFLICT, "좋아요를 누르지 않았습니다."),
    ALREADY_SCRAPPED(HttpStatus.CONFLICT, "이미 스크랩한 레시피입니다."),
    NOT_SCRAPPED(HttpStatus.CONFLICT, "스크랩하지 않은 레시피입니다."),

    // ─── Chat ────────────────────────────────────────────
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 채팅방입니다."),

    // ─── Common ──────────────────────────────────────────
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.", "VALIDATION_FAILED"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", "INTERNAL_ERROR");

    // 정책: ErrorCode 는 "사용되는 시점에 추가" 한다. 미리 선언해두지 않는다.

    private final HttpStatus status;
    private final String message;
    private final String code;

    ErrorCode(HttpStatus status, String message) {
        this(status, message, null);
    }

    ErrorCode(HttpStatus status, String message, String publicCode) {
        this.status = status;
        this.message = message;
        // null 이면 enum name() 을 그대로 사용. 도메인 코드의 기본 정책.
        this.code = publicCode != null ? publicCode : name();
    }
}
