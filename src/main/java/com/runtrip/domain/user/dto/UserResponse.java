package com.runtrip.domain.user.dto;

import com.runtrip.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserResponse(
		@Schema(description = "사용자 ID", example = "1") Long userId,
		@Schema(description = "이메일", example = "runner@runtrip.test") String email,
		@Schema(description = "닉네임", example = "러너") String nickname
) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
	}
}
