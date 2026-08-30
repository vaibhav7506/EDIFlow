DROP INDEX IF EXISTS idx_interchange_dedupe;

DELETE FROM edi_interchanges
WHERE status = 'DUPLICATE';

ALTER TABLE edi_interchanges
    ADD CONSTRAINT uq_edi_interchange_control_numbers
    UNIQUE (partner_id, isa_control_number, st_control_number);
