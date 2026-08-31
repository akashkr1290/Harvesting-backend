-- The CaseStatus enum's AWAITING_PLOT_SELECTION value (the entry point for
-- marketplace orders handed to the internal workflow) was added in code
-- without updating this CHECK constraint, so inserts failed at runtime.
ALTER TABLE harvest_case DROP CONSTRAINT IF EXISTS harvest_case_status_check;
ALTER TABLE harvest_case ADD CONSTRAINT harvest_case_status_check
  CHECK (status::text = ANY (ARRAY[
    'AWAITING_PLOT_SELECTION','SUBMITTED_FOR_PLANNING','PLANNED','RATE_UPDATED',
    'PACKING_MATERIAL_ISSUED','HARVESTING_COMPLETED','PICKUP_ASSIGNED',
    'TRANSPORT_ASSIGNED','COST_ENTRIES_DONE','MATERIAL_RECONCILED',
    'PURCHASE_COMPLETED','SALES_COMPLETED'
  ]::character varying[]));
