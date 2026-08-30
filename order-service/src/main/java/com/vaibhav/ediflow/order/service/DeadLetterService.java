package com.vaibhav.ediflow.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhav.ediflow.events.*;
import com.vaibhav.ediflow.order.domain.DeadLetterRecord;
import com.vaibhav.ediflow.order.repository.DeadLetterRepository;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class DeadLetterService {
    private final DeadLetterRepository records; private final ObjectMapper json; private final KafkaTemplate<String,Object> kafka;
    public DeadLetterService(DeadLetterRepository records,ObjectMapper json,KafkaTemplate<String,Object> kafka){this.records=records;this.json=json;this.kafka=kafka;}
    @Transactional public void record(DeadLetterEvent e){records.save(new DeadLetterRecord(e.eventId(),e.sourceTopic(),e.partnerId(),e.partnerCode(),e.rawPayload(),write(e.canonicalEvent()),e.errorReason(),e.retryTopic(),e.createdAt()));}
    @Transactional(readOnly=true) public Page<DeadLetterRecord> list(Pageable p){return records.findAll(p);}
    @Transactional public DeadLetterRecord retry(UUID id){DeadLetterRecord record=records.findById(id).orElseThrow(()->new IllegalArgumentException("Dead letter "+id+" was not found."));if(record.getRawPayload()!=null){kafka.send(record.getRetryTopic(),id.toString(),new RawEdiRetryEvent(UUID.randomUUID(),record.getRawPayload()));}else{CanonicalEdiEvent event=read(record.getCanonicalEvent());kafka.send(record.getRetryTopic(),event.partitionKey(),event);}record.retried();return record;}
    private String write(CanonicalEdiEvent event){if(event==null)return null;try{return json.writeValueAsString(event);}catch(JsonProcessingException e){throw new IllegalStateException("Could not serialize canonical event.",e);}}
    private CanonicalEdiEvent read(String value){try{return json.readValue(value,CanonicalEdiEvent.class);}catch(JsonProcessingException e){throw new IllegalStateException("Could not deserialize canonical event.",e);}}
}
