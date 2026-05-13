package com.naengo.api_server.global.auth;

import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(=토큰 없음/만료/유효 X) 시 401 + ApiResponse 일관 응답.
 * Spring Security 의 기본 동작은 403 으로 떨어지지만 본 클래스를 등록하여 401 로 일관성 확보.
 *
 * <p>적용 후: SPEC 의 "토큰 없이 호출 → 401" 시나리오가 실 동작과 정합.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.UNAUTHORIZED.getMessage());
        JSON.writeValue(response.getOutputStream(), body);
    }
}
