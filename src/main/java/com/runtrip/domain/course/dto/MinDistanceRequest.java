package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MinDistanceRequest(
		@Schema(description = "출발지 좌표")
		@NotNull(message = "출발지를 입력해 주세요.") @Valid Coordinate start,
		@Schema(description = "도착지 좌표")
		@NotNull(message = "도착지를 입력해 주세요.") @Valid Coordinate end
) {
}
