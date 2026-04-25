package com.naengo.api_server.domain.recipe.service;

import com.naengo.api_server.domain.recipe.dto.RecipeCreateRequest;
import com.naengo.api_server.domain.recipe.dto.RecipeCreateResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeDetailResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeSource;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeService {

    private static final int MAX_PAGE_SIZE = 50;

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Value("${aws.s3.public-url-prefix:}")
    private String s3PublicUrlPrefix;

    @Transactional
    public RecipeCreateResponse create(Long authorId, RecipeCreateRequest request) {
        validateImageUrl(request.imageUrl());

        Recipe recipe = Recipe.builder()
                .title(request.title())
                .fullContent(request.fullContent())
                .imageUrl(request.imageUrl())
                .source(RecipeSource.USER)
                .status(RecipeStatus.PENDING)
                .authorId(authorId)
                .ingredients(request.ingredients())
                .build();
        Recipe saved = recipeRepository.save(recipe);

        // recipe_stats 를 같은 트랜잭션에서 INSERT (0,0)
        entityManager.persist(new RecipeStats(saved));

        return new RecipeCreateResponse(saved.getRecipeId(), saved.getStatus());
    }

    @Transactional(readOnly = true)
    public RecipeListResponse listApproved(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<Recipe> result = switch (sort == null ? "latest" : sort) {
            case "popular" -> recipeRepository.findByStatusOrderByPopular(RecipeStatus.APPROVED, pageable);
            case "latest"  -> recipeRepository.findByStatusOrderByLatest(RecipeStatus.APPROVED, pageable);
            default        -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
        return toListResponse(result);
    }

    @Transactional(readOnly = true)
    public RecipeListResponse listMine(int page, int size) {
        Long userId = requireCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<Recipe> result = recipeRepository.findByAuthorIdOrderByLatest(userId, pageable);
        return toListResponse(result);
    }

    @Transactional(readOnly = true)
    public RecipeDetailResponse detail(Long recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));

        if (recipe.getStatus() != RecipeStatus.APPROVED) {
            Long me = SecurityUtil.currentUserIdOrNull();
            boolean isAuthor = me != null && me.equals(recipe.getAuthorId());
            boolean isAdmin = SecurityUtil.hasRole("ADMIN");
            if (!isAuthor && !isAdmin) {
                throw new CustomException(ErrorCode.RECIPE_NOT_APPROVED);
            }
        }

        String nickname = resolveNickname(recipe.getAuthorId());
        RecipeStats stats = recipe.getStats();
        int likes = stats == null ? 0 : stats.getLikesCount();
        int scraps = stats == null ? 0 : stats.getScrapCount();

        return new RecipeDetailResponse(
                recipe.getRecipeId(),
                recipe.getTitle(),
                recipe.getFullContent(),
                recipe.getImageUrl(),
                recipe.getSource(),
                recipe.getStatus(),
                recipe.getAuthorId(),
                nickname,
                recipe.getIngredients(),
                likes,
                scraps,
                recipe.getCreatedAt()
        );
    }

    @Transactional
    public void delete(Long recipeId) {
        Long userId = requireCurrentUserId();
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        if (!userId.equals(recipe.getAuthorId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // session_logs.selected_recipe_id 가 이 레시피를 가리키면 FK 충돌 → 먼저 NULL 처리
        entityManager.createNativeQuery(
                "UPDATE session_logs SET selected_recipe_id = NULL WHERE selected_recipe_id = :id"
        ).setParameter("id", recipeId).executeUpdate();

        recipeRepository.delete(recipe);
    }

    // ─── 내부 유틸 ─────────────────────────────────────────

    private RecipeListResponse toListResponse(Page<Recipe> page) {
        List<Recipe> content = page.getContent();

        // 작성자 닉네임 일괄 조회 (N+1 방지)
        List<Long> authorIds = content.stream()
                .map(Recipe::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> nicknameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            userRepository.findAllById(authorIds).forEach(u -> nicknameMap.put(u.getUserId(), u.getNickname()));
        }

        List<RecipeListItemResponse> items = content.stream().map(r -> {
            RecipeStats s = r.getStats();
            int likes = s == null ? 0 : s.getLikesCount();
            int scraps = s == null ? 0 : s.getScrapCount();
            String raw = r.getAuthorId() == null ? null : nicknameMap.get(r.getAuthorId());
            return new RecipeListItemResponse(
                    r.getRecipeId(),
                    r.getTitle(),
                    r.getImageUrl(),
                    AuthorDisplayName.of(raw),
                    r.getStatus(),
                    likes,
                    scraps,
                    r.getCreatedAt()
            );
        }).collect(Collectors.toList());

        return new RecipeListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

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
