package org.ticketstampede.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketstampede.entity.ScheduledTask;
import org.ticketstampede.entity.ScheduledTaskType;

import java.util.Optional;
import java.util.UUID;

public interface ScheduleTaskRepository extends JpaRepository<ScheduledTask, UUID> {
    Optional<ScheduledTask> findByScheduledTaskTypeAndReferenceId(
            ScheduledTaskType scheduledTaskType,
            UUID referenceId
    );
}
