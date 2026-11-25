package com.runtrip.domain.course.service;

import com.runtrip.domain.course.repository.CourseRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 저장하지 않은 코스를 만든 지 48시간 뒤 지운다 (API 명세 5번)
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCleanupJob {

	static final Duration UNSAVED_TTL = Duration.ofHours(48);

	private final CourseRepository courseRepository;

	@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
	@Transactional
	public void deleteExpiredUnsavedCourses() {
		int deleted = courseRepository.deleteUnsavedCreatedBefore(OffsetDateTime.now(ZoneId.of("Asia/Seoul")).minus(UNSAVED_TTL));
		if (deleted > 0) {
			log.info("임시 코스 {}개 정리", deleted);
		}
	}
}
