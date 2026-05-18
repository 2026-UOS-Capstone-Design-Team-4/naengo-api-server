package com.naengo.api_server.domain.like.controller;

import com.naengo.api_server.domain.like.service.LikeService;
import com.naengo.api_server.domain.recipe.dto.RecipeStatsResponse;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레시피 좋아요 — api-3.json `/api/v1/recipes/{id}/likes` (POST 추가 / DELETE 취소).
 */
@RestController
@RequestMapping("/api/v1/recipes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{id}/likes")
    public RecipeStatsResponse like(@PathVariable Long id) {
        return likeService.like(currentUserId(), id);
    }

    @DeleteMapping("/{id}/likes")
    public RecipeStatsResponse unlike(@PathVariable Long id) {
        return likeService.unlike(currentUserId(), id);
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
