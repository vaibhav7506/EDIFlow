ALTER TABLE edi_interchanges ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED';
CREATE INDEX idx_interchange_dedupe ON edi_interchanges(partner_id, isa_control_number, st_control_number);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    interchange_id UUID NOT NULL UNIQUE REFERENCES edi_interchanges(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    invoice_number VARCHAR(100) NOT NULL,
    invoice_date DATE NOT NULL,
    referenced_po_number VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    sku VARCHAR(255) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL
);
CREATE TABLE processing_events (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    from_state VARCHAR(30),
    to_state VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_processing_events_entity ON processing_events(entity_type, entity_id, occurred_at);
CREATE TABLE dead_letter_records (
    id UUID PRIMARY KEY,
    source_topic VARCHAR(255) NOT NULL,
    partner_id UUID,
    partner_code VARCHAR(50),
    raw_payload TEXT,
    canonical_event TEXT,
    error_reason VARCHAR(2000) NOT NULL,
    retry_topic VARCHAR(255) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
