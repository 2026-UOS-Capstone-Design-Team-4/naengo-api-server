package com.naengo.api_server.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 사용자 직접 입력 취향 전체 교체 — api-3.json {@code UserInputUpdateRequest} 정합.
 *
 * <p>{@code user_input} 은 필수. 전달된 배열로 기존 값을 <b>완전히 교체</b>한다
 * (빈 배열 → 초기화).
 */
public record UserInputUpdateRequest(
        @JsonProperty("user_input")
        @NotNull(message = "user_input 은 필수입니다.")
        @Size(max = 50, message = "user_input 은 최대 50개입니다.")
        List<@Size(min = 1, max = 500) String> userInput
) {
}
