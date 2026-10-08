-- Tipo de operação deixa de ser lista fechada: o time cria o tipo que precisar ("Cessão de
-- crédito", "Nota promissória"…). A coluna passa a guardar o nome como o time lê, e os tipos
-- personalizados aparecem para todos a partir dos cards que já os usam — sem tabela própria,
-- um tipo digitado errado some da lista quando nenhum card o usa mais.
ALTER TABLE liberacao_card ALTER COLUMN tipo_operacao TYPE VARCHAR(40);

UPDATE liberacao_card
SET tipo_operacao = CASE tipo_operacao
    WHEN 'DUPLICATA' THEN 'Duplicata'
    WHEN 'CHEQUE' THEN 'Cheque'
    WHEN 'COMISSARIA' THEN 'Comissária'
    WHEN 'INTERCOMPANY' THEN 'Intercompany'
    WHEN 'OUTROS' THEN 'Outros'
    ELSE tipo_operacao
END
WHERE tipo_operacao IS NOT NULL;
