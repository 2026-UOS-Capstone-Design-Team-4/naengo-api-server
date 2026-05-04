package com.naengo.api_server.domain.admin.service;

import com.naengo.api_server.domain.admin.dto.AdminUserBlockResponse;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional
    public AdminUserBlockResponse block(Long userId) {
        User user = loadActiveUser(userId);
        user.block();
        return new AdminUserBlockResponse(user.getUserId(), user.isBlocked());
    }

    @Transactional
    public AdminUserBlockResponse unblock(Long userId) {
        User user = loadActiveUser(userId);
        user.unblock();
        return new AdminUserBlockResponse(user.getUserId(), user.isBlocked());
    }

    private User loadActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (user.getDeletedAt() != null) {
            throw new CustomException(ErrorCode.ALREADY_WITHDRAWN);
        }
        return user;
    }
}
