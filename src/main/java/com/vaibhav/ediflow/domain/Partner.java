package com.vaibhav.ediflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partners")
public class Partner {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PartnerStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Partner() { }
    public Partner(String code) { this.code = code; this.name = code; this.status = PartnerStatus.PENDING; this.createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public PartnerStatus getStatus() { return status; }
}
