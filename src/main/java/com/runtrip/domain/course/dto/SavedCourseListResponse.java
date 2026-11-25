package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// API 명세 8번 응답. 저장 시각 최신순
public record SavedCourseListResponse(
		@Schema(description = "저장 코스 수", example = "1") int count,
		List<SavedCourse> courses
) {

	public record SavedCourse(
			@Schema(description = "코스 ID", example = "101") Long courseId,
			@Schema(description = "코스 이름", example = "덕수궁·청계천 코스") String name,
			@Schema(description = "LOOP, ONE_WAY, FREE", example = "LOOP") String type,
			@Schema(description = "실제 경로 거리 (km)", example = "5.2") BigDecimal distanceKm,
			@Schema(description = "지역", example = "서울 중구", nullable = true) String region,
			@Schema(description = "취향", example = "[\"NIGHT_VIEW\", \"NATURE\"]") List<Preference> preferences,
			@Schema(description = "저장 시각") OffsetDateTime savedAt
	) {
	}
}
