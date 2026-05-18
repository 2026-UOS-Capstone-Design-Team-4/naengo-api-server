package com.naengo.api_server.domain.admin.controller;

import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeDetailResponse;
import com.naengo.api_server.domain.admin.dto.AdminPendingRecipeListResponse;
import com.naengo.api_server.domain.admin.dto.PendingRecipeAdminUpdateRequest;
import com.naengo.api_server.domain.admin.service.AdminRecipeService;
import com.naengo.api_server.domain.recipe.dto.PendingRecipeResponse;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 — 제출 레시피 검토 / 단일 PATCH 수정·승인·반려.
 *
 * <p>api-3.json: {@code PATCH /api/v1/admin/pending-recipes/{id}}.
 * list / detail GET 은 관리 콘솔 편의 확장 (api-3.json 외, 유지).
 */
@RestController
@RequestMapping("/api/v1/admin/pending-recipes")
@RequiredArgsConstructor
public class AdminRecipeController {

    private final AdminRecipeService adminRecipeService;

    /** 검토 목록 (status 필터, 기본 PENDING). */
    @GetMapping
    public AdminPendingRecipeListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminRecipeService.list(parseStatus(status), page, size);
    }

    /** 단건 상세. */
    @GetMapping("/{id}")
    public AdminPendingRecipeDetailResponse detail(@PathVariable Long id) {
        return adminRecipeService.detail(id);
    }

    /** 단일 PATCH — 콘텐츠 수정 + 상태 전이(승인 승격/반려) 통합. */
    @PatchMapping("/{id}")
    public PendingRecipeResponse update(
            @PathVariable Long id,
            @Valid @RequestBody PendingRecipeAdminUpdateRequest request) {
        return adminRecipeService.update(id, request);
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
