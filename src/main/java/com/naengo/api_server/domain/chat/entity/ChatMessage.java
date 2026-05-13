package com.naengo.api_server.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 채팅 메시지. AI 서버가 primary writer.
 * API 서버는 read-only.
 *
 * <p>{@code role} 은 V1 CHECK 제약(소문자 'user'/'model') 에 맞춰 String 으로 둔다.
 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** "user" | "model" */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 추천된 레시피 ID 배열. NULL 가능. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipe_ids", columnDefinition = "jsonb")
    private List<Long> recipeIds;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
