-- V73: Add round_off column to quotation, mirroring Sale's rounding-to-rupee flow.
-- Reason: quotation was persisting grand totals at 2-decimal precision while
-- Sale rounded to whole rupees. The on-screen editor summary already rounded,
-- so numbers on Save and PDF diverged from what the user saw. This aligns the
-- three surfaces (UI, DB, PDF) on the same total.

ALTER TABLE quotation
    ADD COLUMN round_off DECIMAL(12,2) NOT NULL DEFAULT 0;
