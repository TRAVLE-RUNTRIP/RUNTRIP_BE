package com.runtrip.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// 로그인 외 API는 모두 세션 쿠키가 필요하므로 기본 인증 요구사항으로 건다
@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "RUN+Trip API",
				version = "v1",
				description = """
						모든 응답은 `RsData` 형식입니다. 성공하면 `success=true`와 `data`, \
						실패하면 `success=false`와 `error.code`, `error.message`가 옵니다.

						로그인하면 `JSESSIONID` 세션 쿠키가 발급되고, 이후 요청은 이 쿠키로 인증합니다. \
						Swagger UI에서는 로그인 API를 먼저 호출하면 브라우저가 쿠키를 저장해 다른 API를 호출할 수 있습니다."""),
		security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE))
@SecurityScheme(
		name = OpenApiConfig.SESSION_COOKIE,
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.COOKIE,
		paramName = "JSESSIONID",
		description = "로그인 API가 발급하는 세션 쿠키 (HttpOnly, Secure, SameSite=Lax, 24시간)")
public class OpenApiConfig {

	public static final String SESSION_COOKIE = "sessionCookie";
}
