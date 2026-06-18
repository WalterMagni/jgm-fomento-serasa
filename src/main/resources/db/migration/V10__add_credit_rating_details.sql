-- Adiciona coluna JSONB para armazenar a seção creditRating completa do RELATORIO_CREDIT_RATING
ALTER TABLE credit_analysis
    ADD COLUMN IF NOT EXISTS credit_rating_details jsonb;
