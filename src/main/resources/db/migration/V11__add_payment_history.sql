-- V11: Adiciona coluna payment_history para armazenar dados de
--      Histórico de Pagamentos, Evolução de Compromissos e Referenciais de Negócios
--      provenientes diretamente da API Serasa (seção paymentHistory do relatório).

ALTER TABLE credit_analysis
    ADD COLUMN IF NOT EXISTS payment_history JSONB;

COMMENT ON COLUMN credit_analysis.payment_history IS
    'Seção paymentHistory do relatório Serasa: titlesQuantity, market, monthDetail, '
    'averageDelayPeriods, evolutionCommitmentsSuppliers e businessReferences.';
