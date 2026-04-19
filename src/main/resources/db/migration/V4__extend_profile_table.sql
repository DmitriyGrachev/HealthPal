-- Extend profile table with user context fields
ALTER TABLE profile 
    ADD COLUMN IF NOT EXISTS age INTEGER,
    ADD COLUMN IF NOT EXISTS gender VARCHAR(20),
    ADD COLUMN IF NOT EXISTS primary_goal VARCHAR(30),
    ADD COLUMN IF NOT EXISTS target_weight_kg DECIMAL(5,2),
    ADD COLUMN IF NOT EXISTS target_date DATE;

-- Add constraint for gender values
ALTER TABLE profile 
    ADD CONSTRAINT chk_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'));

-- Add constraint for primary_goal values
ALTER TABLE profile 
    ADD CONSTRAINT chk_primary_goal CHECK (primary_goal IN ('WEIGHT_LOSS', 'MUSCLE_GAIN', 'MAINTENANCE', 'PERFORMANCE'));
