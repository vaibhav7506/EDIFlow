package com.vaibhav.ediflow.order.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="processing_events") public class ProcessingEvent {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @Column(name="entity_type",nullable=false) private String entityType; @Column(name="entity_id",nullable=false) private UUID entityId; @Column(name="from_state") private String fromState; @Column(name="to_state",nullable=false) private String toState; private String reason; @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 protected ProcessingEvent(){} public ProcessingEvent(String type,UUID id,String from,String to,String reason){entityType=type;entityId=id;fromState=from;toState=to;this.reason=reason;occurredAt=Instant.now();} public UUID getId(){return id;} public String getEntityType(){return entityType;} public UUID getEntityId(){return entityId;} public String getFromState(){return fromState;} public String getToState(){return toState;} public String getReason(){return reason;} public Instant getOccurredAt(){return occurredAt;}
}
