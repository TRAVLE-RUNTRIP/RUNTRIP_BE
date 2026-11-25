package com.runtrip.domain.course.repository;

import com.runtrip.domain.course.entity.CoursePlace;
import com.runtrip.domain.course.entity.CoursePlaceId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoursePlaceRepository extends JpaRepository<CoursePlace, CoursePlaceId> {

	@Query("select cp from CoursePlace cp join fetch cp.place where cp.course.id = :courseId order by cp.visitOrder")
	List<CoursePlace> findWithPlaceByCourseId(@Param("courseId") Long courseId);
}
