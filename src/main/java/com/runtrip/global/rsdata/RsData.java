package com.runtrip.global.rsdata;

import com.runtrip.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record RsData<T>(
		@Schema(description = "성공 여부") boolean success,
		@Schema(description = "성공 시 응답 데이터, 실패 시 null") T data,
		@Schema(description = "실패 시 에러 정보, 성공 시 null") ErrorInfo error,
		@Schema(description = "응답 시각 (Asia/Seoul)", example = "2026-11-12T20:00:00+09:00") OffsetDateTime timestamp
) {

	public record ErrorInfo(
			@Schema(description = "에러 코드 (ErrorCode 이름)", example = "UNAUTHENTICATED") String code,
			@Schema(description = "사용자에게 보여줄 메시지", example = "로그인이 필요해요.") String message) {
	}

	public static <T> RsData<T> success(T data) {
		return new RsData<>(true, data, null, OffsetDateTime.now());
	}

	public static <T> RsData<T> fail(ErrorCode errorCode) {
		return fail(errorCode, errorCode.getMessage());
	}

	// 검증 실패처럼 상세 메시지를 직접 지정해야 할 때 사용
	public static <T> RsData<T> fail(ErrorCode errorCode, String message) {
		return new RsData<>(false, null, new ErrorInfo(errorCode.getCode(), message), OffsetDateTime.now());
	}
}
