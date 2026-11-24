package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record Coordinate(
		@Schema(description = "위도", example = "37.5658") @NotNull(message = "위도를 입력해 주세요.") Double lat,
		@Schema(description = "경도", example = "126.9751") @NotNull(message = "경도를 입력해 주세요.") Double lng
) {
}
