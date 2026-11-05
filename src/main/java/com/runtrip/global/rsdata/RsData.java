package com.runtrip.global.rsdata;

import com.runtrip.global.error.ErrorCode;
import java.time.OffsetDateTime;

public record RsData<T>(
		boolean success,
		T data,
		ErrorInfo error,
		OffsetDateTime timestamp
) {

	public record ErrorInfo(String code, String message) {
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
