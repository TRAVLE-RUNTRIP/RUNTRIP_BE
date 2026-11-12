package com.runtrip.domain.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LocationResult(
		@Schema(description = "장소 이름", example = "경복궁") String name,
		@Schema(description = "주소", example = "서울 종로구 사직로 161") String address,
		@Schema(description = "위도", example = "37.5796") double lat,
		@Schema(description = "경도", example = "126.977") double lng
) {
}
