package com.goldstack.sip;

import com.goldstack.common.events.Events.OrderCompleted;
import com.goldstack.common.events.Events.SipDebitFailed;
import com.goldstack.common.events.Events.SipDebitRequested;
import com.goldstack.common.events.Topics;
import com.goldstack.common.outbox.IdempotentConsumer;
import com.goldstack.common.outbox.OutboxWriter;
import com.goldstack.sip.SipDtos.SipRunSummary;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
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
    private final SipRequester requester;

    public SipRunner(SipRepository sips, SipRequester requester) {
        this.sips = sips;
        this.requester = requester;
    }

    public SipRunSummary runDueSips(LocalDate runDate) {
        List<UUID> dueIds = sips.findDueIds(runDate);
        int requested = 0;
        int skipped = 0;
        for (UUID sipId : dueIds) {
            boolean ok;
            try {
                ok = requester.request(sipId, runDate);
            } catch (DataIntegrityViolationException e) {
                ok = false;
            }
            if (ok) {
                requested++;
            } else {
                skipped++;
            }
        }
        SipRunSummary summary = new SipRunSummary(runDate, dueIds.size(), requested, skipped);
        log.info("SIP run finished: {}", summary);
        return summary;
    }
}

@Component
class SipRequester {

    private final SipRepository sips;
    private final SipExecutionRepository executions;
    private final OutboxWriter outbox;
    private final Clock clock;

    SipRequester(SipRepository sips, SipExecutionRepository executions, OutboxWriter outbox, Clock clock) {
        this.sips = sips;
        this.executions = executions;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public boolean request(UUID sipId, LocalDate runDate) {
        Sip sip = sips.lockById(sipId).orElse(null);
        if (sip == null || !sip.isDueOn(runDate)) {
            return false;
        }
        String key = sipId + ":" + runDate;
        if (executions.existsByIdempotencyKey(key)) {
            return false;
        }
        Instant now = Instant.now(clock);
        executions.saveAndFlush(SipExecution.pending(sipId, runDate, key, now));
        sip.advancePast(runDate, now);
        outbox.write(Topics.SIP_DEBIT_REQUESTED, sip.getUserId().toString(), new SipDebitRequested(UUID.randomUUID(),
                now, sipId, sip.getUserId(), sip.getAmount(), runDate, "sip:" + key));
        return true;
    }

    @Transactional
    public void onOrderCompleted(OrderCompleted e) {
        if (!"SIP".equals(e.source()) || e.sipId() == null) {
            return;
        }
        executions.findByIdempotencyKey(e.sipId() + ":" + e.runDate())
                .ifPresent(x -> x.succeed(e.orderId(), Instant.now(clock)));
    }

    @Transactional
    public void onDebitFailed(SipDebitFailed e) {
        executions.findByIdempotencyKey(e.sipId() + ":" + e.runDate())
                .ifPresent(x -> x.fail(e.orderId(), e.reason(), Instant.now(clock)));
    }
}

@Component
class SipEventListeners {

    private final IdempotentConsumer consumer;
    private final SipRequester requester;

    SipEventListeners(IdempotentConsumer consumer, SipRequester requester) {
        this.consumer = consumer;
        this.requester = requester;
    }

    @KafkaListener(topics = Topics.ORDER_COMPLETED)
    void onOrderCompleted(String payload) {
        consumer.handle(Topics.ORDER_COMPLETED, payload, OrderCompleted.class, requester::onOrderCompleted);
    }

    @KafkaListener(topics = Topics.SIP_DEBIT_FAILED)
    void onDebitFailed(String payload) {
        consumer.handle(Topics.SIP_DEBIT_FAILED, payload, SipDebitFailed.class, requester::onDebitFailed);
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
    @SchedulerLock(name = "sipDailyRun", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void runDaily() {
        runner.runDueSips(LocalDate.now(clock));
    }
}
