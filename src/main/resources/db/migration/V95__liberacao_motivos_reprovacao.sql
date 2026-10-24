-- Motivos de reprovação da esteira de liberação (pedido do time em 2026-10-24): quem reprova marca
-- um ou mais da lista em vez de escrever, e o relatório passa a contar por motivo. O admin mantém
-- a lista.
CREATE TABLE liberacao_motivo_reprovacao (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        VARCHAR(120) NOT NULL,
    ordem       INTEGER      NOT NULL DEFAULT 0,
    -- Desativa em vez de apagar: o card reprovado guarda o nome como texto e continua legível,
    -- mas o motivo some da lista de escolha.
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_liberacao_motivo_reprovacao_nome ON liberacao_motivo_reprovacao (upper(nome));

-- A lista do time.
INSERT INTO liberacao_motivo_reprovacao (nome, ordem) VALUES
    ('Cedente - Sem Limite',             1),
    ('Cedente - Sem Tranche',            2),
    ('Cedente em Saída de Risco',        3),
    ('Op. fora do Perfil',               4),
    ('Op em outro Fundo',                5),
    ('Problema Sefaz',                   6),
    ('Modalidade não Aprovada',          7),
    ('Pendência Cadastro',               8),
    ('Pendência de Checagem',            9),
    ('Pendência Monitore',              10),
    ('Sacado sem Limite',               11),
    ('Perfil de Fraude',                12),
    ('Sacado Não Paga',                 13),
    ('Sacado Não Confirma',             14),
    ('Sacado sem Porte / Concentração', 15),
    ('Praça de Pgto',                   16);

-- Os motivos marcados no card, pelo nome do momento: renomear ou desativar depois não muda o que
-- foi decidido. Nulo fora de Reprovado; reabrir no Comitê limpa, como a ressalva.
ALTER TABLE liberacao_card ADD COLUMN motivos_reprovacao JSONB;
