package com.naengo.api_server.domain.scrap.controller;

import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeStatsResponse;
import com.naengo.api_server.domain.scrap.service.ScrapService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 레시피 스크랩 — api-3.json `/api/v1/recipes/{id}/scraps` (POST 추가 / DELETE 취소)
 * + 본인 스크랩 목록 `GET /api/v1/users/me/scraps` (커서).
 */
@RestController
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    @PostMapping("/api/v1/recipes/{id}/scraps")
    public RecipeStatsResponse scrap(@PathVariable Long id) {
        return scrapService.scrap(currentUserId(), id);
    }

    @DeleteMapping("/api/v1/recipes/{id}/scraps")
    public RecipeStatsResponse unscrap(@PathVariable Long id) {
        return scrapService.unscrap(currentUserId(), id);
    }

    /** 본인 스크랩 목록 — 커서 페이지네이션, 활성 레시피만, 스크랩 시각 내림차순. */
    @GetMapping("/api/v1/users/me/scraps")
    public RecipeListResponse listMine(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return scrapService.listMine(cursor, limit);
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
