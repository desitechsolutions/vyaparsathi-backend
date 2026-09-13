-- Phase 5: Audit Trail — who created / last modified each financial document.
-- These columns are populated by Spring Data JPA AuditingEntityListener via
-- AuditableFinancialEntity (@CreatedBy / @LastModifiedBy).  Existing rows will
-- have NULL until the next write touches them — that is expected and safe.
ALTER TABLE sale              ADD COLUMN created_by VARCHAR(255) NULL;
ALTER TABLE sale              ADD COLUMN updated_by  VARCHAR(255) NULL;
ALTER TABLE sale_item         ADD COLUMN created_by VARCHAR(255) NULL;
ALTER TABLE sale_item         ADD COLUMN updated_by  VARCHAR(255) NULL;
ALTER TABLE purchase_invoices ADD COLUMN created_by VARCHAR(255) NULL;
ALTER TABLE purchase_invoices ADD COLUMN updated_by  VARCHAR(255) NULL;
ALTER TABLE credit_notes      ADD COLUMN created_by VARCHAR(255) NULL;
ALTER TABLE credit_notes      ADD COLUMN updated_by  VARCHAR(255) NULL;
ALTER TABLE debit_notes       ADD COLUMN created_by VARCHAR(255) NULL;
ALTER TABLE debit_notes       ADD COLUMN updated_by  VARCHAR(255) NULL;
