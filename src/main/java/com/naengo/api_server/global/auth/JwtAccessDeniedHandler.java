package com.naengo.api_server.global.auth;

import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인가 실패(=권한 부족 — 예: USER 가 ADMIN endpoint 호출) 시 403 + ApiResponse 일관 응답.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.FORBIDDEN.getMessage());
        JSON.writeValue(response.getOutputStream(), body);
    }
}
