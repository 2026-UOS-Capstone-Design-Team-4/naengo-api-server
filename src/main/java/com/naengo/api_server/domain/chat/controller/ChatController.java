package com.naengo.api_server.domain.chat.controller;

import com.naengo.api_server.domain.chat.dto.ChatMessageResponse;
import com.naengo.api_server.domain.chat.dto.ChatRoomListItemResponse;
import com.naengo.api_server.domain.chat.service.ChatService;
import com.naengo.api_server.global.auth.SecurityUtil;
import com.naengo.api_server.global.exception.CustomException;
import com.naengo.api_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 채팅 — api-3.json `/api/v1/chat/rooms`.
 *
 * <ul>
 *   <li>{@code GET /rooms} — 본인 활성 채팅방 (단순 배열, updated_at DESC)</li>
 *   <li>{@code GET /rooms/{room_id}} — 메시지 시간순 (단순 배열)</li>
 *   <li>{@code DELETE /rooms/{room_id}} — soft delete (is_active=false)</li>
 * </ul>
 * SSE 두 endpoint (`POST /rooms`, `POST /rooms/{id}`) 는 AI 서버 단독 책임 — 본 서버 미구현.
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/rooms")
    public List<ChatRoomListItemResponse> listMyRooms() {
        return chatService.listMyRooms(currentUserId());
    }

    @GetMapping("/rooms/{roomId}")
    public List<ChatMessageResponse> listMessages(@PathVariable Integer roomId) {
        return chatService.listMessages(currentUserId(), roomId);
    }

    @DeleteMapping("/rooms/{roomId}")
    public Map<String, String> deleteRoom(@PathVariable Integer roomId) {
        chatService.deleteRoom(currentUserId(), roomId);
        return Map.of("message", "채팅방이 삭제되었습니다.");
    }

    private Integer currentUserId() {
        Integer userId = SecurityUtil.currentUserIdOrNull();
        if (userId == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        return userId;
    }
}
