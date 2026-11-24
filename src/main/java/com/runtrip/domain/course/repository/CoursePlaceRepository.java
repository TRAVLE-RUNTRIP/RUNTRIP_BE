package com.runtrip.domain.course.repository;

import com.runtrip.domain.course.entity.CoursePlace;
import com.runtrip.domain.course.entity.CoursePlaceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoursePlaceRepository extends JpaRepository<CoursePlace, CoursePlaceId> {
}
