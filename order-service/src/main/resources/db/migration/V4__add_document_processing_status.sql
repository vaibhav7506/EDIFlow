CREATE TABLE document_processing_statuses (
    event_id UUID PRIMARY KEY,
    document_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    detail VARCHAR(1000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
