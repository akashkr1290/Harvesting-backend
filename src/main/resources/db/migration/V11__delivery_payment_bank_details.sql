-- Additive schema for marketplace delivery/payment confirmation and farmer bank details.
ALTER TABLE app_user ADD COLUMN bank_account_number_encrypted TEXT;
ALTER TABLE app_user ADD COLUMN bank_account_holder_name VARCHAR(255);
ALTER TABLE app_user ADD COLUMN bank_ifsc_code VARCHAR(11);
ALTER TABLE app_user ADD COLUMN bank_name VARCHAR(255);

ALTER TABLE registration_session ADD COLUMN bank_account_number_encrypted TEXT;
ALTER TABLE registration_session ADD COLUMN bank_account_holder_name VARCHAR(255);
ALTER TABLE registration_session ADD COLUMN bank_ifsc_code VARCHAR(11);
ALTER TABLE registration_session ADD COLUMN bank_name VARCHAR(255);

ALTER TABLE payment_record ADD COLUMN payment_method VARCHAR(20);
ALTER TABLE payment_record ADD COLUMN payment_url VARCHAR(1000);

ALTER TABLE market_order ADD COLUMN payment_method VARCHAR(20);
ALTER TABLE market_order ADD COLUMN payment_status VARCHAR(30);

CREATE TABLE delivery_assignment (
 id UUID PRIMARY KEY,
 market_order_id UUID NOT NULL UNIQUE REFERENCES market_order(id),
 delivery_boy_username VARCHAR(255) NOT NULL,
 status VARCHAR(30) NOT NULL,
 payment_method VARCHAR(20),
 pickup_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
 buyer_reached BOOLEAN NOT NULL DEFAULT FALSE,
 assigned_at TIMESTAMPTZ NOT NULL,
 picked_up_at TIMESTAMPTZ,
 buyer_reached_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 pickup_note VARCHAR(500)
);
CREATE INDEX idx_delivery_assignment_boy ON delivery_assignment(delivery_boy_username, assigned_at DESC);

CREATE TABLE delivery_otp (
 id UUID PRIMARY KEY,
 market_order_id UUID NOT NULL UNIQUE REFERENCES market_order(id) ON DELETE CASCADE,
 otp_hash VARCHAR(100) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_delivery_otp_order ON delivery_otp(market_order_id);
