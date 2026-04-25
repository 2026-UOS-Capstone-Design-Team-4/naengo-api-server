package com.naengo.api_server.domain.recipe.controller;

import com.naengo.api_server.domain.recipe.dto.RecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.RecipeCreateResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeDetailResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.service.RecipeService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeCreateResponse>> create(
            @Valid @RequestBody RecipeCreateRequest request) {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);

        RecipeCreateResponse response = recipeService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RecipeListResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "latest") String sort) {
        RecipeListResponse response = recipeService.listApproved(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<RecipeListResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RecipeListResponse response = recipeService.listMine(page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RecipeDetailResponse>> detail(@PathVariable Long id) {
        RecipeDetailResponse response = recipeService.detail(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recipeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
