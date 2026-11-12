package com.runtrip.domain.auth.controller;

import com.runtrip.domain.auth.dto.LoginRequest;
import com.runtrip.domain.auth.service.AuthService;
import com.runtrip.domain.user.dto.UserResponse;
import com.runtrip.domain.user.entity.User;
import com.runtrip.global.rsdata.RsData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository securityContextRepository;

	@PostMapping("/login")
	public RsData<UserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		User user = authService.authenticate(request);

		// 세션 고정 공격 방지: 로그인 전 세션이 있으면 ID를 바꾼다
		if (httpRequest.getSession(false) != null) {
			httpRequest.changeSessionId();
		}
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.getId(), null, List.of()));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		return RsData.success(UserResponse.from(user));
	}
}
