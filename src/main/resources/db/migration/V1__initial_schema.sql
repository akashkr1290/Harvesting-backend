-- V1__initial_schema.sql
--
-- Baseline schema for HarvestFlow, hand-written from the JPA entities
-- (rather than generated) so it can be reviewed and version-controlled
-- like any other production migration. Cross-checked field-by-field
-- against every entity in domain/entity/ as of this migration's authoring.
--
-- ddl-auto is set to 'validate' in application.yml, meaning Hibernate
-- will refuse to start if this schema doesn't exactly match the entity
-- mappings — that's intentional: a loud startup failure here is far
-- safer than a silent schema drift discovered later in production.

-- ---------------------------------------------------------------------
-- app_user
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    mobile                VARCHAR(255),
    email                 VARCHAR(255) NOT NULL,
    role                  VARCHAR(255) NOT NULL,
    location              VARCHAR(255),
    username              VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    must_change_password  BOOLEAN NOT NULL DEFAULT TRUE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_at        TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- master_item
-- ---------------------------------------------------------------------
CREATE TABLE master_item (
    id          UUID PRIMARY KEY,
    category    VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_master_item_category_name UNIQUE (category, name)
);

-- ---------------------------------------------------------------------
-- harvest_case — the spine record
-- ---------------------------------------------------------------------
CREATE TABLE harvest_case (
    id                     UUID PRIMARY KEY,
    status                 VARCHAR(255) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    created_by_username    VARCHAR(255) NOT NULL,

    location               VARCHAR(255) NOT NULL,
    visit_date             DATE NOT NULL,
    commission_agent_name  VARCHAR(255),
    farmer_name            VARCHAR(255) NOT NULL,
    farmer_phone           VARCHAR(255),
    village                VARCHAR(255) NOT NULL,
    number_of_plants       INTEGER,
    harvesting_date        DATE NOT NULL,
    remark                 VARCHAR(1000),
    volume                 DOUBLE PRECISION,
    recovery               DOUBLE PRECISION,
    pulp                   DOUBLE PRECISION,
    selected_company       VARCHAR(255) NOT NULL,
    plot_code              VARCHAR(255),
    gps_note               VARCHAR(255),
    priority               VARCHAR(255) DEFAULT 'medium',
    photo_path             VARCHAR(255),

    rate_per_unit          DOUBLE PRECISION
);

CREATE INDEX idx_harvest_case_status ON harvest_case (status);
CREATE INDEX idx_harvest_case_farmer_name ON harvest_case (farmer_name);
CREATE INDEX idx_harvest_case_village ON harvest_case (village);

-- ---------------------------------------------------------------------
-- planning_details — one-to-one with harvest_case
-- ---------------------------------------------------------------------
CREATE TABLE planning_details (
    id                     UUID PRIMARY KEY,
    case_id                UUID NOT NULL,
    planned_date           DATE NOT NULL,
    time_window            VARCHAR(255),
    supervisor_name        VARCHAR(255) NOT NULL,
    pickup_person_name     VARCHAR(255) NOT NULL,
    vehicle_plan           VARCHAR(255),
    expected_quantity      DOUBLE PRECISION,
    notes_for_godown       VARCHAR(1000),
    notes_for_supervisor   VARCHAR(1000),
    CONSTRAINT uq_planning_details_case UNIQUE (case_id),
    CONSTRAINT fk_planning_details_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

-- ---------------------------------------------------------------------
-- packing_material_record — issue and return share this shape
-- ---------------------------------------------------------------------
CREATE TABLE packing_material_record (
    id           UUID PRIMARY KEY,
    case_id      UUID NOT NULL,
    stage        VARCHAR(255) NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL,
    handled_by   VARCHAR(255) NOT NULL,
    remarks      VARCHAR(1000),
    CONSTRAINT uq_packing_record_case_stage UNIQUE (case_id, stage),
    CONSTRAINT fk_packing_record_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

CREATE TABLE packing_material_line (
    id          UUID PRIMARY KEY,
    record_id   UUID NOT NULL,
    item_name   VARCHAR(255) NOT NULL,
    quantity    DOUBLE PRECISION NOT NULL,
    unit        VARCHAR(255) NOT NULL DEFAULT 'pcs',
    CONSTRAINT fk_packing_line_record FOREIGN KEY (record_id) REFERENCES packing_material_record (id)
);

CREATE INDEX idx_packing_line_record ON packing_material_line (record_id);

-- ---------------------------------------------------------------------
-- harvest_completion — one-to-one with harvest_case
-- ---------------------------------------------------------------------
CREATE TABLE harvest_completion (
    id                       UUID PRIMARY KEY,
    case_id                  UUID NOT NULL,
    actual_date              DATE NOT NULL,
    actual_bags_or_crates    INTEGER,
    actual_weight            DOUBLE PRECISION NOT NULL,
    quality_notes            VARCHAR(1000),
    wastage_notes            VARCHAR(1000),
    final_volume             DOUBLE PRECISION,
    final_recovery           DOUBLE PRECISION,
    final_pulp               DOUBLE PRECISION,
    remarks                  VARCHAR(1000),
    weight_slip_file_name    VARCHAR(255),
    CONSTRAINT uq_harvest_completion_case UNIQUE (case_id),
    CONSTRAINT fk_harvest_completion_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

-- ---------------------------------------------------------------------
-- logistics_entry — pickup / transport / labor / local labor
-- ---------------------------------------------------------------------
CREATE TABLE logistics_entry (
    id                          UUID PRIMARY KEY,
    case_id                     UUID NOT NULL,
    kind                        VARCHAR(255) NOT NULL,
    person_or_contractor_name  VARCHAR(255) NOT NULL,
    amount                      DOUBLE PRECISION NOT NULL,
    advance_payment             DOUBLE PRECISION NOT NULL DEFAULT 0,
    remarks                     VARCHAR(1000),
    entered_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_logistics_entry_case_kind UNIQUE (case_id, kind),
    CONSTRAINT fk_logistics_entry_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

-- ---------------------------------------------------------------------
-- purchase_invoice — one-to-one with harvest_case
-- ---------------------------------------------------------------------
CREATE TABLE purchase_invoice (
    id                       UUID PRIMARY KEY,
    case_id                  UUID NOT NULL,
    invoice_number           VARCHAR(255) NOT NULL,
    rate                     DOUBLE PRECISION NOT NULL,
    weight                   DOUBLE PRECISION NOT NULL,
    include_commission       BOOLEAN NOT NULL DEFAULT TRUE,
    commission_amount        DOUBLE PRECISION DEFAULT 0,
    include_packing_cost     BOOLEAN NOT NULL DEFAULT TRUE,
    packing_cost             DOUBLE PRECISION DEFAULT 0,
    include_transport_cost   BOOLEAN NOT NULL DEFAULT TRUE,
    transport_cost           DOUBLE PRECISION DEFAULT 0,
    other_charges            DOUBLE PRECISION DEFAULT 0,
    deductions               DOUBLE PRECISION DEFAULT 0,
    additions                DOUBLE PRECISION DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_purchase_invoice_case UNIQUE (case_id),
    CONSTRAINT uq_purchase_invoice_number UNIQUE (invoice_number),
    CONSTRAINT fk_purchase_invoice_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

-- ---------------------------------------------------------------------
-- sales_invoice — one-to-one with harvest_case
-- ---------------------------------------------------------------------
CREATE TABLE sales_invoice (
    id               UUID PRIMARY KEY,
    case_id          UUID NOT NULL,
    invoice_number   VARCHAR(255) NOT NULL,
    buyer_company    VARCHAR(255) NOT NULL,
    sales_rate       DOUBLE PRECISION NOT NULL,
    weight           DOUBLE PRECISION NOT NULL,
    tax_percent      DOUBLE PRECISION DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sales_invoice_case UNIQUE (case_id),
    CONSTRAINT uq_sales_invoice_number UNIQUE (invoice_number),
    CONSTRAINT fk_sales_invoice_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

-- ---------------------------------------------------------------------
-- timeline_entry — audit trail
-- ---------------------------------------------------------------------
CREATE TABLE timeline_entry (
    id          UUID PRIMARY KEY,
    case_id     UUID NOT NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(500) NOT NULL,
    CONSTRAINT fk_timeline_entry_case FOREIGN KEY (case_id) REFERENCES harvest_case (id)
);

CREATE INDEX idx_timeline_entry_case ON timeline_entry (case_id);

-- ---------------------------------------------------------------------
-- login_history — real login audit log (SOW 5.2 A: "View login history")
-- ---------------------------------------------------------------------
CREATE TABLE login_history (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    username        VARCHAR(255) NOT NULL,
    logged_in_at    TIMESTAMPTZ NOT NULL,
    ip_address      VARCHAR(255),
    success         BOOLEAN NOT NULL,
    failure_reason  VARCHAR(255),
    CONSTRAINT fk_login_history_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_login_history_user ON login_history (user_id);
CREATE INDEX idx_login_history_logged_in_at ON login_history (logged_in_at);

-- ---------------------------------------------------------------------
-- password_reset_token — self-service forgot-password flow
-- ---------------------------------------------------------------------
CREATE TABLE password_reset_token (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    token        VARCHAR(255) NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    used         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_password_reset_token UNIQUE (token),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_password_reset_token_user ON password_reset_token (user_id);
