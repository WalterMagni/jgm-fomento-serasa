-- Esteira unificada, fatia A4: comitê com pareceres e contrato.
-- Design em docs/plans/2026-10-11-esteira-unificada-design.md.
--
-- No Portal Comercial o comitê era só um status que a analista avançava na mão. Aqui ele segue a
-- esteira de liberação (V64): ao entrar no comitê, cada membro (users.comite) é convocado com um
-- parecer vazio na rodada; registrar preenche a posição. Tabela própria, e não a da liberação,
-- para as duas esteiras não dependerem uma da outra.

ALTER TABLE prospeccao
    -- Rodada vigente do comitê. Zero até a primeira entrada; cada entrada no comitê abre uma nova.
    ADD COLUMN rodada_comite            INTEGER      NOT NULL DEFAULT 0,
    -- Quem assina o contrato pelo cliente. Exigido para concluir, como no Portal Comercial.
    ADD COLUMN contrato_contato_nome     VARCHAR(200),
    ADD COLUMN contrato_contato_telefone VARCHAR(40),
    ADD COLUMN contrato_contato_email    VARCHAR(200);

CREATE TABLE prospeccao_parecer (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id  UUID         NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    rodada         INTEGER      NOT NULL,
    usuario_id     UUID         REFERENCES users (id) ON DELETE SET NULL,
    usuario_nome   VARCHAR(200) NOT NULL,
    posicao        VARCHAR(16),  -- FAVORAVEL | COM_RESSALVAS | DESFAVORAVEL; NULL = aguardando
    texto          TEXT,
    criado_em      TIMESTAMP    NOT NULL DEFAULT now(),
    registrado_em  TIMESTAMP,

    CONSTRAINT ux_prospeccao_parecer_rodada UNIQUE (prospeccao_id, rodada, usuario_id),
    CONSTRAINT ck_prospeccao_parecer_posicao CHECK (posicao IS NULL OR posicao IN ('FAVORAVEL', 'COM_RESSALVAS', 'DESFAVORAVEL'))
);

CREATE INDEX idx_prospeccao_parecer_card ON prospeccao_parecer (prospeccao_id, rodada DESC);
-- "Meus pareceres pendentes", para o aviso do topo.
CREATE INDEX idx_prospeccao_parecer_aguardando ON prospeccao_parecer (usuario_id) WHERE posicao IS NULL;
