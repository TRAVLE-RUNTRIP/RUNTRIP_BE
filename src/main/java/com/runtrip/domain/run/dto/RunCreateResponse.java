package com.runtrip.domain.run.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

// API 명세 11번 응답. 앱은 courseSaved가 true면 C-02, false면 P-01로 이동
public record RunCreateResponse(
		@Schema(description = "기록 ID (요청의 runId)") UUID runId,
		@Schema(description = "연결된 코스가 저장 상태인지", example = "true") boolean courseSaved,
		@Schema(description = "연결된 코스 ID. 코스가 정리됐거나 자유 러닝을 저장하지 않았으면 null", example = "101",
				nullable = true) Long courseId
) {
}
