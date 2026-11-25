package com.runtrip.domain.course.entity;

import com.runtrip.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "course_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private CourseType type;

	@Column(nullable = false)
	private BigDecimal distanceKm;

	@Column(nullable = false)
	private Point startPoint;

	@Column(nullable = false)
	private Point endPoint;

	@Column(length = 30)
	private String region;

	@Column(length = 50)
	private String preferences;

	@Column(nullable = false)
	private LineString path;

	private OffsetDateTime savedAt;

	@Column(nullable = false)
	private OffsetDateTime createdAt;

	// 순환 코스 임시 저장. savedAt이 null이면 임시 코스다
	public static Course createLoop(User user, String name, BigDecimal distanceKm, Point start, String region,
			String preferences, LineString path, OffsetDateTime createdAt) {
		return create(CourseType.LOOP, user, name, distanceKm, start, start, region, preferences, path, createdAt);
	}

	// 편도 코스 임시 저장
	public static Course createOneWay(User user, String name, BigDecimal distanceKm, Point start, Point end,
			String region, String preferences, LineString path, OffsetDateTime createdAt) {
		return create(CourseType.ONE_WAY, user, name, distanceKm, start, end, region, preferences, path, createdAt);
	}

	// 자유 러닝 경로로 만든 코스. 경유 장소와 취향이 없다
	public static Course createFree(User user, String name, BigDecimal distanceKm, Point start, Point end,
			String region, LineString path, OffsetDateTime createdAt) {
		return create(CourseType.FREE, user, name, distanceKm, start, end, region, null, path, createdAt);
	}

	// 이미 저장한 코스는 처음 저장 시각을 그대로 둔다 (여러 번 불러도 같은 결과)
	public void save(OffsetDateTime now) {
		if (savedAt == null) {
			savedAt = now;
		}
	}

	// 저장 취소. 임시 코스로 돌아가 48시간 정리 대상이 된다
	public void unsave() {
		savedAt = null;
	}

	private static Course create(CourseType type, User user, String name, BigDecimal distanceKm, Point start,
			Point end, String region, String preferences, LineString path, OffsetDateTime createdAt) {
		Course course = new Course();
		course.user = user;
		course.name = name;
		course.type = type;
		course.distanceKm = distanceKm;
		course.startPoint = start;
		course.endPoint = end;
		course.region = region;
		course.preferences = preferences;
		course.path = path;
		course.createdAt = createdAt;
		return course;
	}
}
