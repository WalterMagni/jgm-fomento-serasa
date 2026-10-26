-- Judit (reunião de 2026-10-26): a franquia é o piso da conta, não o teto, e o admin libera valor a
-- mais acima do limite do mês. Aba Consumo Judit nas Configurações. Ver docs/judit/03-on-demand.md.

-- Nome para o painel de consumo e para o aviso no sino; o sacado nem sempre tem cadastro.
ALTER TABLE judit_consulta ADD COLUMN nome_empresa VARCHAR(255);

-- Última vez que o acompanhamento perguntou a situação do detalhe à Judit. O intervalo cresce com a
-- idade do pedido, e o acompanhamento lê o banco: reiniciar o servidor não perde pedido pendente.
ALTER TABLE judit_consulta ADD COLUMN verificado_em TIMESTAMP;

CREATE INDEX idx_judit_consulta_pendente ON judit_consulta (criado_em) WHERE situacao = 'PENDENTE';

UPDATE judit_consulta c
   SET nome_empresa = d.company_name
  FROM company_details d
 WHERE d.document_number = c.documento
   AND c.nome_empresa IS NULL;

-- Valor a mais no limite do mês, liberado pelo admin. Vale só para o mês indicado (sempre o dia 1).
CREATE TABLE judit_liberacao (
    id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    mes         DATE           NOT NULL,
    valor       NUMERIC(10, 2) NOT NULL CHECK (valor > 0),
    motivo      TEXT,
    admin_id    UUID,
    admin_nome  VARCHAR(255),
    criado_em   TIMESTAMP      NOT NULL
);

CREATE INDEX idx_judit_liberacao_mes ON judit_liberacao (mes);
