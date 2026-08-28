package com.vaibhav.ediflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "edi_interchanges")
public class EdiInterchange {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "partner_id") private Partner partner;
    @Column(name = "isa_control_number", nullable = false) private String isaControlNumber;
    @Column(name = "gs_control_number", nullable = false) private String gsControlNumber;
    @Column(name = "st_control_number", nullable = false) private String stControlNumber;
    @Column(name = "document_type", nullable = false) private String documentType;
    @Column(name = "received_at", nullable = false) private Instant receivedAt;

    protected EdiInterchange() { }
    public EdiInterchange(Partner partner, String isa, String gs, String st) {
        this.partner = partner; isaControlNumber = isa; gsControlNumber = gs; stControlNumber = st;
        documentType = "PO_850"; receivedAt = Instant.now();
    }
}
