-- V3__phase4_payment_notification_admin_review.sql
--
-- Phase 4 scope-completion pass: mock payments, general notifications, and
-- admin manual-review columns on produce_image. Additive only — no existing
-- table (harvest_case, purchase_invoice, sales_invoice, produce_image's
-- existing columns, etc.) is altered in a breaking way.

ALTER TABLE produce_image
    ADD COLUMN admin_reviewed_by_username VARCHAR(255),
    ADD COLUMN admin_review_note VARCHAR(500),
    ADD COLUMN admin_reviewed_at TIMESTAMPTZ;

CREATE TABLE payment_record (
    id                     UUID PRIMARY KEY,
    reference_type         VARCHAR(50) NOT NULL,
    reference_id           UUID NOT NULL,
    amount                 DOUBLE PRECISION NOT NULL,
    currency               VARCHAR(10) NOT NULL DEFAULT 'INR',
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider               VARCHAR(100) NOT NULL,
    gateway_reference      VARCHAR(255),
    failure_reason         VARCHAR(500),
    initiated_by_username  VARCHAR(255) NOT NULL,
    note                   VARCHAR(500),
    created_at             TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ
);

CREATE INDEX idx_payment_record_reference ON payment_record (reference_type, reference_id);
CREATE INDEX idx_payment_record_status ON payment_record (status);

CREATE TABLE notification (
    id                     UUID PRIMARY KEY,
    recipient_username     VARCHAR(255) NOT NULL,
    category               VARCHAR(50) NOT NULL,
    title                  VARCHAR(255) NOT NULL,
    message                VARCHAR(1000) NOT NULL,
    related_entity_type    VARCHAR(100),
    related_entity_id      UUID,
    created_at             TIMESTAMPTZ NOT NULL,
    read_at                TIMESTAMPTZ
);

CREATE INDEX idx_notification_recipient ON notification (recipient_username, created_at DESC);
