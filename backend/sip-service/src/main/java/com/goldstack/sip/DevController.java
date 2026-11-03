package com.goldstack.sip;

import com.goldstack.sip.SipDtos.SipRunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/dev")
@Tag(name = "Dev tools (disable in production)")
@ConditionalOnProperty(name = "goldstack.dev-endpoints.enabled", havingValue = "true")
public class DevController {

    private final SipRunner sipRunner;
    private final Clock clock;

    public DevController(SipRunner sipRunner, Clock clock) {
        this.sipRunner = sipRunner;
        this.clock = clock;
    }

    @PostMapping("/sips/run")
    @Operation(summary = "Run the SIP job now, as if today were 'date' (default: today)")
    public SipRunSummary runSips(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                 LocalDate date) {
        return sipRunner.runDueSips(date != null ? date : LocalDate.now(clock));
    }
}
