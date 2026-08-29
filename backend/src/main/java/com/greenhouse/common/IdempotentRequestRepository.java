package com.greenhouse.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotentRequestRepository extends JpaRepository<IdempotentRequest, Long> {

    Optional<IdempotentRequest> findByIdempotencyKey(String idempotencyKey);
}
