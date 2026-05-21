package com.naengo.api_server.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class SignUpRequest {

    // DBv5 정합 후 — 로그인 식별자는 email 이 아니라 username (이메일 포맷 강제 안 함).
    @NotBlank(message = "username 은 필수 입력입니다.")
    @Size(min = 3, max = 255, message = "username 은 3자 이상 255자 이하여야 합니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수 입력입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+=-]{8,}$", message = "비밀번호는 영문자와 숫자를 각각 하나 이상 포함해야 합니다.")
    private String password;

    @NotBlank(message = "닉네임은 필수 입력입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    private String nickname;
}
