package com.runtrip.domain.course.entity;

import com.runtrip.domain.place.entity.Place;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "course_places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoursePlace {

	@EmbeddedId
	private CoursePlaceId id;

	@MapsId("courseId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@MapsId("placeId")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "place_id", nullable = false)
	private Place place;

	@Column(nullable = false)
	private short visitOrder;

	@Column(nullable = false)
	private Point routePoint;

	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private ReasonSource reasonSource;
}
