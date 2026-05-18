package com.naengo.api_server.domain.chat.repository;

import com.naengo.api_server.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    /** 본인 활성 채팅방 — updated_at 내림차순 (api-3.json `GET /api/v1/chat/rooms`, 단순 배열). */
    @Query("""
           SELECT r FROM ChatRoom r
           WHERE r.userId = :userId AND r.isActive = true
           ORDER BY r.updatedAt DESC
           """)
    List<ChatRoom> findActiveByUserOrderByLatestUpdated(@Param("userId") Long userId);

    /** 본인 소유 활성 채팅방 단건 (soft delete 가드). */
    @Query("""
           SELECT r FROM ChatRoom r
           WHERE r.roomId = :roomId AND r.userId = :userId AND r.isActive = true
           """)
    Optional<ChatRoom> findActiveByIdAndUser(@Param("roomId") Long roomId,
                                             @Param("userId") Long userId);

    /**
     * 회원 탈퇴 시 사용자의 모든 채팅방 soft delete (`is_active=false`).
     *
     * <p>chat_messages 행 자체의 hard delete / 본문 PII 스크럽은 AI 서버
     * (메시지 primary writer) 합의 후 승격 (`docs/spec/user-domain-todo.md §6-2`).
     */
    @Modifying
    @Query("UPDATE ChatRoom r SET r.isActive = false WHERE r.userId = :userId AND r.isActive = true")
    int deactivateAllByUserId(@Param("userId") Long userId);
}
