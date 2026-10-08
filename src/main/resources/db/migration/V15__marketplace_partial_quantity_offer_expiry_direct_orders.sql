-- Partial sales: a listing keeps selling until its available quantity is used up.
ALTER TABLE produce_listing ADD COLUMN IF NOT EXISTS available_quantity DOUBLE PRECISION;
UPDATE produce_listing
   SET available_quantity = CASE WHEN status = 'CLOSED' THEN 0 ELSE quantity END
 WHERE available_quantity IS NULL;
ALTER TABLE produce_listing ALTER COLUMN available_quantity SET NOT NULL;

-- Offers expire so stale offers do not block a farmer forever.
ALTER TABLE marketplace_offer ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_marketplace_offer_status_expiry ON marketplace_offer(status, expires_at);

-- Small orders can skip the internal 11-stage workflow (DIRECT); existing orders stay ASSISTED.
ALTER TABLE market_order ADD COLUMN IF NOT EXISTS fulfilment_mode VARCHAR(20) NOT NULL DEFAULT 'ASSISTED';
