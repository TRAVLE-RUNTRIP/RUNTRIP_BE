package com.runtrip.domain.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.runtrip.global.osrm.OsrmClient.LatLng;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class CourseServiceTests {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void 서울_시각_18시부터_6시_전까지가_야간() {
		assertThat(CourseService.isNight(ZonedDateTime.of(2026, 11, 24, 18, 0, 0, 0, SEOUL))).isTrue();
		assertThat(CourseService.isNight(ZonedDateTime.of(2026, 11, 24, 5, 59, 0, 0, SEOUL))).isTrue();
		assertThat(CourseService.isNight(ZonedDateTime.of(2026, 11, 24, 6, 0, 0, 0, SEOUL))).isFalse();
		assertThat(CourseService.isNight(ZonedDateTime.of(2026, 11, 24, 17, 59, 0, 0, SEOUL))).isFalse();
	}

	@Test
	void 다른_시간대로_와도_서울_시각으로_판단() {
		// UTC 10:00 = 서울 19:00
		assertThat(CourseService.isNight(ZonedDateTime.of(2026, 11, 24, 10, 0, 0, 0, ZoneId.of("UTC")))).isTrue();
	}

	@Test
	void 거리별_최대_장소_수() {
		assertThat(CourseService.maxPlaces(1)).isEqualTo(2);
		assertThat(CourseService.maxPlaces(3)).isEqualTo(2);
		assertThat(CourseService.maxPlaces(5)).isEqualTo(3);
		assertThat(CourseService.maxPlaces(7)).isEqualTo(3);
		assertThat(CourseService.maxPlaces(14)).isEqualTo(4);
		assertThat(CourseService.maxPlaces(21)).isEqualTo(5);
	}

	@Test
	void 직선거리는_구면_거리() {
		// 위도 1도 ≈ 111.2km
		assertThat(CourseService.distanceM(new LatLng(37, 127), new LatLng(38, 127))).isCloseTo(111_195, within(1.0));
		assertThat(CourseService.distanceM(new LatLng(37.5658, 126.9751), new LatLng(37.5658, 126.9751))).isZero();
	}
}
