package com.vaibhav.ediflow.order.api;

import com.vaibhav.ediflow.order.domain.DeadLetterStatus;
import com.vaibhav.ediflow.order.repository.DeadLetterRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsSummaryController {
    private final MeterRegistry metrics;
    private final DeadLetterRepository deadLetters;

    public MetricsSummaryController(MeterRegistry metrics, DeadLetterRepository deadLetters) {
        this.metrics = metrics;
        this.deadLetters = deadLetters;
    }

    @GetMapping("/summary")
    public Summary summary() {
        var lag = metrics.find("ediflow.kafka.consumer.lag").gauge();
        return new Summary(lag == null ? 0 : Math.round(lag.value()), deadLetters.countByStatus(DeadLetterStatus.OPEN));
    }

    record Summary(long kafkaLag, long deadLetterDepth) { }
}
