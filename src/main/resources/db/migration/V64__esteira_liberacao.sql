-- Esteira de liberação de operações: o quadro ORIGEM → COMITE → PENDENCIA → APROVADO | REPROVADO
-- que substitui a planilha do departamento. Design em
-- docs/plans/2026-10-07-esteira-liberacao-design.md.
--
-- Diferente da prospecção, nada aqui é apagado de verdade: é liberação de dinheiro, e a
-- auditoria pesa mais do que limpar a fila. O card apagado ganha excluido_em e some do quadro.
CREATE TABLE liberacao_card (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Número curto para o time se referir ao card em conversa ("o 42"), como no Trello.
    numero               BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,

    etapa                VARCHAR(12)   NOT NULL,   -- ORIGEM | COMITE | PENDENCIA | APROVADO | REPROVADO
    etapa_desde          TIMESTAMP     NOT NULL DEFAULT now(),
    -- Rodada de pareceres vigente. Devolver para a Origem ou reabrir um finalizado abre rodada
    -- nova em vez de apagar os pareceres anteriores, que ficam como histórico.
    rodada               INTEGER       NOT NULL DEFAULT 1,

    -- Uma operação por card, e o mesmo cedente pode ter várias em paralelo: por isso não há
    -- índice único por CNPJ, ao contrário da prospecção.
    cedente_cnpj         VARCHAR(14)   NOT NULL,
    -- Copiado na criação: o card continua legível se a empresa nunca for cadastrada.
    cedente_nome         TEXT          NOT NULL,

    tipo_operacao        VARCHAR(16),  -- DUPLICATA | CHEQUE | COMISSARIA | INTERCOMPANY | OUTROS
    valor                NUMERIC(15,2),
    prazo                TIMESTAMP,
    parecer_origem       TEXT,

    -- Autor denormalizado como na prospecção: o nome sobrevive à remoção do usuário.
    criado_por_id        UUID          REFERENCES users (id) ON DELETE SET NULL,
    criado_por_nome      VARCHAR(200)  NOT NULL,
    criado_em            TIMESTAMP     NOT NULL DEFAULT now(),
    atualizado_por_id    UUID          REFERENCES users (id) ON DELETE SET NULL,
    atualizado_por_nome  VARCHAR(200)  NOT NULL,
    atualizado_em        TIMESTAMP     NOT NULL DEFAULT now(),

    finalizado_em        TIMESTAMP,

    excluido_em          TIMESTAMP,
    excluido_por_id      UUID          REFERENCES users (id) ON DELETE SET NULL,
    excluido_por_nome    VARCHAR(200),

    -- Trava otimista: duas analistas editando o mesmo card não sobrescrevem uma à outra.
    version              BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_liberacao_card_etapa ON liberacao_card (etapa, etapa_desde) WHERE excluido_em IS NULL;
CREATE INDEX idx_liberacao_card_cedente ON liberacao_card (cedente_cnpj);

CREATE TABLE liberacao_sacado (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id  UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    cnpj     VARCHAR(14)   NOT NULL,
    nome     TEXT,
    valor    NUMERIC(15,2),
    ordem    INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT ux_liberacao_sacado_card_cnpj UNIQUE (card_id, cnpj)
);

CREATE INDEX idx_liberacao_sacado_cnpj ON liberacao_sacado (cnpj);

-- Quem acompanha o card. Entram sozinhos o criador, o Comitê e quem recebe pendência.
CREATE TABLE liberacao_membro (
    card_id        UUID        NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    usuario_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    origem         VARCHAR(12) NOT NULL,   -- CRIADOR | COMITE | PENDENCIA | MANUAL
    adicionado_em  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (card_id, usuario_id)
);

CREATE INDEX idx_liberacao_membro_usuario ON liberacao_membro (usuario_id);

-- Uma linha por membro do Comitê por rodada. Nasce com posicao NULL quando o card entra no
-- Comitê; registrar o parecer preenche posicao, texto e registrado_em.
CREATE TABLE liberacao_parecer (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id        UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    rodada         INTEGER       NOT NULL,
    usuario_id     UUID          REFERENCES users (id) ON DELETE SET NULL,
    usuario_nome   VARCHAR(200)  NOT NULL,
    posicao        VARCHAR(16),  -- FAVORAVEL | COM_RESSALVAS | DESFAVORAVEL; NULL = aguardando
    texto          TEXT,
    registrado_em  TIMESTAMP,
    criado_em      TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ux_liberacao_parecer_rodada UNIQUE (card_id, rodada, usuario_id)
);

CREATE INDEX idx_liberacao_parecer_aguardando
    ON liberacao_parecer (usuario_id) WHERE posicao IS NULL;

CREATE TABLE liberacao_pendencia (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id              UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    aberta_por_id        UUID          REFERENCES users (id) ON DELETE SET NULL,
    aberta_por_nome      VARCHAR(200)  NOT NULL,
    destinatario_id      UUID          REFERENCES users (id) ON DELETE SET NULL,
    destinatario_nome    VARCHAR(200)  NOT NULL,
    texto                TEXT          NOT NULL,
    resposta             TEXT,
    aberta_em            TIMESTAMP     NOT NULL DEFAULT now(),
    respondida_em        TIMESTAMP,
    respondida_por_nome  VARCHAR(200)
);

CREATE INDEX idx_liberacao_pendencia_card ON liberacao_pendencia (card_id);
CREATE INDEX idx_liberacao_pendencia_aberta
    ON liberacao_pendencia (destinatario_id) WHERE respondida_em IS NULL;

-- Timeline append-only. Edição de campo grava o valor antes e depois, para o "quem mudou o
-- valor de 50 para 80 mil" ter resposta sem depender de memória.
CREATE TABLE liberacao_evento (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id       UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    tipo          VARCHAR(30)   NOT NULL,
    etapa_de      VARCHAR(12),
    etapa_para    VARCHAR(12),
    campo         VARCHAR(40),
    valor_antes   TEXT,
    valor_depois  TEXT,
    texto         TEXT,
    usuario_id    UUID          REFERENCES users (id) ON DELETE SET NULL,
    usuario_nome  VARCHAR(200)  NOT NULL,
    criado_em     TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_liberacao_evento_card ON liberacao_evento (card_id, criado_em DESC);
