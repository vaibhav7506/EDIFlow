package com.vaibhav.ediflow.order.service;

import com.vaibhav.ediflow.events.*; import org.springframework.kafka.core.KafkaTemplate; import org.springframework.stereotype.Component; import java.time.Instant; import java.util.UUID;
@Component public class DeadLetterPublisher { public static final String DLQ="ediflow.dlq.v1"; private final KafkaTemplate<String,Object> kafka; public DeadLetterPublisher(KafkaTemplate<String,Object> kafka){this.kafka=kafka;} public void publish(CanonicalEdiEvent original,String source,String reason){DeadLetterEvent d=new DeadLetterEvent(UUID.randomUUID(),source,original.partnerId(),original.partnerCode(),null,original,reason,source,Instant.now());kafka.send(DLQ,original.partitionKey(),d);} }
