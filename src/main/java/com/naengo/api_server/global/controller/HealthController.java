package com.naengo.api_server.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 헬스체크. 두 경로 모두 200 + {@code {"status":"UP"}} 반환:
 * <ul>
 *   <li>{@code GET /health} — 기존 (CorsIntegrationTest 등 내부)</li>
 *   <li>{@code GET /}       — api-3.json PR-8, LB target group / 외부 모니터링용</li>
 * </ul>
 * 둘 다 {@code permitAll} (SecurityConfig). DB/외부 의존 검사는 하지 않음 (liveness 만).
 */
@RestController
public class HealthController {

    @GetMapping({"/", "/health"})
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
