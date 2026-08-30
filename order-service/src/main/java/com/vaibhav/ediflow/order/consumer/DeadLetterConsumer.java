package com.vaibhav.ediflow.order.consumer;
import com.vaibhav.ediflow.events.DeadLetterEvent; import com.vaibhav.ediflow.order.service.DeadLetterService; import org.springframework.kafka.annotation.KafkaListener; import org.springframework.stereotype.Component;
@Component public class DeadLetterConsumer {private final DeadLetterService service;public DeadLetterConsumer(DeadLetterService s){service=s;}@KafkaListener(topics="ediflow.dlq.v1",groupId="ediflow-dlq-persistence") public void consume(DeadLetterEvent event){service.record(event);}}
