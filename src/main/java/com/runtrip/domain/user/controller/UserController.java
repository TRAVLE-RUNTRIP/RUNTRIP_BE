package com.runtrip.domain.user.controller;

import com.runtrip.domain.user.dto.UserResponse;
import com.runtrip.domain.user.repository.UserRepository;
import com.runtrip.global.config.ApiErrorExamples;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepository;

	@Operation(summary = "내 정보 조회", description = "세션 쿠키의 사용자 정보를 반환합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@GetMapping("/me")
	public RsData<UserResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return userRepository.findById(userId)
				.map(user -> RsData.success(UserResponse.from(user)))
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
	}
}
