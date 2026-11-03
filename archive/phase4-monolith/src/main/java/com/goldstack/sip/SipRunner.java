package com.goldstack.sip;

import com.goldstack.common.error.ApiException;
import com.goldstack.common.error.InsufficientFundsException;
import com.goldstack.order.OrderService;
import com.goldstack.sip.SipDtos.SipDebitFailedEvent;
import com.goldstack.sip.SipDtos.SipRunSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SipRunner {

    private static final Logger log = LoggerFactory.getLogger(SipRunner.class);

    private final SipRepository sips;
    private final SipExecutionRepository executions;
    private final SipExecutionRecorder recorder;
    private final OrderService orderService;
    private final ApplicationEventPublisher events;

    public SipRunner(SipRepository sips, SipExecutionRepository executions, SipExecutionRecorder recorder,
                     OrderService orderService, ApplicationEventPublisher events) {
        this.sips = sips;
        this.executions = executions;
        this.recorder = recorder;
        this.orderService = orderService;
        this.events = events;
    }

    public SipRunSummary runDueSips(LocalDate runDate) {
        List<UUID> dueIds = sips.findDueIds(runDate);
        int ok = 0;
        int failed = 0;
        int skipped = 0;
        for (UUID sipId : dueIds) {
            switch (processOne(sipId, runDate)) {
                case SUCCESS -> ok++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }
        SipRunSummary summary = new SipRunSummary(runDate, dueIds.size(), ok, failed, skipped);
        log.info("SIP run finished: {}", summary);
        return summary;
    }

    private enum Outcome { SUCCESS, FAILED, SKIPPED }

    private Outcome processOne(UUID sipId, LocalDate runDate) {
        Sip sip = sips.findById(sipId).orElse(null);
        if (sip == null || !sip.isDueOn(runDate)) {
            return Outcome.SKIPPED;
        }
        String key = sipId + ":" + runDate;
        if (executions.existsByIdempotencyKey(key)) {
            return Outcome.SKIPPED;
        }
        try {
            UUID orderId = orderService.buyForSip(sip.getUserId(), sip.getAmount(), "sip:" + key).order().id();
            recorder.record(sipId, runDate, key, orderId, null);
            return Outcome.SUCCESS;
        } catch (DataIntegrityViolationException e) {
            return Outcome.SKIPPED;
        } catch (InsufficientFundsException e) {

            return fail(sip, runDate, key, "Insufficient balance: " + e.getMessage());
        } catch (ApiException e) {
            return fail(sip, runDate, key, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error running SIP {} for {}", sipId, runDate, e);
            return fail(sip, runDate, key, "Unexpected error");
        }
    }

    private Outcome fail(Sip sip, LocalDate runDate, String key, String reason) {
        try {
            recorder.record(sip.getId(), runDate, key, null, reason);
        } catch (DataIntegrityViolationException e) {
            return Outcome.SKIPPED;
        }
        events.publishEvent(new SipDebitFailedEvent(sip.getId(), sip.getUserId(), sip.getAmount(), runDate, reason));
        return Outcome.FAILED;
    }
}

@Component
class SipExecutionRecorder {

    private final SipRepository sips;
    private final SipExecutionRepository executions;
    private final Clock clock;

    SipExecutionRecorder(SipRepository sips, SipExecutionRepository executions, Clock clock) {
        this.sips = sips;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID sipId, LocalDate runDate, String key, UUID orderId, String failureReason) {
        Instant now = Instant.now(clock);
        SipExecution.Status status = orderId != null ? SipExecution.Status.SUCCESS : SipExecution.Status.FAILED;
        executions.saveAndFlush(new SipExecution(sipId, runDate, key, orderId, status, truncate(failureReason), now));

        sips.findById(sipId).ifPresent(s -> s.advancePast(runDate, now));
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 255);
    }
}

@Component
class SipScheduler {

    private final SipRunner runner;
    private final Clock clock;

    SipScheduler(SipRunner runner, Clock clock) {
        this.runner = runner;
        this.clock = clock;
    }

    @Scheduled(cron = "${goldstack.sip.cron}", zone = "${goldstack.zone}")
    void runDaily() {
        runner.runDueSips(LocalDate.now(clock));
    }
}
