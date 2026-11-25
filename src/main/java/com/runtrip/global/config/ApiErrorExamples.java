package com.runtrip.global.config;

// Swagger 에러 응답 예시. 어노테이션 값은 상수여야 해서 ErrorCode 메시지를 그대로 옮겨 적는다
public final class ApiErrorExamples {

	public static final String UNAUTHENTICATED = """
			{"success":false,"data":null,"error":{"code":"UNAUTHENTICATED","message":"로그인이 필요해요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String INVALID_CREDENTIALS = """
			{"success":false,"data":null,"error":{"code":"INVALID_CREDENTIALS","message":"이메일 또는 비밀번호를 확인해 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String VALIDATION_ERROR = """
			{"success":false,"data":null,"error":{"code":"VALIDATION_ERROR","message":"이메일을 입력해 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String QUERY_REQUIRED = """
			{"success":false,"data":null,"error":{"code":"VALIDATION_ERROR","message":"검색어를 입력해 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String TARGET_KM_RANGE = """
			{"success":false,"data":null,"error":{"code":"VALIDATION_ERROR","message":"목표 거리는 1~21km예요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String OUT_OF_SEOUL = """
			{"success":false,"data":null,"error":{"code":"OUT_OF_SEOUL","message":"서울에서만 만들 수 있어요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String DESTINATION_REQUIRED = """
			{"success":false,"data":null,"error":{"code":"VALIDATION_ERROR","message":"도착지를 입력해 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String DESTINATION_TOO_FAR = """
			{"success":false,"data":null,"error":{"code":"DESTINATION_TOO_FAR","message":"도착지가 너무 멀어요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String NO_PLACES = """
			{"success":false,"data":null,"error":{"code":"NO_PLACES","message":"이 거리로는 갈 만한 장소가 없어요. 거리를 늘려 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String COURSE_NOT_FOUND = """
			{"success":false,"data":null,"error":{"code":"COURSE_NOT_FOUND","message":"코스를 찾을 수 없어요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String DISTANCE_TOO_SHORT = """
			{"success":false,"data":null,"error":{"code":"DISTANCE_TOO_SHORT","message":"목표 거리가 최소 입력 거리보다 짧아요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String NO_MORE_COURSES = """
			{"success":false,"data":null,"error":{"code":"NO_MORE_COURSES","message":"다시 만들 코스가 없어요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String GENERATION_TIMEOUT = """
			{"success":false,"data":null,"error":{"code":"GENERATION_TIMEOUT","message":"코스 생성 시간이 초과됐어요. 다시 시도해 주세요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	public static final String SERVER_ERROR = """
			{"success":false,"data":null,"error":{"code":"SERVER_ERROR","message":"일시적인 오류가 발생했어요."},"timestamp":"2026-11-12T20:00:00+09:00"}""";

	private ApiErrorExamples() {
	}
}
