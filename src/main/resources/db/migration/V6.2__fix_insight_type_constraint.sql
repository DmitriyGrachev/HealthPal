ALTER TABLE ai_insights DROP CONSTRAINT IF EXISTS check_insight_type;
ALTER TABLE ai_insights ADD CONSTRAINT check_insight_type CHECK (insight_type IN ('DAILY', 'WEEKLY', 'MONTHLY'));
