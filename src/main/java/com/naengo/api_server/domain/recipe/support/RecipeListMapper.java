package com.naengo.api_server.domain.recipe.support;

import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.dto.RecipeListResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStats;
import com.naengo.api_server.domain.user.repository.UserRepository;
import com.naengo.api_server.domain.user.support.AuthorDisplayName;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Recipe → RecipeListItemResponse 매핑.
 * 작성자 닉네임은 일괄 조회로 N+1 방지.
 *
 * <p>두 형태 노출:
 * <ul>
 *   <li>{@link #toResponse(Page)} — Page&lt;Recipe&gt; → 페이징 응답 (RecipeService.listApproved / ScrapService.listMine)</li>
 *   <li>{@link #toItems(List)} — List&lt;Recipe&gt; → 단순 리스트 (ChatService.listMessages 의 메시지별 추천 매핑)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RecipeListMapper {

    private final UserRepository userRepository;

    public RecipeListResponse toResponse(Page<Recipe> page) {
        List<RecipeListItemResponse> items = toItems(page.getContent());
        return new RecipeListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    public List<RecipeListItemResponse> toItems(List<Recipe> recipes) {
        if (recipes.isEmpty()) return List.of();

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

        return recipes.stream().map(r -> {
            RecipeStats s = r.getStats();
            int likes = s == null ? 0 : s.getLikesCount();
            int scraps = s == null ? 0 : s.getScrapCount();
            String raw = r.getAuthorId() == null ? null : nicknameMap.get(r.getAuthorId());
            return new RecipeListItemResponse(
                    r.getRecipeId(),
                    r.getTitle(),
                    r.getDescription(),
                    r.getImageUrl(),
                    AuthorDisplayName.of(raw),
                    r.getAuthorType(),
                    r.getDifficulty(),
                    r.getCookingTime(),
                    likes,
                    scraps,
                    r.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }
}
