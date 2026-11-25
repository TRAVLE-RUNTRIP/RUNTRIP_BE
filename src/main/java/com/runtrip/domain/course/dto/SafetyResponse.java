package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 코스 응답의 safety (API 명세 5·9번, B4 + timeBand)
public record SafetyResponse(
		@Schema(description = "DAY 또는 NIGHT. Asia/Seoul 18:00~06:00이 야간", example = "NIGHT") String timeBand,
		@Schema(description = "0~100 조명 환경 점수. 판정할 구간이 없으면 null", example = "81", nullable = true) Integer score,
		@Schema(description = "좋음(80 이상), 보통(60 이상), 주의, 자료부족. score가 null이면 null", example = "좋음",
				nullable = true) String level,
		@Schema(description = "보안등으로 판정한 길이 비율", example = "0.59") double coverage,
		@Schema(description = "큰길이라 조명이 있다고 추정한 길이 비율", example = "0.41") double estimated,
		@Schema(description = "\"적음\" 구간이 있으면 true", example = "true") boolean hasLowSegment,
		List<SegmentResponse> segments,
		@Schema(description = "화면에 보여줄 안내 문구") List<String> warnings
) {

	public record SegmentResponse(
			@Schema(description = "path의 시작 위치", example = "0") int fromIndex,
			@Schema(description = "path의 끝 위치", example = "40") int toIndex,
			@Schema(description = "큰길(조명 추정), 많음, 보통, 적음, 자료없음", example = "적음") String grade
	) {
	}
}
