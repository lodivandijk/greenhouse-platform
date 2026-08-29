package com.greenhouse.careloop.command;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommandLifecycleEventRepository extends JpaRepository<CommandLifecycleEvent, Long> {

    List<CommandLifecycleEvent> findAllByCommandIdOrderByOccurredAtAsc(Long commandId);

    Optional<CommandLifecycleEvent> findFirstByCommandIdOrderByOccurredAtDescIdDesc(Long commandId);
}
