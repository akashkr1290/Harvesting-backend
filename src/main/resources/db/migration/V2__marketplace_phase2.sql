-- V2__marketplace_phase2.sql
--
-- Phase 2: marketplace tables (produce_listing, its images, offers, and
-- orders), hand-written to match domain/entity/{ProduceListing,Offer,
-- MarketOrder}.java the same way V1 matches the original entities.
-- ddl-auto stays 'validate' — see V1's header comment.

-- ---------------------------------------------------------------------
-- produce_listing
-- ---------------------------------------------------------------------
CREATE TABLE produce_listing (
    id                       UUID PRIMARY KEY,
    seller_id                UUID NOT NULL REFERENCES app_user(id),
    seller_role              VARCHAR(255) NOT NULL,
    seller_name              VARCHAR(255) NOT NULL,
    crop_name                VARCHAR(255) NOT NULL,
    quantity                 DOUBLE PRECISION NOT NULL,
    unit                     VARCHAR(255) NOT NULL,
    quality                  VARCHAR(255),
    harvest_date             DATE NOT NULL,
    location                 VARCHAR(255) NOT NULL,
    expected_price_per_unit  DOUBLE PRECISION NOT NULL,
    description              VARCHAR(2000),
    status                   VARCHAR(255) NOT NULL,
    primary_image_index      INTEGER,
    verification_status      VARCHAR(255) NOT NULL DEFAULT 'NOT_SUBMITTED',
    created_at               TIMESTAMPTZ NOT NULL,
    published_at             TIMESTAMPTZ,
    closed_at                TIMESTAMPTZ
);

CREATE INDEX idx_produce_listing_seller ON produce_listing(seller_id);
CREATE INDEX idx_produce_listing_status ON produce_listing(status);

-- ---------------------------------------------------------------------
-- produce_listing_images — @ElementCollection backing table
-- ---------------------------------------------------------------------
CREATE TABLE produce_listing_images (
    listing_id  UUID NOT NULL REFERENCES produce_listing(id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    image_url   VARCHAR(255) NOT NULL,
    PRIMARY KEY (listing_id, position)
);

-- ---------------------------------------------------------------------
-- marketplace_offer
-- ---------------------------------------------------------------------
CREATE TABLE marketplace_offer (
    id              UUID PRIMARY KEY,
    listing_id      UUID NOT NULL REFERENCES produce_listing(id),
    buyer_id        UUID NOT NULL REFERENCES app_user(id),
    buyer_role      VARCHAR(255) NOT NULL,
    buyer_name      VARCHAR(255) NOT NULL,
    quantity        DOUBLE PRECISION NOT NULL,
    price_per_unit  DOUBLE PRECISION NOT NULL,
    message         VARCHAR(1000),
    status          VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ
);

CREATE INDEX idx_marketplace_offer_listing ON marketplace_offer(listing_id);
CREATE INDEX idx_marketplace_offer_buyer ON marketplace_offer(buyer_id);

-- ---------------------------------------------------------------------
-- market_order
-- ---------------------------------------------------------------------
CREATE TABLE market_order (
    id                       UUID PRIMARY KEY,
    offer_id                 UUID NOT NULL UNIQUE REFERENCES marketplace_offer(id),
    listing_id               UUID NOT NULL REFERENCES produce_listing(id),
    seller_id                UUID NOT NULL REFERENCES app_user(id),
    buyer_id                 UUID NOT NULL REFERENCES app_user(id),
    crop_name                VARCHAR(255) NOT NULL,
    quantity                 DOUBLE PRECISION NOT NULL,
    agreed_price_per_unit    DOUBLE PRECISION NOT NULL,
    total_amount             DOUBLE PRECISION NOT NULL,
    status                   VARCHAR(255) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_market_order_seller ON market_order(seller_id);
CREATE INDEX idx_market_order_buyer ON market_order(buyer_id);
