package com.runtrip.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	OUT_OF_SEOUL(HttpStatus.UNPROCESSABLE_CONTENT, "서울에서만 만들 수 있어요."),
	DISTANCE_TOO_SHORT(HttpStatus.UNPROCESSABLE_CONTENT, "목표 거리가 최소 입력 거리보다 짧아요."),
	DESTINATION_TOO_FAR(HttpStatus.UNPROCESSABLE_CONTENT, "도착지가 너무 멀어요."),
	NO_PLACES(HttpStatus.UNPROCESSABLE_CONTENT, "이 거리로는 갈 만한 장소가 없어요. 거리를 늘려 주세요."),
	NO_MORE_COURSES(HttpStatus.UNPROCESSABLE_CONTENT, "다시 만들 코스가 없어요."),
	COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "코스를 찾을 수 없어요."),
	GENERATION_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "코스 생성 시간이 초과됐어요. 다시 시도해 주세요."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
	SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요.");

	private final HttpStatus status;
	private final String message;

	public String getCode() {
		return name();
	}
}
