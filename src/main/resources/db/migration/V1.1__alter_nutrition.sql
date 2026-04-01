

ALTER TABLE fatsecret_day
    ALTER COLUMN calories     TYPE DOUBLE PRECISION USING calories::double precision,
    ALTER COLUMN protein      TYPE DOUBLE PRECISION USING protein::double precision,
    ALTER COLUMN fat          TYPE DOUBLE PRECISION USING fat::double precision,
    ALTER COLUMN carbohydrate TYPE DOUBLE PRECISION USING carbohydrate::double precision;

ALTER TABLE fatsecret_food
    ALTER COLUMN protein      TYPE DOUBLE PRECISION USING protein::double precision,
    ALTER COLUMN fat          TYPE DOUBLE PRECISION USING fat::double precision,
    ALTER COLUMN carbohydrate TYPE DOUBLE PRECISION USING carbohydrate::double precision;