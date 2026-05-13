package com.naengo.api_server.domain.chat.dto;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
