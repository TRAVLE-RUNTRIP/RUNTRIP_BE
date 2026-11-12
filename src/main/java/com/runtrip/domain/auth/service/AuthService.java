package com.runtrip.domain.auth.service;

import com.runtrip.domain.auth.dto.LoginRequest;
import com.runtrip.domain.user.entity.User;
import com.runtrip.domain.user.repository.UserRepository;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	// 없는 이메일도 BCrypt 비교를 거치게 해 응답 시간으로 계정 존재 여부가 드러나지 않게 한다
	private final String dummyHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.dummyHash = passwordEncoder.encode("dummy-password");
	}

	public User authenticate(LoginRequest request) {
		User user = userRepository.findByEmail(request.email()).orElse(null);
		String hash = user != null ? user.getPasswordHash() : dummyHash;
		if (!passwordEncoder.matches(request.password(), hash) || user == null) {
			throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
		}
		return user;
	}
}
