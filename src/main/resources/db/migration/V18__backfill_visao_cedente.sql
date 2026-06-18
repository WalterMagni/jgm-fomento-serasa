-- V18: Recalcula visao_cedente para registros existentes que ainda estão como PENDENTE
-- Detecta presença de dados no nó segmentData.assignor dentro do JSONB payment_history.
-- Mesma lógica do SerasaCreditRatingMapper.detectarVisaoCedente()

UPDATE credit_analysis
SET visao_cedente = CASE
    -- businessReferencesList com pelo menos 1 item
    WHEN jsonb_array_length(
             payment_history #> '{segmentData,assignor,businessReferences,businessReferencesList}'
         ) > 0
        THEN 'SIM'
    -- evolutionCommitmentsSuppliersList com pelo menos 1 item
    WHEN jsonb_array_length(
             payment_history #> '{segmentData,assignor,evolutionCommitmentsSuppliers,evolutionCommitmentsSuppliersList}'
         ) > 0
        THEN 'SIM'
    -- monthDetail.months com pelo menos 1 item
    WHEN jsonb_array_length(
             payment_history #> '{segmentData,assignor,paymentHistory,monthDetail,months}'
         ) > 0
        THEN 'SIM'
    -- assignor existe mas está vazio
    WHEN payment_history #> '{segmentData,assignor}' IS NOT NULL
        THEN 'NAO'
    -- segmentData não existe no payload
    ELSE 'PENDENTE'
END
WHERE visao_cedente = 'PENDENTE'
  AND payment_history IS NOT NULL
  AND payment_history != '{}'::jsonb;
