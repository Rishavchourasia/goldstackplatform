package com.goldstack.sip;

import com.goldstack.common.Money;
import com.goldstack.common.error.ApiException;
import com.goldstack.common.web.PageResponse;
import com.goldstack.common.web.Paging;
import com.goldstack.sip.SipDtos.CreateSipRequest;
import com.goldstack.sip.SipDtos.SipExecutionResponse;
import com.goldstack.sip.SipDtos.SipResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SipService {

    private final SipRepository sips;
    private final SipExecutionRepository executions;
    private final Clock clock;

    public SipService(SipRepository sips, SipExecutionRepository executions, Clock clock) {
        this.sips = sips;
        this.executions = executions;
        this.clock = clock;
    }

    @Transactional
    public SipResponse create(UUID userId, CreateSipRequest req) {
        if (req.startDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("START_DATE_IN_PAST", "startDate must be today or later");
        }
        Sip sip = sips.save(new Sip(userId, Money.coins(req.amount()), req.frequency(), req.startDate(),
                Instant.now(clock)));
        return SipResponse.from(sip);
    }

    @Transactional(readOnly = true)
    public List<SipResponse> list(UUID userId) {
        return sips.findByUserIdOrderByCreatedAtDesc(userId).stream().map(SipResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SipResponse get(UUID userId, UUID sipId) {
        return SipResponse.from(owned(userId, sipId));
    }

    @Transactional
    public SipResponse pause(UUID userId, UUID sipId) {
        Sip sip = owned(userId, sipId);
        sip.pause(Instant.now(clock));
        return SipResponse.from(sip);
    }

    @Transactional
    public SipResponse resume(UUID userId, UUID sipId) {
        Sip sip = owned(userId, sipId);
        sip.resume(LocalDate.now(clock), Instant.now(clock));
        return SipResponse.from(sip);
    }

    @Transactional
    public SipResponse cancel(UUID userId, UUID sipId) {
        Sip sip = owned(userId, sipId);
        sip.cancel(Instant.now(clock));
        return SipResponse.from(sip);
    }

    @Transactional(readOnly = true)
    public PageResponse<SipExecutionResponse> executions(UUID userId, UUID sipId, int page, int size) {
        owned(userId, sipId);
        return PageResponse.of(executions.findBySipId(sipId, Paging.newestFirst(page, size, "runDate")),
                SipExecutionResponse::from);
    }

    private Sip owned(UUID userId, UUID sipId) {
        return sips.findByIdAndUserId(sipId, userId).orElseThrow(() -> ApiException.notFound("SIP"));
    }
}
