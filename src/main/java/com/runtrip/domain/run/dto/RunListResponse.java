package com.runtrip.domain.run.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// API 명세 12번 응답. 시작 시각 최신순
public record RunListResponse(List<RunItem> runs) {

	public record RunItem(
			@Schema(description = "기록 ID") UUID runId,
			@Schema(description = "COURSE 또는 FREE", example = "COURSE") String type,
			@Schema(description = "제목", example = "덕수궁·청계천 코스") String title,
			@Schema(description = "시작 시각") OffsetDateTime startedAt,
			@Schema(description = "달린 시간 (초)", example = "1934") int durationSec,
			@Schema(description = "평균 페이스 (초/km)", example = "371") int avgPaceSec,
			@Schema(description = "거리 (km)", example = "5.21") BigDecimal distanceKm
	) {
	}
}
