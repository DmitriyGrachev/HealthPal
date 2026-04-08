-- Сначала нормализуем уже существующие данные
UPDATE ai_insights
SET insight_type = UPPER(insight_type)
WHERE insight_type IS NOT NULL;

-- Удаляем старый CHECK
ALTER TABLE ai_insights
DROP CONSTRAINT IF EXISTS check_insight_type;

-- Создаём новый CHECK под значения из Java enum
ALTER TABLE ai_insights
    ADD CONSTRAINT check_insight_type
        CHECK (insight_type IN ('DAILY', 'WEEKLY', 'MONTHLY'));