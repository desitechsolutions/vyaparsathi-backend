-- V74: Add round_off column to sales_order — mirrors V73 for quotation.
-- Reason: sales_order.recomputeTotals was persisting to 2 decimals while
-- Sale rounds to whole rupee. UI, DB and PDF got out of step. This aligns
-- the three surfaces so a sales order converted to a sale keeps its total
-- exactly.

ALTER TABLE sales_order
    ADD COLUMN round_off DECIMAL(12,2) NOT NULL DEFAULT 0;
