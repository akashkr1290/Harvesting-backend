CREATE TABLE registration_session (
    id UUID PRIMARY KEY,
    registration_token VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(15),
    mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,
    aadhaar_number_encrypted TEXT,
    aadhaar_last4 VARCHAR(4),
    aadhaar_verified BOOLEAN NOT NULL DEFAULT FALSE,
    selected_role VARCHAR(32),
    address_line VARCHAR(255), village_or_city VARCHAR(100), district VARCHAR(100), state VARCHAR(100), pincode VARCHAR(6),
    status VARCHAR(32) NOT NULL DEFAULT 'REGISTRATION_STARTED',
    created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ
);
CREATE INDEX idx_registration_session_mobile ON registration_session(mobile_number);
CREATE TABLE otp_challenge (
    id UUID PRIMARY KEY, registration_session_id UUID NOT NULL REFERENCES registration_session(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL, otp_hash VARCHAR(100) NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ, attempt_count INT NOT NULL DEFAULT 0, resend_count INT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_otp_challenge_session_purpose ON otp_challenge(registration_session_id,purpose,created_at DESC);
CREATE TABLE registration_document (
    id UUID PRIMARY KEY, registration_session_id UUID NOT NULL REFERENCES registration_session(id) ON DELETE CASCADE,
    document_type VARCHAR(32) NOT NULL, storage_path TEXT NOT NULL, original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL, file_size_bytes BIGINT NOT NULL, uploaded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_registration_document_type UNIQUE(registration_session_id,document_type)
);
