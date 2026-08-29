-- V2__produce_image.sql
--
-- Phase 4: crop/produce image upload + AI authenticity verification.
-- Additive only — no existing table is touched, so this cannot affect the
-- existing HarvestFlow workflow (Plot Selection through Sales) in any way.
--
-- caseId/listingId are both plain UUID columns, not foreign keys to a
-- 'produce_listing' table, because that table does not exist in this
-- codebase (see ProduceImage entity javadoc / docs/phase4-integration-notes.md).
-- caseId IS backed by a real FK to harvest_case since that table exists today.

CREATE TABLE produce_image (
    id                     UUID PRIMARY KEY,
    case_id                UUID REFERENCES harvest_case(id),
    listing_id             UUID,
    uploaded_by_username   VARCHAR(255) NOT NULL,
    storage_key            VARCHAR(500) NOT NULL,
    original_filename      VARCHAR(500),
    content_type           VARCHAR(100) NOT NULL,
    file_size_bytes        BIGINT NOT NULL,
    pixel_width            INTEGER,
    pixel_height           INTEGER,
    checksum_sha256        VARCHAR(64),
    is_primary             BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_at            TIMESTAMPTZ NOT NULL,
    verification_status    VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    decision               VARCHAR(50) NOT NULL DEFAULT 'MANUAL_REVIEW',
    confidence_score       DOUBLE PRECISION,
    verification_reasons   TEXT,
    verifier_id            VARCHAR(100),
    verified_at            TIMESTAMPTZ,
    verification_attempts  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_produce_image_case_id ON produce_image (case_id);
CREATE INDEX idx_produce_image_listing_id ON produce_image (listing_id);
CREATE INDEX idx_produce_image_checksum ON produce_image (checksum_sha256);
CREATE INDEX idx_produce_image_decision ON produce_image (decision);
