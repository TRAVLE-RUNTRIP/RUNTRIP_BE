package com.runtrip.domain.auth.controller;

import com.runtrip.domain.auth.dto.LoginRequest;
import com.runtrip.domain.auth.service.AuthService;
import com.runtrip.domain.user.dto.UserResponse;
import com.runtrip.domain.user.entity.User;
import com.runtrip.global.config.ApiErrorExamples;
import com.runtrip.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "로그인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository securityContextRepository;

	@Operation(summary = "로그인", description = "이메일과 비밀번호로 로그인하고 세션 쿠키를 발급합니다.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "로그인 성공",
			headers = @Header(name = "Set-Cookie", description = "`JSESSIONID` 세션 쿠키 (HttpOnly, Secure, SameSite=Lax, 24시간)"))
	@ApiResponse(responseCode = "400", description = "이메일 또는 비밀번호가 비어 있음",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.VALIDATION_ERROR)))
	@ApiResponse(responseCode = "401", description = "이메일이 없거나 비밀번호가 틀림",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.INVALID_CREDENTIALS)))
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
