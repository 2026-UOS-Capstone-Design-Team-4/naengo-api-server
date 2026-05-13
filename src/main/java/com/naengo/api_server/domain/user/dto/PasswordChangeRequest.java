package com.naengo.api_server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호는 필수 입력입니다.")
        @Size(max = 64)
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수 입력입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+=-]{8,}$",
                message = "비밀번호는 영문자와 숫자를 각각 하나 이상 포함해야 합니다."
        )
        String newPassword
) {
}
