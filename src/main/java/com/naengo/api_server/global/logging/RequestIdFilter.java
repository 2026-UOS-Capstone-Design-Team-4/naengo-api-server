package com.naengo.api_server.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 고유 ID 부여 + MDC 에 put → 로그 라인이 같은 요청끼리 상관 가능하게.
 * 클라이언트가 {@code X-Request-Id} 헤더로 보낸 값이 있으면 그대로 사용 (분산 추적 호환),
 * 없으면 UUID 생성. 응답에도 같은 ID 를 헤더로 반환.
 *
 * <p>{@link Ordered#HIGHEST_PRECEDENCE} 로 등록되어 Spring Security 의 FilterChainProxy
 * 보다 먼저 실행 → 인증 처리 / 컨트롤러 / 예외 핸들러까지 모든 로그 라인이 requestId 를 가진다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // userId 도 같이 정리 (JwtAuthenticationFilter 가 인증 성공 시 추가했더라도)
            MDC.clear();
        }
    }
}
