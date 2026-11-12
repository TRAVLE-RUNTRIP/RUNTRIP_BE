package com.runtrip.domain.user.controller;

import com.runtrip.domain.user.dto.UserResponse;
import com.runtrip.domain.user.repository.UserRepository;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.rsdata.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepository;

	@GetMapping("/me")
	public RsData<UserResponse> me(@AuthenticationPrincipal Long userId) {
		return userRepository.findById(userId)
				.map(user -> RsData.success(UserResponse.from(user)))
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
	}
}
