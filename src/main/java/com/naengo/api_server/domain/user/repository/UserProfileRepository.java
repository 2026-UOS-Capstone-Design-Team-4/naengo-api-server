package com.naengo.api_server.domain.user.repository;

import com.naengo.api_server.domain.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Modifying
    @Query("DELETE FROM UserProfile p WHERE p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
