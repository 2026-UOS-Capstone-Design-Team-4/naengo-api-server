package com.naengo.api_server.domain.admin.controller;

import com.naengo.api_server.domain.admin.dto.AdminUserBlockResponse;
import com.naengo.api_server.domain.admin.service.AdminUserService;
import com.naengo.api_server.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** SPEC-20260504-03 — 사용자 차단. */
    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<AdminUserBlockResponse>> block(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.block(userId)));
    }

    /** SPEC-20260504-03 — 차단 해제. */
    @PostMapping("/{userId}/unblock")
    public ResponseEntity<ApiResponse<AdminUserBlockResponse>> unblock(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.unblock(userId)));
    }
}
