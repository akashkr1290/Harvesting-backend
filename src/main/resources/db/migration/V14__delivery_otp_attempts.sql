-- Counts wrong delivery-OTP guesses so the 6-digit code cannot be brute forced.
ALTER TABLE delivery_otp ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
