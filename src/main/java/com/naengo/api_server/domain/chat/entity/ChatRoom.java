package com.naengo.api_server.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 채팅방. AI 서버가 생성·메시지 누적의 primary writer.
 * API 서버는 SELECT + 사용자 요청 soft delete(`is_active=false`) 만 쓴다 (PR-7, D-6 합의안).
 */
@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /** 사용자 숨김 처리 (soft delete). 실제 행은 보존. */
    public void deactivate() {
        this.isActive = false;
        this.updatedAt = ZonedDateTime.now();
    }
}
