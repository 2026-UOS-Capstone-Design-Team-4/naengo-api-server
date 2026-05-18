package com.naengo.api_server.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 표준 에러 응답 — naengo-ai {@code docs/architecture/api/05-error-response.md} 형식.
 *
 * <pre>{@code
 * {
 *   "error": {
 *     "code": "RECIPE_NOT_FOUND",
 *     "message": "레시피를 찾을 수 없습니다.",
 *     "details": { ... }
 *   }
 * }
 * }</pre>
 *
 * <p>{@code details} 가 {@code null} 이거나 비어 있으면 직렬화에서 제외된다.
 */
public record ErrorResponse(Body error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Body(code, message, null));
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new Body(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Body(String code, String message, Map<String, Object> details) {
    }
}
