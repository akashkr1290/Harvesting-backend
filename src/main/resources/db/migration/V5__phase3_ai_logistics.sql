-- V2__phase3_ai_logistics.sql
--
-- Phase 3: AI demand forecasting + route optimization layer.
-- Additive only — no existing column is altered or dropped, so every
-- prior migration and seeded row stays valid.

-- ---------------------------------------------------------------------
-- harvest_case — tag each case with the crop it's for, so demand can be
-- forecast per crop/location instead of only per location.
-- ---------------------------------------------------------------------
ALTER TABLE harvest_case ADD COLUMN crop_type VARCHAR(255);
CREATE INDEX idx_harvest_case_crop_type ON harvest_case (crop_type);

-- ---------------------------------------------------------------------
-- master_item — optional coordinates so LOCATIONS/COMPANIES entries can
-- be resolved to a lat/lng by name during route optimization.
-- ---------------------------------------------------------------------
ALTER TABLE master_item ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE master_item ADD COLUMN longitude DOUBLE PRECISION;

-- ---------------------------------------------------------------------
-- demand_prediction — one row per generated forecast run for a
-- crop/location pair. History is kept (not overwritten) so trend and
-- "latest" queries both work off the same table.
-- ---------------------------------------------------------------------
CREATE TABLE demand_prediction (
    id                      UUID PRIMARY KEY,
    crop_type               VARCHAR(255) NOT NULL,
    location                VARCHAR(255) NOT NULL,
    prediction_date         DATE NOT NULL,
    predicted_quantity      DOUBLE PRECISION NOT NULL,
    demand_level            VARCHAR(50) NOT NULL,
    trend                   VARCHAR(50) NOT NULL,
    confidence_score        DOUBLE PRECISION,
    data_points_used        INTEGER NOT NULL,
    historical_average      DOUBLE PRECISION,
    generated_by_username   VARCHAR(255) NOT NULL,
    generated_at            TIMESTAMPTZ NOT NULL,
    notes                   VARCHAR(1000)
);

CREATE INDEX idx_demand_prediction_crop_location
    ON demand_prediction (crop_type, location, generated_at DESC);

-- ---------------------------------------------------------------------
-- delivery_vehicle — lightweight fleet registry. Distinct from the
-- existing MasterCategory.VEHICLE_TYPES lookup (which is just a type
-- name for dropdowns): this is an actual vehicle with a capacity, used
-- to validate route optimization requests against real load limits.
-- ---------------------------------------------------------------------
CREATE TABLE delivery_vehicle (
    id                      UUID PRIMARY KEY,
    registration_number     VARCHAR(255) NOT NULL,
    vehicle_type            VARCHAR(255) NOT NULL,
    capacity_kg             DOUBLE PRECISION NOT NULL,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_delivery_vehicle_registration UNIQUE (registration_number)
);

-- ---------------------------------------------------------------------
-- route_optimization_result — one row per optimization run. Optionally
-- linked to the harvest_case it was run for (the "marketplace order" of
-- this app) and to the vehicle it was checked against.
-- ---------------------------------------------------------------------
CREATE TABLE route_optimization_result (
    id                          UUID PRIMARY KEY,
    case_id                     UUID,
    vehicle_id                  UUID,
    status                      VARCHAR(50) NOT NULL DEFAULT 'OPTIMIZED',
    total_quantity_kg           DOUBLE PRECISION,
    original_distance_km        DOUBLE PRECISION NOT NULL,
    optimized_distance_km       DOUBLE PRECISION NOT NULL,
    distance_saved_km           DOUBLE PRECISION NOT NULL,
    estimated_duration_minutes  DOUBLE PRECISION NOT NULL,
    estimated_cost              DOUBLE PRECISION NOT NULL,
    requested_by_username       VARCHAR(255) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_route_result_case FOREIGN KEY (case_id) REFERENCES harvest_case (id),
    CONSTRAINT fk_route_result_vehicle FOREIGN KEY (vehicle_id) REFERENCES delivery_vehicle (id)
);

CREATE INDEX idx_route_result_case ON route_optimization_result (case_id);

-- ---------------------------------------------------------------------
-- route_stop — the optimized stop sequence for a route result.
-- ---------------------------------------------------------------------
CREATE TABLE route_stop (
    id                  UUID PRIMARY KEY,
    route_result_id     UUID NOT NULL,
    sequence_index       INTEGER NOT NULL,
    name                VARCHAR(255) NOT NULL,
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    stop_type           VARCHAR(50) NOT NULL,
    CONSTRAINT fk_route_stop_result FOREIGN KEY (route_result_id) REFERENCES route_optimization_result (id)
);

CREATE INDEX idx_route_stop_result ON route_stop (route_result_id, sequence_index);
