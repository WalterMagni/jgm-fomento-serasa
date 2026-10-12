-- Esteira unificada, fatia A3: pendências, comentários e a pausa do prazo.
-- Design em docs/plans/2026-10-11-esteira-unificada-design.md.
--
-- No Portal Comercial a pendência era um status (PENDENTE_INFO, PENDENTE_DOC), e a requisição
-- ficava presa quando o último documento pedido era apagado em vez de aceito. Aqui ela é um
-- registro próprio: o card continua no estágio, com o selo "aguardando comercial", e o relógio
-- do prazo para enquanto houver pendência aberta.

-- ------------------------------------------------------------------ pendências

CREATE TABLE prospeccao_pendencia (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id         UUID         NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    pergunta              TEXT         NOT NULL,
    -- Nomes dos campos de dados comerciais que a analista marcou como exigidos
    -- (ex.: ["faturamentoMensal", "ticketMedio"]). A tela mostra esses campos com asterisco.
    campos_exigidos       JSONB,
    -- Ids de itens do checklist (prospeccao_documento) pedidos nesta pendência.
    documentos_pedidos    JSONB,
    status                VARCHAR(12)  NOT NULL DEFAULT 'ABERTA',   -- ABERTA | RESPONDIDA | CANCELADA
    aberta_por            UUID         REFERENCES users (id) ON DELETE SET NULL,
    aberta_por_nome       VARCHAR(200),
    aberta_em             TIMESTAMP    NOT NULL DEFAULT now(),
    resposta              TEXT,
    respondida_por        UUID         REFERENCES users (id) ON DELETE SET NULL,
    respondida_por_nome   VARCHAR(200),
    -- Fase A: a equipe responde com o que o comercial trouxe. Fase B: o próprio comercial.
    em_nome_do_comercial  BOOLEAN      NOT NULL DEFAULT FALSE,
    fechada_em            TIMESTAMP,

    CONSTRAINT ck_prospeccao_pendencia_status CHECK (status IN ('ABERTA', 'RESPONDIDA', 'CANCELADA')),
    CONSTRAINT ck_prospeccao_pendencia_resposta CHECK (status <> 'RESPONDIDA' OR resposta IS NOT NULL)
);

CREATE INDEX idx_prospeccao_pendencia_card ON prospeccao_pendencia (prospeccao_id, aberta_em DESC);
CREATE INDEX idx_prospeccao_pendencia_aberta ON prospeccao_pendencia (prospeccao_id) WHERE status = 'ABERTA';

-- ------------------------------------------------------------------ pausa do prazo

-- O tempo de pendência aberta não conta no prazo do estágio: quem deve a resposta é o cliente.
-- Guardado no card, e não recalculado das pendências, para o quadro não precisar de consulta
-- extra por card. Os dois zeram a cada troca de estágio, junto com estagio_desde.
ALTER TABLE prospeccao
    ADD COLUMN pausado_desde  TIMESTAMP,
    ADD COLUMN horas_pausadas INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN prospeccao.pausado_desde IS 'Início da pausa corrente do prazo (pendência aberta). Nulo sem pausa';
COMMENT ON COLUMN prospeccao.horas_pausadas IS 'Horas úteis já pausadas no estágio atual, descontadas do prazo';

-- ------------------------------------------------------------------ comentários

-- Mesmo formato de liberacao_comentario (V65): editável pelo autor, exclusão lógica, menções no
-- texto como @[Nome](user:uuid) e @[Empresa](cnpj:...).
CREATE TABLE prospeccao_comentario (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id  UUID          NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    autor_id       UUID          REFERENCES users (id) ON DELETE SET NULL,
    autor_nome     VARCHAR(200)  NOT NULL,
    texto          TEXT          NOT NULL,
    criado_em      TIMESTAMP     NOT NULL DEFAULT now(),
    editado_em     TIMESTAMP,
    excluido_em    TIMESTAMP
);

CREATE INDEX idx_prospeccao_comentario_card ON prospeccao_comentario (prospeccao_id, criado_em DESC)
    WHERE excluido_em IS NULL;
