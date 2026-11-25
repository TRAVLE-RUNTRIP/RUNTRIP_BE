package com.runtrip.domain.run.service;

import com.runtrip.domain.course.entity.Course;
import com.runtrip.domain.course.repository.CourseRepository;
import com.runtrip.domain.location.service.SeoulRegionResolver;
import com.runtrip.domain.run.dto.RunCreateRequest;
import com.runtrip.domain.run.dto.RunCreateResponse;
import com.runtrip.domain.run.dto.RunListResponse;
import com.runtrip.domain.run.dto.RunListResponse.RunItem;
import com.runtrip.domain.run.entity.Run;
import com.runtrip.domain.run.entity.RunType;
import com.runtrip.domain.run.repository.RunRepository;
import com.runtrip.domain.user.entity.User;
import com.runtrip.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

// 러닝 기록 저장·조회 (API 명세 11·12번)
@Slf4j
@Service
@RequiredArgsConstructor
public class RunService {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter FREE_COURSE_DATE = DateTimeFormatter.ofPattern("M월 d일");
	private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

	private final RunRepository runRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final SeoulRegionResolver seoulRegionResolver;

	// created가 false면 같은 runId로 이미 저장한 기록이다 (200으로 같은 내용)
	public record CreateResult(RunCreateResponse response, boolean created) {
	}

	@Transactional
	public CreateResult create(Long userId, RunCreateRequest request) {
		Run existing = runRepository.findByIdAndUserId(request.runId(), userId).orElse(null);
		if (existing != null) {
			return new CreateResult(toResponse(existing), false);
		}

		// DB(timestamptz)는 마이크로초까지 저장한다. 다시 보낸 요청의 응답과 맞추려고 자른다
		OffsetDateTime now = OffsetDateTime.now(SEOUL).truncatedTo(ChronoUnit.MICROS);
		User user = userRepository.getReferenceById(userId);
		Course course = request.type() == RunType.COURSE
				? courseRun(userId, request, now)
				: freeRun(user, request, now);

		Run run = runRepository.save(Run.create(request.runId(), user, course, request.type(), request.title().strip(),
				request.startedAt(), request.endedAt(), request.distanceKm(), request.durationSec(),
				request.avgPaceSec(), request.segments()));
		return new CreateResult(toResponse(run), true);
	}

	@Transactional(readOnly = true)
	public RunListResponse list(Long userId) {
		return new RunListResponse(runRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
				.map(run -> new RunItem(run.getId(), run.getType().name(), run.getTitle(), run.getStartedAt(),
						run.getDurationSec(), run.getAvgPaceSec(), run.getDistanceKm()))
				.toList());
	}

	// 내 코스가 정리됐거나(48시간) 남의 코스면 기록만 저장한다
	private Course courseRun(Long userId, RunCreateRequest request, OffsetDateTime now) {
		Course course = courseRepository.findByIdAndUserId(request.courseId(), userId).orElse(null);
		if (course != null && request.saveCourse()) {
			course.save(now);
		}
		return course;
	}

	// 자유 러닝 경로를 새 코스로 저장. 끊긴 구간은 달린 순서대로 이어 붙인다 (끊긴 곳은 직선)
	private Course freeRun(User user, RunCreateRequest request, OffsetDateTime now) {
		if (!request.saveCourse()) {
			return null;
		}
		Coordinate[] points = request.segments().stream()
				.filter(Objects::nonNull)
				.flatMap(List::stream)
				.map(p -> new Coordinate(p.get(1), p.get(0)))
				.toArray(Coordinate[]::new);
		Coordinate first = points[0];
		Coordinate last = points[points.length - 1];
		String name = request.startedAt().atZoneSameInstant(SEOUL).format(FREE_COURSE_DATE) + " 자유 러닝 코스";

		Course course = Course.createFree(user, name, request.distanceKm(), GEOMETRY.createPoint(first),
				GEOMETRY.createPoint(last), region(first), GEOMETRY.createLineString(points), now);
		course.save(now);
		return courseRepository.save(course);
	}

	// 서울 밖이거나 T map 조회에 실패하면 null. 기록 저장은 막지 않는다
	private String region(Coordinate start) {
		try {
			return seoulRegionResolver.findSeoulRegion(start.getY(), start.getX()).orElse(null);
		} catch (RestClientException e) {
			log.warn("자유 러닝 코스 지역 조회 실패: {}", e.getMessage());
			return null;
		}
	}

	private RunCreateResponse toResponse(Run run) {
		Course course = run.getCourse();
		return new RunCreateResponse(run.getId(), course != null && course.getSavedAt() != null,
				course != null ? course.getId() : null);
	}
}
