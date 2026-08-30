CREATE TABLE partners (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE edi_interchanges (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),
    isa_control_number VARCHAR(50) NOT NULL,
    gs_control_number VARCHAR(50) NOT NULL,
    st_control_number VARCHAR(50) NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    interchange_id UUID NOT NULL UNIQUE REFERENCES edi_interchanges(id),
    partner_id UUID NOT NULL REFERENCES partners(id),
    po_number VARCHAR(100) NOT NULL,
    po_date DATE,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    sku VARCHAR(255) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    unit_of_measure VARCHAR(20) NOT NULL,
    unit_price NUMERIC(19,4) NOT NULL,
    extended_price NUMERIC(19,2) NOT NULL
);
CREATE INDEX idx_purchase_orders_partner_id ON purchase_orders(partner_id);
CREATE INDEX idx_purchase_orders_created_at ON purchase_orders(created_at);
