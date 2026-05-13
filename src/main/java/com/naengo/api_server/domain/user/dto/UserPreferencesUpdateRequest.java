package com.naengo.api_server.domain.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 직접 입력 영역만. AI 분석 필드는 의도적으로 부재 (defense in depth).
 * 모든 필드 선택. 보낸 필드만 갱신, 미포함 필드는 기존 값 유지.
 */
public record UserPreferencesUpdateRequest(
        @Size(max = 50, message = "userInput 은 최대 50개입니다.")
        List<@NotBlank @Size(min = 1, max = 500) String> userInput,

        @Pattern(regexp = "easy|normal|hard", message = "cookingSkill 은 easy / normal / hard 중 하나입니다.")
        String cookingSkill,

        @Positive(message = "preferredCookingTime 은 양수여야 합니다.")
        Integer preferredCookingTime,

        @DecimalMin(value = "0.1", message = "servingSize 는 0.1 이상이어야 합니다.")
        @DecimalMax(value = "99.9", message = "servingSize 는 99.9 이하여야 합니다.")
        BigDecimal servingSize
) {
}
