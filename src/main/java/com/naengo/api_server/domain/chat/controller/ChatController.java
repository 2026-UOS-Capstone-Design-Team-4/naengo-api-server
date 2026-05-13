package com.naengo.api_server.domain.chat.controller;

import com.naengo.api_server.domain.chat.dto.ChatMessageListResponse;
import com.naengo.api_server.domain.chat.dto.ChatRoomListResponse;
import com.naengo.api_server.domain.chat.service.ChatService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.dto.ApiResponse;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 본인 채팅방 목록 (`SPEC-20260503-08`). */
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> listMyRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.listMyRooms(currentUserId(), page, size)));
    }

    /** 채팅방 메시지 시간순 (`SPEC-20260503-09`). */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageListResponse>> listMessages(@PathVariable Long roomId) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.listMessages(currentUserId(), roomId)));
    }

    private Long currentUserId() {
        Long userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
