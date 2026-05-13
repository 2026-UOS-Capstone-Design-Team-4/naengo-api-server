package com.naengo.api_server.domain.like.controller;

import com.naengo.api_server.domain.like.dto.LikeToggleResponse;
import com.naengo.api_server.domain.like.service.LikeService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<LikeToggleResponse>> toggle(@PathVariable Long id) {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);

        return ResponseEntity.ok(ApiResponse.ok(likeService.toggle(userId, id)));
    }
}
