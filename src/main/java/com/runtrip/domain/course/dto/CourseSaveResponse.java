package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

// API 명세 7번 응답
public record CourseSaveResponse(
		@Schema(description = "코스 ID", example = "101") Long courseId,
		@Schema(description = "저장 여부", example = "true") boolean saved,
		@Schema(description = "처음 저장한 시각") OffsetDateTime savedAt
) {
}
