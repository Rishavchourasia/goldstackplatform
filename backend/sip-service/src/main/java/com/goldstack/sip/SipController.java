package com.goldstack.sip;

import com.goldstack.common.security.AuthUser;
import com.goldstack.common.web.PageResponse;
import com.goldstack.sip.SipDtos.CreateSipRequest;
import com.goldstack.sip.SipDtos.SipExecutionResponse;
import com.goldstack.sip.SipDtos.SipResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sips")
@Tag(name = "SIP (auto-invest)")
public class SipController {

    private final SipService sipService;

    public SipController(SipService sipService) {
        this.sipService = sipService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a SIP: buy 'amount' coins of gold every DAILY/WEEKLY/MONTHLY from startDate")
    public SipResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateSipRequest req) {
        return sipService.create(user.id(), req);
    }

    @GetMapping
    @Operation(summary = "My SIPs")
    public List<SipResponse> list(@AuthenticationPrincipal AuthUser user) {
        return sipService.list(user.id());
    }

    @GetMapping("/{id}")
    public SipResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return sipService.get(user.id(), id);
    }

    @PostMapping("/{id}/pause")
    public SipResponse pause(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return sipService.pause(user.id(), id);
    }

    @PostMapping("/{id}/resume")
    public SipResponse resume(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return sipService.resume(user.id(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel permanently (history is kept)")
    public SipResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return sipService.cancel(user.id(), id);
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "Installment history of one SIP (success/failed), newest first")
    public PageResponse<SipExecutionResponse> executions(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return sipService.executions(user.id(), id, page, size);
    }
}
