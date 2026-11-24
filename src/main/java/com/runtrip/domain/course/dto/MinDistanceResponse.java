package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record MinDistanceResponse(
		@Schema(description = "출발지→도착지 최단 도로 거리 (km)", example = "2.1") BigDecimal shortestKm,
		@Schema(description = "입력할 수 있는 최소 목표 거리 (km). 최단 거리 × 1.1을 0.1km 단위로 올림", example = "2.4")
		BigDecimal minInputKm
) {
}
