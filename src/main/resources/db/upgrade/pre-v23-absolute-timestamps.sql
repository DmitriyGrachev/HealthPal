-- V23 is the authority for conversion: naive legacy note timestamps mean UTC.
-- This bridge only locks the table and performs an aggregate preflight.
LOCK TABLE user_notes IN SHARE ROW EXCLUSIVE MODE;

SELECT COUNT(*)
  FROM user_notes;
