package com.naengo.api_server.global.config;

import com.naengo.api_server.global.auth.JwtAccessDeniedHandler;
import com.naengo.api_server.global.auth.JwtAuthenticationEntryPoint;
import com.naengo.api_server.global.auth.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 사용 가능하게
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // REST API → CSRF 불필요 (SameSite=Lax 가 1차 방어)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWT → 세션 사용 안 함
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 미인증 → 401 + ApiResponse
                        .accessDeniedHandler(jwtAccessDeniedHandler))          // 미인가 → 403 + ApiResponse
                .authorizeHttpRequests(auth -> auth

                        // ── 인증 없이 접근 가능 ──────────────────────────
                        .requestMatchers("/health").permitAll()                           // 헬스체크
                        .requestMatchers("/api/auth/**").permitAll()                      // 회원가입, 로그인
                        // `/api/recipes/my` 는 인증 필수 (아래 permitAll 규칙보다 먼저 매칭)
                        .requestMatchers(HttpMethod.GET, "/api/recipes/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/recipes/**").permitAll()  // 레시피 조회(공개)
                        .requestMatchers(HttpMethod.POST, "/api/chat/**").permitAll()    // 비로그인 채팅
                        .requestMatchers("/oauth/**").permitAll()                         // 개발용 OAuth 콜백

                        // ── 관리자 전용 ───────────────────────────────────
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // ── 그 외는 로그인 필요 ───────────────────────────
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 설정. {@code allowCredentials=true} 가 고정 정책 (쿠키 인증 통로 호환).
     * 따라서 origin 와일드카드 사용 불가 → `cors.allowed-origins` 에 명시적 목록 주입.
     * 응답에 {@code Set-Cookie} 가 노출되도록 exposed-headers 에 포함.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(corsProperties.allowedOrigins());
        cors.setAllowedMethods(corsProperties.allowedMethods());
        cors.setAllowedHeaders(corsProperties.allowedHeaders());
        cors.setExposedHeaders(corsProperties.exposedHeaders());
        cors.setAllowCredentials(true);
        cors.setMaxAge(corsProperties.maxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
