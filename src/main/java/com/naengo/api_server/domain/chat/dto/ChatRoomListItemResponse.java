package com.naengo.api_server.domain.chat.dto;

import com.naengo.api_server.domain.chat.entity.ChatRoom;

import java.time.ZonedDateTime;

/**
 * AI 서버 OpenAPI 의 ChatRoomResponse 와 동일한 schema.
 */
public record ChatRoomListItemResponse(
        Long roomId,
        String title,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
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
