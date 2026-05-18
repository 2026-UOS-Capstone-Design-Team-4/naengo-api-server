package com.naengo.api_server.domain.admin.controller;

import com.naengo.api_server.domain.admin.service.AdminRecipeService;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 — 정식 레시피 조회. api-3.json {@code GET /api/v1/admin/recipes?video_url=}.
 * 레시피 등록 전 중복 여부 확인용. 없으면 404.
 */
@RestController
@RequestMapping("/api/v1/admin/recipes")
@RequiredArgsConstructor
public class AdminRecipeLookupController {

    private final AdminRecipeService adminRecipeService;

    @GetMapping
    public RecipeListItemResponse getByVideoUrl(@RequestParam("video_url") String videoUrl) {
        return adminRecipeService.findByVideoUrl(videoUrl);
    }
}
