package com.runtrip.domain.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record LocationSearchResponse(
		@Schema(description = "서울 안 검색 결과. 없으면 빈 배열") List<LocationResult> results
) {
}
