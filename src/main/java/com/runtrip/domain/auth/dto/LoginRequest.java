package com.runtrip.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@Schema(description = "이메일", example = "runner@runtrip.test")
		@NotBlank(message = "이메일을 입력해 주세요.") String email,
		@Schema(description = "비밀번호", example = "test-password")
		@NotBlank(message = "비밀번호를 입력해 주세요.") String password
) {
}
