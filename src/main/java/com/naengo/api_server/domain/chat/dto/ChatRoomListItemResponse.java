package com.naengo.api_server.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naengo.api_server.domain.chat.entity.ChatRoom;

import java.time.ZonedDateTime;

/**
 * 채팅방 한 행 — api-3.json {@code ChatRoomResponse} 정합 (snake_case).
 */
public record ChatRoomListItemResponse(
        @JsonProperty("room_id") Long roomId,
        @JsonProperty("title") String title,
        @JsonProperty("created_at") ZonedDateTime createdAt,
        @JsonProperty("updated_at") ZonedDateTime updatedAt
) {
    public static ChatRoomListItemResponse from(ChatRoom room) {
        return new ChatRoomListItemResponse(
                room.getRoomId(),
                room.getTitle(),
                room.getCreatedAt(),
                room.getUpdatedAt()
        );
    }
}
