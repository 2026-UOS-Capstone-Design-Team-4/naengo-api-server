package com.naengo.api_server.domain.recipe.support;

import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recipe → RecipeListItemResponse 매핑 (api-3.json 정합 — 전체 레시피 필드 + engagement).
 *
 * <p>작성자 닉네임은 일괄 조회로 N+1 방지. {@code is_liked}/{@code is_scrapped} 는
 * 호출부가 현재 사용자 기준으로 미리 계산한 id 집합을 넘긴다 (비로그인이면 빈 집합).
 */
@Component
@RequiredArgsConstructor
public class RecipeListMapper {

    private final UserRepository userRepository;

    /** 사용자 컨텍스트 없음 (ChatService 등). engagement flag 는 모두 false. */
    public List<RecipeListItemResponse> toItems(List<Recipe> recipes) {
        return toItems(recipes, Set.of(), Set.of());
    }

    /** 사용자 컨텍스트 포함. likedIds / scrappedIds 는 현재 사용자 기준. */
    public List<RecipeListItemResponse> toItems(List<Recipe> recipes,
                                                Set<Long> likedIds,
                                                Set<Long> scrappedIds) {
        if (recipes.isEmpty()) return List.of();

        Map<Long, String> nicknameMap = loadNicknames(recipes);

        return recipes.stream()
                .map(r -> toItem(r, nicknameMap, likedIds, scrappedIds))
                .toList();
    }

    /** 단건 매핑 (RecipeService.detail). */
    public RecipeListItemResponse toItem(Recipe r, boolean liked, boolean scrapped) {
        Map<Long, String> nicknameMap = loadNicknames(List.of(r));
        return toItem(r, nicknameMap,
                liked ? Set.of(r.getRecipeId()) : Set.of(),
                scrapped ? Set.of(r.getRecipeId()) : Set.of());
    }

    // ─── 내부 ─────────────────────────────────────────────

    private RecipeListItemResponse toItem(Recipe r,
                                          Map<Long, String> nicknameMap,
                                          Set<Long> likedIds,
                                          Set<Long> scrappedIds) {
        RecipeStats s = r.getStats();
        int likes = s == null ? 0 : s.getLikesCount();
        int scraps = s == null ? 0 : s.getScrapCount();
        String rawNickname = r.getAuthorId() == null ? null : nicknameMap.get(r.getAuthorId());
        return new RecipeListItemResponse(
                r.getRecipeId(),
                r.getTitle(),
                r.getDescription(),
                r.getIngredients(),
                r.getIngredientsRaw(),
                r.getInstructions(),
                r.getServings(),
                r.getCookingTime(),
                r.getCalories(),
                r.getDifficulty(),
                r.getCategory(),
                r.getTags(),
                r.getTips(),
                r.getVideoUrl(),
                r.getImageUrl(),
                r.getAuthorType(),
                AuthorDisplayName.of(rawNickname),
                r.getCreatedAt(),
                likes,
                scraps,
                likedIds.contains(r.getRecipeId()),
                scrappedIds.contains(r.getRecipeId())
        );
    }

    private Map<Long, String> loadNicknames(List<Recipe> recipes) {
        List<Long> authorIds = recipes.stream()
                .map(Recipe::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> nicknameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            userRepository.findAllById(authorIds)
                    .forEach(u -> nicknameMap.put(u.getUserId(), u.getNickname()));
        }
        return nicknameMap;
    }
}
