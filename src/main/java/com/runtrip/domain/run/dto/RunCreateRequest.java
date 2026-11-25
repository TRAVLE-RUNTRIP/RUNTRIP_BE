package com.runtrip.domain.run.dto;

import com.runtrip.domain.run.entity.RunType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// API 명세 11번 요청
public record RunCreateRequest(
		@Schema(description = "앱이 만든 UUID. 중복 저장 방지용")
		@NotNull(message = "runId를 입력해 주세요.") UUID runId,
		@Schema(description = "COURSE 또는 FREE", example = "COURSE")
		@NotNull(message = "러닝 종류를 입력해 주세요.") RunType type,
		@Schema(description = "코스 러닝의 코스 ID. 자유 러닝은 null", example = "101", nullable = true)
		Long courseId,
		@Schema(description = "기록에 복사해 두는 이름. 자유 러닝은 \"자유 러닝\"", example = "덕수궁·청계천 코스")
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(max = 100, message = "제목은 100자까지예요.") String title,
		@Schema(description = "시작 시각", example = "2026-11-05T21:30:00+09:00")
		@NotNull(message = "시작 시각을 입력해 주세요.") OffsetDateTime startedAt,
		@Schema(description = "종료 시각", example = "2026-11-05T22:02:14+09:00")
		@NotNull(message = "종료 시각을 입력해 주세요.") OffsetDateTime endedAt,
		@Schema(description = "거리 (km)", example = "5.21")
		@NotNull(message = "거리를 입력해 주세요.")
		@DecimalMin(value = "0", message = "거리를 확인해 주세요.")
		@DecimalMax(value = "999.99", message = "거리를 확인해 주세요.") BigDecimal distanceKm,
		@Schema(description = "달린 시간 (초)", example = "1934")
		@NotNull(message = "달린 시간을 입력해 주세요.")
		@PositiveOrZero(message = "달린 시간을 확인해 주세요.") Integer durationSec,
		@Schema(description = "평균 페이스 (초/km)", example = "371")
		@NotNull(message = "평균 페이스를 입력해 주세요.")
		@PositiveOrZero(message = "평균 페이스를 확인해 주세요.") Integer avgPaceSec,
		@Schema(description = "들른 장소 ID. 자유 러닝은 빈 배열. 지금은 저장하지 않아요", example = "[\"T1116925\"]")
		@NotNull(message = "방문 장소를 입력해 주세요.") List<String> visitedPlaceIds,
		@Schema(description = "끊긴 구간마다 나눈 경로. 점은 [위도, 경도]", example = "[[[37.5658, 126.9751], [37.5661, 126.9755]]]")
		@NotNull(message = "경로를 입력해 주세요.")
		List<@NotNull(message = "경로를 확인해 주세요.") List<@NotNull(message = "경로를 확인해 주세요.")
				@Size(min = 2, max = 2, message = "경로의 점은 [위도, 경도]예요.")
				List<@NotNull(message = "경로를 확인해 주세요.") Double>>> segments,
		@Schema(description = "코스 러닝은 그 코스를 저장, 자유 러닝은 경로로 새 코스를 만들어 저장", example = "true")
		@NotNull(message = "코스 저장 여부를 입력해 주세요.") Boolean saveCourse
) {

	@Schema(hidden = true)
	@AssertTrue(message = "종료 시각은 시작 시각보다 뒤여야 해요.")
	public boolean isEndedAfterStarted() {
		return startedAt == null || endedAt == null || endedAt.isAfter(startedAt);
	}

	@Schema(hidden = true)
	@AssertTrue(message = "코스 러닝은 courseId가 필요하고, 자유 러닝은 courseId가 없어야 해요.")
	public boolean isCourseIdMatchingType() {
		return type == null || (type == RunType.COURSE) == (courseId != null);
	}

	// 자유 러닝 코스의 경로선은 점이 2개 이상이어야 만들 수 있다
	@Schema(hidden = true)
	@AssertTrue(message = "코스로 저장하려면 경로의 점이 2개 이상이어야 해요.")
	public boolean isFreeCoursePathValid() {
		if (type != RunType.FREE || !Boolean.TRUE.equals(saveCourse) || segments == null) {
			return true;
		}
		return segments.stream().filter(Objects::nonNull).mapToInt(List::size).sum() >= 2;
	}
}
