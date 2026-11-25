package com.runtrip.domain.run.repository;

import com.runtrip.domain.run.entity.Run;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, UUID> {

	Optional<Run> findByIdAndUserId(UUID id, Long userId);

	List<Run> findByUserIdOrderByStartedAtDesc(Long userId);
}
