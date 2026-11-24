package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record Coordinate(
		@Schema(description = "위도", example = "37.5658") @NotNull(message = "위도를 입력해 주세요.")
		@DecimalMin(value = "-90", message = "위도는 -90~90 사이여야 해요.")
		@DecimalMax(value = "90", message = "위도는 -90~90 사이여야 해요.") Double lat,
		@Schema(description = "경도", example = "126.9751") @NotNull(message = "경도를 입력해 주세요.")
		@DecimalMin(value = "-180", message = "경도는 -180~180 사이여야 해요.")
		@DecimalMax(value = "180", message = "경도는 -180~180 사이여야 해요.") Double lng
) {
}
