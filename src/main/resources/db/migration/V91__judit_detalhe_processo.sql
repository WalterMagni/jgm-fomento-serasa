-- Detalhar um processo pela Judit (pedido de 2026-10-15): consulta processual por CNJ, direto no
-- tribunal, com resumo de IA. Traz todas as movimentações, que a base histórica não traz.
--
-- Fica na mesma judit_consulta para o limite do mês continuar sendo uma soma só. Diferente das
-- outras, essa consulta é assíncrona: a Judit responde em ~1 minuto. A linha nasce PENDENTE (já
-- cobrada — a Judit cobra o pedido) e o backend acompanha até CONCLUIDA ou FALHOU.

ALTER TABLE judit_consulta DROP CONSTRAINT judit_consulta_servico_check;
ALTER TABLE judit_consulta
    ADD CONSTRAINT judit_consulta_servico_check CHECK (servico IN ('RESUMO', 'PROCESSOS', 'DETALHE'));

-- Número CNJ do processo detalhado. Nulo nas consultas por documento.
ALTER TABLE judit_consulta ADD COLUMN cnj VARCHAR(25);

ALTER TABLE judit_consulta
    ADD COLUMN situacao VARCHAR(12) NOT NULL DEFAULT 'CONCLUIDA'
        CHECK (situacao IN ('PENDENTE', 'CONCLUIDA', 'FALHOU'));
ALTER TABLE judit_consulta ADD COLUMN concluida_em TIMESTAMP;
ALTER TABLE judit_consulta ADD COLUMN erro TEXT;

-- Enquanto PENDENTE ainda não há resposta.
ALTER TABLE judit_consulta ALTER COLUMN resposta DROP NOT NULL;

CREATE INDEX idx_judit_consulta_cnj ON judit_consulta (cnj, criado_em DESC) WHERE cnj IS NOT NULL;
