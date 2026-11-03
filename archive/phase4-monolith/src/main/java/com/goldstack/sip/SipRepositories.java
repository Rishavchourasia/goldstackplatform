package com.goldstack.sip;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SipRepository extends JpaRepository<Sip, UUID> {

    List<Sip> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Sip> findByIdAndUserId(UUID id, UUID userId);

    @Query("select s.id from Sip s where s.status = com.goldstack.sip.Sip.Status.ACTIVE and s.nextRunDate <= :date")
    List<UUID> findDueIds(@Param("date") LocalDate date);
}

interface SipExecutionRepository extends JpaRepository<SipExecution, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<SipExecution> findBySipId(UUID sipId, Pageable pageable);
}
