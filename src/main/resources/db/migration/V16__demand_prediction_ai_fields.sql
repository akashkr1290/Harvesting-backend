-- Forecast details from the Python AI service (engine, range, price forecast, data source).
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS forecast_engine VARCHAR(60);
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS predicted_low DOUBLE PRECISION;
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS predicted_high DOUBLE PRECISION;
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS predicted_price_per_kg DOUBLE PRECISION;
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS price_trend VARCHAR(20);
ALTER TABLE demand_prediction ADD COLUMN IF NOT EXISTS data_source VARCHAR(120);
