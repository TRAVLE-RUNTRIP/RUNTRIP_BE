package com.runtrip.domain.course.repository;

import com.runtrip.domain.course.entity.Course;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

	// 다른 계정 코스는 없는 코스와 똑같이 다룬다
	Optional<Course> findByIdAndUserId(Long id, Long userId);

	List<Course> findByUserIdAndSavedAtIsNotNullOrderBySavedAtDesc(Long userId);

	// 오래된 임시 코스 정리. course_places는 ON DELETE CASCADE, runs.course_id는 SET NULL (DB 제약)
	@Modifying
	@Query("delete from Course c where c.savedAt is null and c.createdAt < :cutoff")
	int deleteUnsavedCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
