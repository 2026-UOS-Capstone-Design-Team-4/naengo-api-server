package com.naengo.api_server.domain.scrap.controller;

import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.scrap.dto.ScrapToggleResponse;
import com.naengo.api_server.domain.scrap.service.ScrapService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    /** 스크랩 토글 — 좋아요와 같은 패턴. */
    @PostMapping("/api/recipes/{id}/scrap")
    public ResponseEntity<ApiResponse<ScrapToggleResponse>> toggle(@PathVariable Long id) {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);

        return ResponseEntity.ok(ApiResponse.ok(scrapService.toggle(userId, id)));
    }

    /** 본인 스크랩 목록 — 활성 레시피만, 스크랩 시각 내림차순. */
    @GetMapping("/api/scraps/my")
    public ResponseEntity<ApiResponse<RecipeListResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(scrapService.listMine(page, size)));
    }
}
