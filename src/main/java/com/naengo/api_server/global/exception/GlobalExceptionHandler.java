package com.naengo.api_server.global.exception;

import com.naengo.api_server.global.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모든 예외를 표준 에러 응답({@link ErrorResponse}) 으로 변환한다.
 * 표준 shape: {@code {"error":{"code","message","details"}}}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("[CustomException] {} - {}", ec.getCode(), e.getMessage());
        return ResponseEntity
                .status(ec.getStatus())
                .body(ErrorResponse.of(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        List<Map<String, String>> fields = e.getBindingResult().getFieldErrors().stream()
                .map(this::fieldErrorToMap)
                .toList();
        Map<String, Object> details = Map.of("fields", fields);
        log.warn("[ValidationException] {}", fields);
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INVALID_INPUT.getCode(),
                        "요청 값이 유효하지 않습니다.",
                        details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("[AccessDeniedException] {}", e.getMessage());
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getStatus())
                .body(ErrorResponse.of(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private Map<String, String> fieldErrorToMap(FieldError fe) {
        // 순서 보장 LinkedHashMap — 응답 로그/디버깅 일관성.
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", fe.getField());
        entry.put("reason", fe.getDefaultMessage());
        return entry;
    }
}
