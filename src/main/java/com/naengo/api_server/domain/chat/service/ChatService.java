package com.naengo.api_server.domain.chat.service;

import com.naengo.api_server.domain.chat.dto.ChatMessageResponse;
import com.naengo.api_server.domain.chat.dto.ChatRoomListItemResponse;
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
 * 채팅 도메인 서비스. AI 서버가 메시지 누적의 primary writer 이고,
 * 본 서비스는 SELECT + 사용자 요청 soft delete(`is_active=false`) 만 수행한다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeListMapper recipeListMapper;

    /** 본인의 활성 채팅방 목록 (`updated_at DESC`, 단순 배열). */
    @Transactional(readOnly = true)
    public List<ChatRoomListItemResponse> listMyRooms(Integer userId) {
        return chatRoomRepository.findActiveByUserOrderByLatestUpdated(userId).stream()
                .map(ChatRoomListItemResponse::from)
                .toList();
    }

    /** 본인 소유 채팅방의 메시지 시간순 (단순 배열). recipe_ids → 활성 RecipeListItemResponse. */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Integer userId, Integer roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!userId.equals(room.getUserId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (!room.isActive()) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND);
        }

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAt(roomId);

        Set<Integer> allRecipeIds = new HashSet<>();
        for (ChatMessage m : messages) {
            if (m.getRecipeIds() != null) allRecipeIds.addAll(m.getRecipeIds());
        }

        Map<Integer, RecipeListItemResponse> recipeMap;
        if (allRecipeIds.isEmpty()) {
            recipeMap = Collections.emptyMap();
        } else {
            List<Recipe> active = recipeRepository.findActiveByIds(allRecipeIds);
            recipeMap = recipeListMapper.toItems(active).stream()
                    .collect(Collectors.toMap(RecipeListItemResponse::recipeId, Function.identity()));
        }

        return messages.stream().map(m -> {
            List<RecipeListItemResponse> recipes = null;
            if (m.getRecipeIds() != null && !m.getRecipeIds().isEmpty()) {
                List<RecipeListItemResponse> resolved = m.getRecipeIds().stream()
                        .map(recipeMap::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
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
    }

    /**
     * 채팅방 soft delete (`is_active=false`). 본인 소유 + 활성만.
     * 없거나 타인 소유거나 이미 삭제됨 → 404 (api-3.json: 이미 삭제 시 404).
     */
    @Transactional
    public void deleteRoom(Integer userId, Integer roomId) {
        ChatRoom room = chatRoomRepository.findActiveByIdAndUser(roomId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_FOUND));
        room.deactivate();
    }
}
