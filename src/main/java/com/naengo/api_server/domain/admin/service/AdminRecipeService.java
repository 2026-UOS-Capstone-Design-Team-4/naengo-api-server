package com.naengo.api_server.domain.admin.service;

import com.naengo.api_server.domain.admin.dto.AdminUserRecipeDetailResponse;
import com.naengo.api_server.domain.admin.dto.AdminUserRecipeListItemResponse;
import com.naengo.api_server.domain.admin.dto.AdminUserRecipeListResponse;
import com.naengo.api_server.domain.admin.dto.UserRecipeAdminUpdateRequest;
import com.naengo.api_server.domain.recipe.dto.UserRecipeResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.entity.UserRecipe;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeAuthorType;
import com.naengo.api_server.domain.recipe.entity.RecipeIngredient;
import com.naengo.api_server.domain.recipe.entity.RecipeLabel;
import com.naengo.api_server.domain.recipe.entity.RecipeMedia;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import com.naengo.api_server.domain.recipe.entity.RecipeStep;
import com.naengo.api_server.domain.recipe.repository.UserRecipeRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeMediaRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.domain.recipe.support.RecipeNormalizer;
import com.naengo.api_server.domain.user.entity.User;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 관리자 — user_recipes 검토 / 수정 / 승인·반려 (단일 PATCH), recipes video_url 조회.
 *
 * <p>api-3.json `PATCH /api/v1/admin/user-recipes/{id}` 정합:
 * <ul>
 *   <li>전달하지 않은 필드는 변경하지 않는다 (PATCH 시맨틱).</li>
 *   <li>status 가 변경되면 reviewed_at = NOW.</li>
 *   <li>status=APPROVED 전이 시 정식 {@code recipes} 로 승격 (트리거가 stats 자동 생성).</li>
 *   <li>승인 필수 필드 부족 시 400 {@code PENDING_RECIPE_INCOMPLETE}.</li>
 * </ul>
 * <p>list / detail GET 은 api-3.json 외 우리 관리 콘솔 편의 확장 (유지).
 */
@Service
@RequiredArgsConstructor
public class AdminRecipeService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserRecipeRepository userRecipeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeMediaRepository recipeMediaRepository;
    private final UserRepository userRepository;
    private final RecipeListMapper recipeListMapper;

    @Transactional(readOnly = true)
    public AdminUserRecipeListResponse list(RecipeStatus status, int page, int size) {
        RecipeStatus filter = status == null ? RecipeStatus.PENDING : status;
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<UserRecipe> result = userRecipeRepository.findByStatusOrderByLatest(filter, pageable);

        Map<Integer, String> nicknameMap = loadNicknames(result.getContent().stream().map(UserRecipe::getUserId).toList());

        List<AdminUserRecipeListItemResponse> items = result.getContent().stream()
                .map(p -> new AdminUserRecipeListItemResponse(
                        p.getUserRecipeId(),
                        p.getUserId(),
                        AuthorDisplayName.of(nicknameMap.get(p.getUserId())),
                        p.getTitle(),
                        p.getDescription(),
                        p.getStatus(),
                        p.getAdminNote(),
                        p.getReviewedAt(),
                        p.getCreatedAt()
                ))
                .toList();

        return new AdminUserRecipeListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserRecipeDetailResponse detail(Integer userRecipeId) {
        UserRecipe p = userRecipeRepository.findById(userRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));

        String rawNickname = userRepository.findById(p.getUserId())
                .map(User::getNickname)
                .orElse(null);

        return new AdminUserRecipeDetailResponse(
                p.getUserRecipeId(),
                p.getUserId(),
                AuthorDisplayName.of(rawNickname),
                p.getTitle(),
                p.getDescription(),
                p.getContent(),
                p.getIngredients(),
                p.getIngredientsRaw(),
                p.getInstructions(),
                p.getServings(),
                p.getCookingTime(),
                p.getCalories(),
                p.getDifficulty(),
                p.getCategory(),
                p.getTags(),
                p.getTips(),
                p.getVideoUrl(),
                p.getImageUrl(),
                p.getStatus(),
                p.getAdminNote(),
                p.getReviewedAt(),
                p.getCreatedAt()
        );
    }

    /**
     * 단일 PATCH — 콘텐츠 부분 수정 + 상태 전이(승인 승격/반려) 통합.
     */
    @Transactional
    public UserRecipeResponse update(Integer userRecipeId, UserRecipeAdminUpdateRequest req) {
        UserRecipe p = userRecipeRepository.findById(userRecipeId)
                .orElseThrow(() -> new CustomException(ErrorCode.PENDING_RECIPE_NOT_FOUND));

        // 1) 콘텐츠 부분 수정 (null 은 보존)
        p.applyAdminPatch(
                req.title(), req.content(), req.description(),
                req.ingredients(), req.ingredientsRaw(), req.instructions(),
                req.servings(), req.cookingTime(), req.calories(), req.difficulty(),
                req.category(), req.tags(), req.tips(),
                req.videoUrl(), req.imageUrl());

        // 2) 상태 전이
        RecipeStatus newStatus = req.status();
        boolean statusChanged = newStatus != null && newStatus != p.getStatus();
        if (statusChanged) {
            if (newStatus == RecipeStatus.APPROVED) {
                ensureCompleteForApproval(p);   // 부족 시 400
                promoteToRecipe(p);             // recipes INSERT (트리거가 stats 생성)
            }
            p.changeStatus(newStatus);          // status + reviewed_at = NOW
        }

        // 3) admin_note 는 상태 전이와 독립적으로 반영
        p.setAdminNote(req.adminNote());

        return UserRecipeResponse.from(p);
    }

    /** video_url(recipe_media) 로 정식 레시피 단건 조회 (등록 전 중복 확인). 없으면 404. */
    @Transactional(readOnly = true)
    public RecipeListItemResponse findByVideoUrl(String videoUrl) {
        Recipe recipe = recipeMediaRepository
                .findRecipesByVideoUrl(videoUrl, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPE_NOT_FOUND));
        return recipeListMapper.toItem(recipe, false, false);
    }

    // ─── 내부 ────────────────────────────────────────

    private void promoteToRecipe(UserRecipe p) {
        Recipe recipe = Recipe.builder()
                .title(p.getTitle())
                .description(p.getDescription())
                .servings(p.getServings())
                .cookingTimeMinutes(p.getCookingTime())
                .kcalPerServing(p.getCalories())
                .difficulty(p.getDifficulty())
                .visibility("PUBLIC")
                .isActive(true)
                .authorType(RecipeAuthorType.USER)
                .authorId(p.getUserId())
                .classificationStatus("NOT_CLASSIFIED")
                .build();

        for (RecipeIngredient i : RecipeNormalizer.toIngredientRows(p.getIngredients())) {
            recipe.addIngredient(i);
        }
        for (RecipeStep s : RecipeNormalizer.toStepRows(p.getInstructions())) {
            recipe.addStep(s);
        }
        for (RecipeLabel l : RecipeNormalizer.toLabelRows(p.getCategory(), p.getTags(), p.getTips())) {
            recipe.addLabel(l);
        }
        for (RecipeMedia m : RecipeNormalizer.toMediaRows(p.getImageUrl(), p.getVideoUrl())) {
            recipe.addMedia(m);
        }

        Recipe saved = recipeRepository.save(recipe);
        p.markImported(saved.getRecipeId());
    }

    private void ensureCompleteForApproval(UserRecipe p) {
        if (isBlank(p.getTitle())
                || isBlank(p.getDescription())
                || isEmpty(p.getIngredients())
                || isBlank(p.getIngredientsRaw())
                || isEmpty(p.getInstructions())
                || p.getServings() == null
                || p.getCookingTime() == null
                || isBlank(p.getDifficulty())
                || isEmpty(p.getCategory())) {
            throw new CustomException(ErrorCode.PENDING_RECIPE_INCOMPLETE);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private Map<Integer, String> loadNicknames(List<Integer> userIds) {
        Map<Integer, String> map = new HashMap<>();
        List<Integer> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return map;
        userRepository.findAllById(distinct).forEach(u -> map.put(u.getUserId(), u.getNickname()));
        return map;
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
