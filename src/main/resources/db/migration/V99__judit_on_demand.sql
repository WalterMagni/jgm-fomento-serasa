-- Judit fase 2 (reunião de 2026-10-26): On Demand no lugar da lista do Data Lake, franquia que é piso
-- (não teto) e liberação do admin acima do limite do mês. Ver docs/judit/03-on-demand.md.

-- On Demand: Data Lake + tribunais ao vivo, R$ 6,00 a cada 1.000. Assíncrono, como o detalhe.
ALTER TABLE judit_consulta DROP CONSTRAINT judit_consulta_servico_check;
ALTER TABLE judit_consulta
    ADD CONSTRAINT judit_consulta_servico_check
        CHECK (servico IN ('RESUMO', 'PROCESSOS', 'DETALHE', 'ON_DEMAND'));

-- Só no ON_DEMAND: o polo pedido. Ré por padrão; autora num pedido à parte.
ALTER TABLE judit_consulta ADD COLUMN polo VARCHAR(7) CHECK (polo IN ('PASSIVO', 'ATIVO'));

-- A pré-consulta (RESUMO) usada na estimativa. Nula: a pessoa pulou a pré-consulta — é o que a
-- auditoria do painel procura quando o On Demand sai caro.
ALTER TABLE judit_consulta ADD COLUMN pre_consulta_id UUID REFERENCES judit_consulta (id);

-- O que a tela prometeu antes de cobrar. No On Demand o `custo` vira o real quando a resposta chega.
ALTER TABLE judit_consulta ADD COLUMN custo_estimado NUMERIC(10, 2);

-- Nome para o painel de consumo e para o aviso no sino; o sacado nem sempre tem cadastro.
ALTER TABLE judit_consulta ADD COLUMN nome_empresa VARCHAR(255);

-- Última vez que o acompanhamento perguntou a situação à Judit. O intervalo cresce com a idade do
-- pedido, e o acompanhamento lê o banco: reiniciar o servidor não perde pedido pendente.
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
