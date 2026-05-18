package com.naengo.api_server.domain.scrap.repository;

import com.naengo.api_server.domain.scrap.entity.Scrap;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUserIdAndRecipeId(Long userId, Long recipeId);

    @Modifying
    @Query("DELETE FROM Scrap s WHERE s.userId = :userId AND s.recipeId = :recipeId")
    int deleteByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") Long recipeId);

    @Modifying
    @Query("DELETE FROM Scrap s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /** 주어진 recipeId 집합 중 사용자가 스크랩한 것만. is_scrapped 일괄 계산용. */
    @Query("SELECT s.recipeId FROM Scrap s WHERE s.userId = :userId AND s.recipeId IN :ids")
    List<Long> findScrappedRecipeIds(@Param("userId") Long userId,
                                     @Param("ids") Collection<Long> ids);

    /**
     * 본인 스크랩 커서 페이지 — scrap_id 내림차순 (스크랩한 시각의 역순).
     * cursor 는 마지막으로 받은 scrapId. null 이면 첫 페이지. pageable 은 LIMIT 용.
     */
    @Query("""
           SELECT s FROM Scrap s
           WHERE s.userId = :userId
             AND (:cursor IS NULL OR s.scrapId < :cursor)
           ORDER BY s.scrapId DESC
           """)
    List<Scrap> findUserScrapsPage(@Param("userId") Long userId,
                                   @Param("cursor") Long cursor,
                                   Pageable pageable);
}
