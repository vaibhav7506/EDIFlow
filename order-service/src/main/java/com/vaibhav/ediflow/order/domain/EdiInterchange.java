package com.vaibhav.ediflow.order.domain;

import com.vaibhav.ediflow.events.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="edi_interchanges")
public class EdiInterchange {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false) @JoinColumn(name="partner_id") private Partner partner;
    @Column(name="isa_control_number",nullable=false) private String isaControlNumber;
    @Column(name="gs_control_number",nullable=false) private String gsControlNumber;
    @Column(name="st_control_number",nullable=false) private String stControlNumber;
    @Enumerated(EnumType.STRING) @Column(name="document_type",nullable=false) private DocumentType documentType;
    @Column(name="received_at",nullable=false) private Instant receivedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private InterchangeStatus status;
    protected EdiInterchange() { }
    public EdiInterchange(Partner partner, CanonicalEdiEvent e, InterchangeStatus status){this.partner=partner;isaControlNumber=e.isaControlNumber();gsControlNumber=e.gsControlNumber();stControlNumber=e.stControlNumber();documentType=e.documentType();receivedAt=e.receivedAt();this.status=status;}
    public void status(InterchangeStatus value){status=value;} public UUID getId(){return id;} public InterchangeStatus getStatus(){return status;} public Partner getPartner(){return partner;} public String getIsaControlNumber(){return isaControlNumber;} public String getStControlNumber(){return stControlNumber;} public DocumentType getDocumentType(){return documentType;} public Instant getReceivedAt(){return receivedAt;}
}
