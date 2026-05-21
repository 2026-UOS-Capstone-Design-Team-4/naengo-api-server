package com.naengo.api_server.global.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {}

    /** 현재 로그인된 사용자의 userId. 비로그인이면 null. (옵션 A — DBv5 SERIAL PK 정합으로 Integer) */
    public static Integer currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 현재 사용자가 해당 role 을 가졌는가. 비로그인이면 false. */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        String target = "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(a -> target.equals(a.getAuthority()));
    }
}
