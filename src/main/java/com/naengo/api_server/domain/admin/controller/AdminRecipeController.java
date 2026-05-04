package com.naengo.api_server.domain.admin.controller;

import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeDetailResponse;
import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeListResponse;
import com.naengo.api_server.domain.admin.dto.RecipeApprovalRequest;
import com.naengo.api_server.domain.admin.dto.RecipeApprovalResponse;
import com.naengo.api_server.domain.admin.dto.RecipeRejectionRequest;
import com.naengo.api_server.domain.admin.dto.RecipeRejectionResponse;
import com.naengo.api_server.domain.admin.service.AdminRecipeService;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/pending-recipes")
@RequiredArgsConstructor
public class AdminRecipeController {

    private final AdminRecipeService adminRecipeService;

    /** SPEC-20260504-01 — 검토 대기 목록 (status 필터, 기본 PENDING). */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPendingRecipeListResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RecipeStatus parsed = parseStatus(status);
        return ResponseEntity.ok(ApiResponse.ok(adminRecipeService.list(parsed, page, size)));
    }

    /** SPEC-20260504-01 — 단건 상세. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminPendingRecipeDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(adminRecipeService.detail(id)));
    }

    /** SPEC-20260504-02 — 승인 → recipes 로 INSERT + pending status=APPROVED. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RecipeApprovalResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RecipeApprovalRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminRecipeService.approve(id, request)));
    }

    /** SPEC-20260504-02 — 반려 → pending status=REJECTED + 사유 기록. */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RecipeRejectionResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRejectionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adminRecipeService.reject(id, request)));
    }

    private RecipeStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return RecipeStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
