package com.naengo.api_server.domain.recipe.repository;

import com.naengo.api_server.domain.recipe.entity.PendingRecipe;
import com.naengo.api_server.domain.recipe.entity.RecipeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PendingRecipeRepository extends JpaRepository<PendingRecipe, Long> {

    /** 본인 제출 목록 — is_active=true, 최신순. api-3.json `GET /api/v1/pending-recipes` (단순 배열). */
    @Query("""
           SELECT p FROM PendingRecipe p
           WHERE p.userId = :userId AND p.isActive = true
           ORDER BY p.createdAt DESC
           """)
    List<PendingRecipe> findActiveByUserOrderByLatest(@Param("userId") Long userId);

    /** 본인 제출 단건 — is_active=true. 소유자 검증 포함. */
    @Query("""
           SELECT p FROM PendingRecipe p
           WHERE p.pendingRecipeId = :id AND p.userId = :userId AND p.isActive = true
           """)
    Optional<PendingRecipe> findActiveByIdAndUser(@Param("id") Long id,
                                                  @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM PendingRecipe p WHERE p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    /**
     * 관리자 검토용 — status 별 목록 (모든 사용자, is_active 무관, created_at DESC).
     */
    @Query("""
           SELECT p FROM PendingRecipe p
           WHERE p.status = :status
           ORDER BY p.createdAt DESC
           """)
    Page<PendingRecipe> findByStatusOrderByLatest(@Param("status") RecipeStatus status, Pageable pageable);
}
