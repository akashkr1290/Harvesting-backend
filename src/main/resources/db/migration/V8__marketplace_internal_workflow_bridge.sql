-- V7: connect an accepted marketplace order to the existing internal HarvestCase.
ALTER TABLE harvest_case
    ADD COLUMN market_order_id UUID;

ALTER TABLE harvest_case
    ADD CONSTRAINT fk_harvest_case_market_order
    FOREIGN KEY (market_order_id) REFERENCES market_order(id);

CREATE UNIQUE INDEX uq_harvest_case_market_order
    ON harvest_case(market_order_id)
    WHERE market_order_id IS NOT NULL;

ALTER TABLE market_order
    ADD COLUMN delivered_at TIMESTAMPTZ;
