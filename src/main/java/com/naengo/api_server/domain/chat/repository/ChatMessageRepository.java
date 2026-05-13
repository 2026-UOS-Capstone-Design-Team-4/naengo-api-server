package com.naengo.api_server.domain.chat.repository;

import com.naengo.api_server.domain.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
           SELECT m FROM ChatMessage m
           WHERE m.roomId = :roomId
           ORDER BY m.createdAt ASC, m.messageId ASC
           """)
    List<ChatMessage> findByRoomIdOrderByCreatedAt(@Param("roomId") Long roomId);
}
