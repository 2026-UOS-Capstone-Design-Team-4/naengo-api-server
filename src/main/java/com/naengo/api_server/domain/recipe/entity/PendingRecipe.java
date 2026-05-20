package com.naengo.api_server.domain.recipe.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 제출 레시피 (정규화 — submission_text + draft_payload JSONB).
 *
 * <p>구조화 필드(description/ingredients/instructions/…)는 {@code draftPayload}
 * JSONB 에 보관한다. api-3.json 평면 계약을 깨지 않도록, 평면 accessor
 * (getContent/getDescription/getIngredients/…) 를 그대로 노출하여
 * draft_payload 와 submission_text 위에 평면 뷰를 제공한다.
 */
@Entity
@Table(name = "pending_recipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PendingRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pending_recipe_id")
    private Long pendingRecipeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "submission_text", columnDefinition = "TEXT", nullable = false)
    private String submissionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_payload", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private RecipeDraft draftPayload = RecipeDraft.empty();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_suggested_patch", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private RecipeDraft aiSuggestedPatch = RecipeDraft.empty();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_errors", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RecipeStatus status = RecipeStatus.PENDING;

    /** "NOT_IMPORTED" / "IMPORTED" / "FAILED" */
    @Column(name = "import_status", nullable = false, length = 30)
    @Builder.Default
    private String importStatus = "NOT_IMPORTED";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @Column(name = "imported_recipe_id")
    private Long importedRecipeId;

    @Column(name = "imported_at")
    private ZonedDateTime importedAt;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    // ─── 평면 뷰 accessor (api-3.json 계약 유지) ─────────────

    /** api-3.json content == 사용자 원문 == submission_text. */
    public String getContent() {
        return submissionText;
    }

    public String getDescription() {
        return draftPayload.getDescription();
    }

    public List<Ingredient> getIngredients() {
        return draftPayload.getIngredients();
    }

    /** draft 는 ingredients_raw 를 배열로 보관 — 평면 계약은 단일 문자열. */
    public String getIngredientsRaw() {
        List<String> raw = draftPayload.getIngredientsRaw();
        if (raw == null || raw.isEmpty()) return null;
        return String.join("\n", raw);
    }

    public List<String> getInstructions() {
        return draftPayload.getInstructions();
    }

    public BigDecimal getServings() {
        return draftPayload.getServings();
    }

    public Integer getCookingTime() {
        return draftPayload.getCookingTimeMinutes();
    }

    public Integer getCalories() {
        return draftPayload.getKcalPerServing();
    }

    public String getDifficulty() {
        return draftPayload.getDifficulty();
    }

    public List<String> getCategory() {
        return draftPayload.getCategory();
    }

    public List<String> getTags() {
        return draftPayload.getTags();
    }

    public List<String> getTips() {
        return draftPayload.getTips();
    }

    public String getVideoUrl() {
        return draftPayload.getVideoUrl();
    }

    public String getImageUrl() {
        return draftPayload.getImageUrl();
    }

    // ─── 상태 전이 ────────────────────────────────────────

    public void cancel() {
        this.isActive = false;
    }

    public void markApproved(String adminNote) {
        this.status = RecipeStatus.APPROVED;
        this.adminNote = adminNote;
        this.reviewedAt = ZonedDateTime.now();
    }

    public void markRejected(String reason) {
        this.status = RecipeStatus.REJECTED;
        this.rejectionReason = reason;
        this.adminNote = reason;
        this.reviewedAt = ZonedDateTime.now();
    }

    /** 승격 완료 표시 (recipes 로 import 됨). */
    public void markImported(Long recipeId) {
        this.importStatus = "IMPORTED";
        this.importedRecipeId = recipeId;
        this.importedAt = ZonedDateTime.now();
    }

    /** status 전이 없이 admin_note 만 갱신. */
    public void setAdminNote(String adminNote) {
        if (adminNote != null) this.adminNote = adminNote;
    }

    /** status 변경 시 검토 시각 기록 (api-3.json: status 변경 → reviewed_at = NOW). */
    public void changeStatus(RecipeStatus newStatus) {
        this.status = newStatus;
        this.reviewedAt = ZonedDateTime.now();
    }

    /**
     * 관리자 부분 수정 — null 인자는 기존 값 보존 (PATCH 시맨틱).
     * 평면 입력을 draft_payload / submission_text 로 반영.
     */
    public void applyAdminPatch(String title, String content, String description,
                                List<Ingredient> ingredients, String ingredientsRaw,
                                List<String> instructions, BigDecimal servings,
                                Integer cookingTime, Integer calories, String difficulty,
                                List<String> category, List<String> tags, List<String> tips,
                                String videoUrl, String imageUrl) {
        if (title != null) this.title = title;
        if (content != null) this.submissionText = content;
        RecipeDraft d = this.draftPayload;
        if (description != null) d.setDescription(description);
        if (ingredients != null) d.setIngredients(ingredients);
        if (ingredientsRaw != null) {
            d.setIngredientsRaw(ingredientsRaw.isBlank()
                    ? new ArrayList<>()
                    : new ArrayList<>(List.of(ingredientsRaw)));
        }
        if (instructions != null) d.setInstructions(instructions);
        if (servings != null) d.setServings(servings);
        if (cookingTime != null) d.setCookingTimeMinutes(cookingTime);
        if (calories != null) d.setKcalPerServing(calories);
        if (difficulty != null) d.setDifficulty(difficulty);
        if (category != null) d.setCategory(category);
        if (tags != null) d.setTags(tags);
        if (tips != null) d.setTips(tips);
        if (videoUrl != null) d.setVideoUrl(videoUrl);
        if (imageUrl != null) d.setImageUrl(imageUrl);
    }
}
