package com.vaibhav.ediflow.order.observability;

import com.vaibhav.ediflow.order.domain.DeadLetterStatus;
import com.vaibhav.ediflow.order.repository.DeadLetterRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterMetrics {
    public DeadLetterMetrics(MeterRegistry registry, DeadLetterRepository records) {
        Gauge.builder("ediflow.dead.letter.queue.depth", records,
                repository -> repository.countByStatus(DeadLetterStatus.OPEN))
            .description("Open EDIFlow dead-letter records")
            .register(registry);
    }
}
