package com.runtrip.domain.auth.service;

import com.runtrip.domain.auth.dto.LoginRequest;
import com.runtrip.domain.user.entity.User;
import com.runtrip.domain.user.repository.UserRepository;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public User authenticate(LoginRequest request) {
		return userRepository.findByEmail(request.email())
				.filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
				.orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
	}
}
