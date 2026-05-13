package com.naengo.api_server.domain.chat.dto;

import java.util.List;

public record ChatMessageListResponse(
        List<ChatMessageResponse> items
) {
}
