package com.naengo.api_server.domain.admin.controller;

import com.naengo.api_server.domain.admin.dto.AdminUserBlockResponse;
import com.naengo.api_server.domain.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** SPEC-20260504-03 — 사용자 차단. */
    @PostMapping("/{userId}/block")
    public AdminUserBlockResponse block(@PathVariable Integer userId) {
        return adminUserService.block(userId);
    }

    /** SPEC-20260504-03 — 차단 해제. */
    @PostMapping("/{userId}/unblock")
    public AdminUserBlockResponse unblock(@PathVariable Integer userId) {
        return adminUserService.unblock(userId);
    }
}
