package com.runtrip.domain.run.entity;

import com.runtrip.domain.course.entity.Course;
import com.runtrip.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Run {

	@Id
	@Column(name = "run_id")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_id")
	private Course course;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private RunType type;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private OffsetDateTime startedAt;

	@Column(nullable = false)
	private OffsetDateTime endedAt;

	@Column(nullable = false)
	private BigDecimal distanceKm;

	@Column(nullable = false)
	private int durationSec;

	@Column(nullable = false)
	private int avgPaceSec;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<List<List<Double>>> segments;
}
