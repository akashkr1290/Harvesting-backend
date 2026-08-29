-- V6__phase5_integration.sql
--
-- Phase 5 (final integration): connects the three previously-independent
-- branches (Phase 2 marketplace, Phase 3 logistics/AI, Phase 4 image
-- verification/payments) now that all their tables exist in one schema.
-- Additive only — no existing column type is changed, no existing row's
-- meaning changes, nothing is dropped.

-- ---------------------------------------------------------------------
-- produce_image.listing_id: was an unconstrained UUID (Phase 4 docs,
-- §7.3) because produce_listing did not exist in that branch. It exists
-- now (V2__marketplace_phase2.sql), so add the FK exactly as Phase 4's
-- own integration notes specified. Left NULLable: an image can still be
-- attached only to a HarvestCase (case_id) with no listing yet.
-- ---------------------------------------------------------------------
ALTER TABLE produce_image
    ADD CONSTRAINT fk_produce_image_listing FOREIGN KEY (listing_id) REFERENCES produce_listing(id);

-- ---------------------------------------------------------------------
-- route_optimization_result: Phase 3 linked a route run only to case_id
-- ("the marketplace order of this app", per that branch's own migration
-- comment, written before a real MarketplaceOrder/market_order existed).
-- Add an equivalent, equally-optional link to the real market_order now
-- that Phase 2's table exists, so Part 11 of the SIH flow (confirmed
-- order -> logistics -> route optimization) can be represented directly
-- instead of only through a HarvestCase proxy. case_id is left in place
-- unchanged for the existing internal-ops route-optimization use case
-- (Phase 3 also allows optimizing a pickup route for internal logistics
-- that never touches the marketplace at all).
-- ---------------------------------------------------------------------
ALTER TABLE route_optimization_result
    ADD COLUMN market_order_id UUID,
    ADD CONSTRAINT fk_route_result_market_order FOREIGN KEY (market_order_id) REFERENCES market_order(id);

CREATE INDEX idx_route_result_market_order ON route_optimization_result (market_order_id);

-- ---------------------------------------------------------------------
-- payment_record.reference_type: Phase 4 only knew about PURCHASE_INVOICE
-- and SALES_INVOICE (no Order entity existed in that branch — see
-- PaymentReferenceType's own javadoc). No column-type change is needed
-- (reference_type is a plain VARCHAR, not a DB enum), so the new
-- MARKET_ORDER value introduced in PaymentReferenceType.java (Phase 5)
-- is already valid without a migration — noted here only so the schema
-- history explains why new values start appearing in that column.
-- ---------------------------------------------------------------------
