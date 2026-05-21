package com.naengo.api_server.domain.recipe.support;

import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeIngredient;
import com.naengo.api_server.domain.recipe.entity.RecipeLabel;
import com.naengo.api_server.domain.recipe.entity.RecipeMedia;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.recipe.entity.RecipeStep;
import com.naengo.api_server.domain.recipe.repository.RecipeIngredientRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeLabelRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeMediaRepository;
import com.naengo.api_server.domain.recipe.repository.RecipeStepRepository;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recipe(정규화) → RecipeListItemResponse(api-3.json 평면) 매핑.
 *
 * <p>재료/조리/라벨/미디어는 분리 테이블이라 페이지 단위로 일괄 조회 후
 * recipeId 로 그룹핑하여 평면 조립한다 (N+1 / bag fetch 회피).
 * {@code is_liked}/{@code is_scrapped} 는 호출부가 현재 사용자 기준으로
 * 미리 계산한 id 집합을 넘긴다 (비로그인이면 빈 집합).
 */
@Component
@RequiredArgsConstructor
public class RecipeListMapper {

    private final UserRepository userRepository;
    private final RecipeIngredientRepository ingredientRepository;
    private final RecipeStepRepository stepRepository;
    private final RecipeLabelRepository labelRepository;
    private final RecipeMediaRepository mediaRepository;

    /** 사용자 컨텍스트 없음 (ChatService 등). engagement flag 는 모두 false. */
    public List<RecipeListItemResponse> toItems(List<Recipe> recipes) {
        return toItems(recipes, Set.of(), Set.of());
    }

    /** 사용자 컨텍스트 포함. likedIds / scrappedIds 는 현재 사용자 기준. */
    public List<RecipeListItemResponse> toItems(List<Recipe> recipes,
                                                Set<Integer> likedIds,
                                                Set<Integer> scrappedIds) {
        if (recipes.isEmpty()) return List.of();

        List<Integer> recipeIds = recipes.stream().map(Recipe::getRecipeId).toList();
        Map<Integer, String> nicknameMap = loadNicknames(recipes);
        Children c = loadChildren(recipeIds);

        List<RecipeListItemResponse> out = new ArrayList<>(recipes.size());
        for (Recipe r : recipes) {
            out.add(toItem(r, nicknameMap, c, likedIds, scrappedIds));
        }
        return out;
    }

    /** 단건 매핑 (RecipeService.detail / AdminRecipeService.findByVideoUrl). */
    public RecipeListItemResponse toItem(Recipe r, boolean liked, boolean scrapped) {
        Map<Integer, String> nicknameMap = loadNicknames(List.of(r));
        Children c = loadChildren(List.of(r.getRecipeId()));
        return toItem(r, nicknameMap, c,
                liked ? Set.of(r.getRecipeId()) : Set.of(),
                scrapped ? Set.of(r.getRecipeId()) : Set.of());
    }

    // ─── 내부 ─────────────────────────────────────────────

    private RecipeListItemResponse toItem(Recipe r,
                                          Map<Integer, String> nicknameMap,
                                          Children c,
                                          Set<Integer> likedIds,
                                          Set<Integer> scrappedIds) {
        Integer id = r.getRecipeId();
        RecipeStats s = r.getStats();
        int likes = s == null ? 0 : s.getLikesCount();
        int scraps = s == null ? 0 : s.getScrapCount();
        String rawNickname = r.getAuthorId() == null ? null : nicknameMap.get(r.getAuthorId());

        List<RecipeIngredient> ings = c.ingredients.getOrDefault(id, List.of());
        List<RecipeStep> steps = c.steps.getOrDefault(id, List.of());
        List<RecipeLabel> labels = c.labels.getOrDefault(id, List.of());
        List<RecipeMedia> media = c.media.getOrDefault(id, List.of());

        return new RecipeListItemResponse(
                id,
                r.getTitle(),
                r.getDescription(),
                RecipeNormalizer.toIngredientDtos(ings),
                RecipeNormalizer.toIngredientsRaw(ings),
                RecipeNormalizer.toInstructions(steps),
                r.getServings(),
                r.getCookingTimeMinutes(),
                r.getKcalPerServing(),
                r.getDifficulty(),
                RecipeNormalizer.labelValues(labels, RecipeLabel.TYPE_CATEGORY),
                RecipeNormalizer.labelValues(labels, RecipeLabel.TYPE_TAG),
                RecipeNormalizer.labelValues(labels, RecipeLabel.TYPE_TIP),
                RecipeNormalizer.videoUrl(media),
                RecipeNormalizer.primaryImageUrl(media),
                r.getAuthorType(),
                AuthorDisplayName.of(rawNickname),
                r.getCreatedAt(),
                likes,
                scraps,
                likedIds.contains(id),
                scrappedIds.contains(id)
        );
    }

    private Children loadChildren(List<Integer> recipeIds) {
        if (recipeIds.isEmpty()) {
            return new Children(Map.of(), Map.of(), Map.of(), Map.of());
        }
        return new Children(
                groupByRecipe(ingredientRepository.findByRecipeIds(recipeIds),
                        i -> i.getRecipe().getRecipeId()),
                groupByRecipe(stepRepository.findByRecipeIds(recipeIds),
                        st -> st.getRecipe().getRecipeId()),
                groupByRecipe(labelRepository.findByRecipeIds(recipeIds),
                        l -> l.getRecipe().getRecipeId()),
                groupByRecipe(mediaRepository.findByRecipeIds(recipeIds),
                        m -> m.getRecipe().getRecipeId()));
    }

    private <T> Map<Integer, List<T>> groupByRecipe(List<T> rows,
                                                 java.util.function.Function<T, Integer> keyFn) {
        Map<Integer, List<T>> map = new HashMap<>();
        for (T row : rows) {
            map.computeIfAbsent(keyFn.apply(row), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private Map<Integer, String> loadNicknames(List<Recipe> recipes) {
        List<Integer> authorIds = recipes.stream()
                .map(Recipe::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, String> nicknameMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            userRepository.findAllById(authorIds)
                    .forEach(u -> nicknameMap.put(u.getUserId(), u.getNickname()));
        }
        return nicknameMap;
    }

    private record Children(Map<Integer, List<RecipeIngredient>> ingredients,
                            Map<Integer, List<RecipeStep>> steps,
                            Map<Integer, List<RecipeLabel>> labels,
                            Map<Integer, List<RecipeMedia>> media) {
    }
}
