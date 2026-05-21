package com.naengo.api_server.domain.recipe.controller;

import com.naengo.api_server.domain.recipe.dto.UserRecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.UserRecipeResponse;
import com.naengo.api_server.domain.recipe.service.UserRecipeService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 사용자 제출 레시피 — api-3.json `/api/v1/user-recipes`.
 *
 * <ul>
 *   <li>{@code POST} — 제출 (201)</li>
 *   <li>{@code GET} — 본인 제출 목록 (단순 배열, is_active=true, 최신순)</li>
 *   <li>{@code GET /{id}} — 본인 제출 단건</li>
 *   <li>{@code DELETE /{id}} — soft delete (is_active=false)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/user-recipes")
@RequiredArgsConstructor
public class UserRecipeController {

    private final UserRecipeService userRecipeService;

    @PostMapping
    public ResponseEntity<UserRecipeResponse> create(
            @Valid @RequestBody UserRecipeCreateRequest request) {
        UserRecipeResponse response = userRecipeService.create(currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<UserRecipeResponse> listMine() {
        return userRecipeService.listMine(currentUserId());
    }

    @GetMapping("/{id}")
    public UserRecipeResponse getMine(@PathVariable Integer id) {
        return userRecipeService.getMine(currentUserId(), id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Integer id) {
        userRecipeService.softDelete(currentUserId(), id);
        return Map.of("message", "레시피가 삭제되었습니다.");
    }

    private Integer currentUserId() {
        Integer userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
