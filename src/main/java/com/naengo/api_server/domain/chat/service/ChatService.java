package com.naengo.api_server.domain.chat.service;

import com.naengo.api_server.domain.chat.dto.ChatMessageListResponse;
import com.naengo.api_server.domain.chat.dto.ChatMessageResponse;
import com.naengo.api_server.domain.chat.dto.ChatRoomListItemResponse;
import com.naengo.api_server.domain.chat.dto.ChatRoomListResponse;
import com.naengo.api_server.domain.chat.entity.ChatMessage;
import com.naengo.api_server.domain.chat.entity.ChatRoom;
import com.naengo.api_server.domain.chat.repository.ChatMessageRepository;
import com.naengo.api_server.domain.chat.repository.ChatRoomRepository;
import com.naengo.api_server.domain.recipe.dto.RecipeListItemResponse;
import com.naengo.api_server.domain.recipe.entity.Recipe;
import com.naengo.api_server.domain.recipe.repository.RecipeRepository;
import com.naengo.api_server.domain.recipe.support.RecipeListMapper;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 채팅 read-only 도메인 서비스. AI 서버가 primary writer 이므로 본 서비스는
 * SELECT 만 수행하고 INSERT / UPDATE / DELETE 는 절대 하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeListMapper recipeListMapper;

    /** 본인의 활성 채팅방 목록 (`updated_at DESC`). */
    @Transactional(readOnly = true)
    public ChatRoomListResponse listMyRooms(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        Page<ChatRoom> result = chatRoomRepository.findActiveByUserOrderByLatestUpdated(userId, pageable);

        List<ChatRoomListItemResponse> items = result.getContent().stream()
                .map(ChatRoomListItemResponse::from)
                .toList();

        return new ChatRoomListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /** 본인 소유 채팅방의 메시지 시간순. recipe_ids 는 활성 RecipeListItemResponse 로 변환. */
    @Transactional(readOnly = true)
    public ChatMessageListResponse listMessages(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!userId.equals(room.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (!room.isActive()) {
            // 본인 소유라도 숨김 처리된 채팅방은 노출 안 함
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAt(roomId);

        // 메시지의 모든 recipe_id 를 한 번에 모아 일괄 조회 (N+1 방지)
        Set<Long> allRecipeIds = new HashSet<>();
        for (ChatMessage m : messages) {
            if (m.getRecipeIds() != null) allRecipeIds.addAll(m.getRecipeIds());
        }

        Map<Long, RecipeListItemResponse> recipeMap;
        if (allRecipeIds.isEmpty()) {
            recipeMap = Collections.emptyMap();
        } else {
            List<Recipe> active = recipeRepository.findActiveByIds(allRecipeIds);
            List<RecipeListItemResponse> mapped = recipeListMapper.toItems(active);
            recipeMap = mapped.stream()
                    .collect(Collectors.toMap(RecipeListItemResponse::recipeId, Function.identity()));
        }

        List<ChatMessageResponse> items = messages.stream().map(m -> {
            List<RecipeListItemResponse> recipes = null;
            if (m.getRecipeIds() != null && !m.getRecipeIds().isEmpty()) {
                List<RecipeListItemResponse> resolved = m.getRecipeIds().stream()
                        .map(recipeMap::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                // 비활성 레시피만 있던 메시지는 빈 배열 → null 로 정규화
                recipes = resolved.isEmpty() ? null : resolved;
            }
            return new ChatMessageResponse(
                    m.getMessageId(),
                    m.getRole(),
                    m.getContent(),
                    recipes,
                    m.getCreatedAt()
            );
        }).toList();

        return new ChatMessageListResponse(items);
    }

    private int clampSize(int size) {
        if (size <= 0) return 20;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
