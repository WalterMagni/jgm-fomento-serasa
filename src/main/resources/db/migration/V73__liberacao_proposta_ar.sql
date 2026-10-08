-- Proposta importada do PDF da Análise de Risco (AR). Foto do relatório no momento da importação:
-- limites, comprometimento, concentração e a carteira do cedente no card; a carteira de cada
-- sacado (vencidos, a vencer, liquidados, recomprados) no sacado. Card criado à mão fica nulo.
ALTER TABLE liberacao_card ADD COLUMN proposta JSONB;
ALTER TABLE liberacao_sacado ADD COLUMN carteira JSONB;
