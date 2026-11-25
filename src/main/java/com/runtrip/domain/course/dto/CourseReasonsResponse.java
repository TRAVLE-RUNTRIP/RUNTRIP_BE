package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// API 명세 6번 응답. PENDING이면 reasons는 빈 배열
public record CourseReasonsResponse(
		@Schema(description = "PENDING 또는 DONE", example = "DONE") String status,
		List<ReasonItem> reasons
) {

	public record ReasonItem(
			@Schema(description = "장소 ID", example = "T1116925") String placeId,
			@Schema(description = "추천 이유. 개요도 없으면 null", example = "돌담을 따라 조명이 이어져 밤에도 걷기 편해요",
					nullable = true) String reason,
			@Schema(description = "GEMINI 또는 OVERVIEW(실패 시 개요 첫 문장)", example = "GEMINI") String source
	) {
	}
}
