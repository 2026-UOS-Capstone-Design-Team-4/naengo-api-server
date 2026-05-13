package com.naengo.api_server.domain.chat.repository;

import com.naengo.api_server.domain.chat.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("""
           SELECT r FROM ChatRoom r
           WHERE r.userId = :userId AND r.isActive = true
           ORDER BY r.updatedAt DESC
           """)
    Page<ChatRoom> findActiveByUserOrderByLatestUpdated(@Param("userId") Long userId, Pageable pageable);
}
