package com.runtrip.domain.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;

public record CourseCreateRequest(
		@Schema(description = "출발지 좌표")
		@NotNull(message = "출발지를 입력해 주세요.") @Valid Coordinate start,
		@Schema(description = "도착지 좌표. null이면 순환 코스", nullable = true)
		@Null(message = "도착지 지정은 아직 지원하지 않아요.") Coordinate end,
		@Schema(description = "목표 거리 (km, 1~21)", example = "5")
		@NotNull(message = "목표 거리를 입력해 주세요.")
		@DecimalMin(value = "1", message = "목표 거리는 1~21km예요.")
		@DecimalMax(value = "21", message = "목표 거리는 1~21km예요.") Double targetKm,
		@Schema(description = "취향 1~3개", example = "[\"NIGHT_VIEW\", \"NATURE\"]")
		@NotEmpty(message = "취향을 1개 이상 골라 주세요.")
		@Size(max = 3, message = "취향은 3개까지 고를 수 있어요.") List<@NotNull(message = "취향을 확인해 주세요.") Preference> preferences,
		@Schema(description = "다시 만들기에서 이미 보여준 장소 조합. 처음에는 빈 배열", example = "[]")
		List<List<String>> excludedCombos
) {

	@Schema(hidden = true)
	@AssertTrue(message = "취향이 중복됐어요.")
	public boolean isPreferencesUnique() {
		return preferences == null || new HashSet<>(preferences).size() == preferences.size();
	}
}
