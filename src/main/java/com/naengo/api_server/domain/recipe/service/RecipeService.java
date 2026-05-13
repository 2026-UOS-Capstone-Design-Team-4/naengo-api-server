package com.naengo.api_server.domain.recipe.service;

import com.naengo.api_server.domain.recipe.dto.PendingRecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.PendingRecipeListResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.RecipeCreateResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeDetailResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.entity.PendingRecipe;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.recipe.repository.PendingRecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RecipeRepository recipeRepository;
    private final PendingRecipeRepository pendingRecipeRepository;
    private final UserRepository userRepository;
    private final RecipeListMapper recipeListMapper;

    @Value("${aws.s3.public-url-prefix:}")
    private String s3PublicUrlPrefix;

    /**
     * 사용자 레시피 제출 → pending_recipes 테이블에 INSERT.
     * 승인되어 recipes 로 옮겨지는 흐름은 Step 6 (Admin) 책임.
     */
    @Transactional
    public RecipeCreateResponse create(Long userId, RecipeCreateRequest request) {
        validateImageUrl(request.imageUrl());

        PendingRecipe pending = PendingRecipe.builder()
                .userId(userId)
                .title(request.title())
                .description(request.description())
                .content(request.content())
                .ingredients(request.ingredients())
                .ingredientsRaw(request.ingredientsRaw())
                .instructions(request.instructions())
                .servings(request.servings())
                .cookingTime(request.cookingTime())
                .calories(request.calories())
                .difficulty(request.difficulty())
                .category(request.category())
                .tags(request.tags())
                .tips(request.tips())
                .videoUrl(request.videoUrl())
                .imageUrl(request.imageUrl())
                .build();

        PendingRecipe saved = pendingRecipeRepository.save(pending);
        return new RecipeCreateResponse(saved.getPendingRecipeId(), saved.getStatus());
    }

    /**
     * 공개 목록: recipes 테이블의 is_active=true 만.
     */
    @Transactional(readOnly = true)
    public RecipeListResponse listApproved(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<Recipe> result = switch (sort == null ? "latest" : sort) {
            case "popular" -> recipeRepository.findActiveOrderByPopular(pageable);
            case "latest"  -> recipeRepository.findActiveOrderByLatest(pageable);
            default        -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
        return recipeListMapper.toResponse(result);
    }

    /**
     * 내가 제출한 레시피 (pending_recipes 의 모든 상태).
     */
    @Transactional(readOnly = true)
    public PendingRecipeListResponse listMine(int page, int size) {
        Long userId = requireCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<PendingRecipe> result =
                pendingRecipeRepository.findActiveByUserOrderByLatest(userId, pageable);

        List<PendingRecipeListItemResponse> items = result.getContent().stream()
                .map(p -> new PendingRecipeListItemResponse(
                        p.getPendingRecipeId(),
                        p.getTitle(),
                        p.getDescription(),
                        p.getImageUrl(),
                        p.getStatus(),
                        p.getAdminNote(),
                        p.getReviewedAt(),
                        p.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return new PendingRecipeListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /**
     * 단건 조회: recipes 테이블에서만 (승인된 것만 노출).
     * 본인의 pending 은 listMine() 에서 확인.
     */
    @Transactional(readOnly = true)
    public RecipeDetailResponse detail(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));

        if (!recipe.isActive()) {
            throw new CustomException(ErrorCode.RECIPE_NOT_FOUND);
        }

        String nickname = resolveNickname(recipe.getAuthorId());
        RecipeStats stats = recipe.getStats();
        int likes = stats == null ? 0 : stats.getLikesCount();
        int scraps = stats == null ? 0 : stats.getScrapCount();

        return new RecipeDetailResponse(
                recipe.getRecipeId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getIngredients(),
                recipe.getIngredientsRaw(),
                recipe.getInstructions(),
                recipe.getServings(),
                recipe.getCookingTime(),
                recipe.getCalories(),
                recipe.getDifficulty(),
                recipe.getCategory(),
                recipe.getTags(),
                recipe.getTips(),
                recipe.getContent(),
                recipe.getVideoUrl(),
                recipe.getImageUrl(),
                recipe.getAuthorType(),
                recipe.getAuthorId(),
                nickname,
                likes,
                scraps,
                recipe.getCreatedAt()
        );
    }

    /**
     * 본인 제출 레시피 삭제 (pending_recipes hard delete).
     * 승인된 recipes 의 삭제는 별도 admin endpoint (Step 6) 책임.
     */
    @Transactional
    public void delete(Long pendingRecipeId) {
        Long userId = requireCurrentUserId();
        PendingRecipe pending = pendingRecipeRepository.findById(pendingRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));
        if (!userId.equals(pending.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        pendingRecipeRepository.delete(pending);
    }

    // ─── 내부 유틸 ─────────────────────────────────────────

    private String resolveNickname(Long authorId) {
        if (authorId == null) return null;
        return userRepository.findById(authorId)
                .map(User::getNickname)
                .map(AuthorDisplayName::of)
                .orElse(null);
    }

    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        if (s3PublicUrlPrefix == null || s3PublicUrlPrefix.isBlank()) return; // 로컬/AWS 미설정
        if (!imageUrl.startsWith(s3PublicUrlPrefix)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
