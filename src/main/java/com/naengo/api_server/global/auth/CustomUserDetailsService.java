package com.naengo.api_server.global.auth;

import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        // 옵션 A — DBv5 SERIAL PK 정합으로 Integer.parseInt. Spring Security 의
        // loadUserByUsername 인자명이 "username" 이지만 실제 값은 우리 userId 문자열.
        User user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isBlocked()) {
            throw new CustomException(ErrorCode.USER_BLOCKED);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(String.valueOf(user.getUserId()))
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .build();
    }
}
