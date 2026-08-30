package com.vaibhav.ediflow.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="partners")
public class Partner {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=50) private String code;
    @Column(nullable=false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private PartnerStatus status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected Partner() { }
    public Partner(UUID id, String code, PartnerStatus status) { this.id=id; this.code=code; this.name=code; this.status=status; this.createdAt=Instant.now(); }
    public void activate() { status=PartnerStatus.ACTIVE; }
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public PartnerStatus getStatus(){return status;}
}
