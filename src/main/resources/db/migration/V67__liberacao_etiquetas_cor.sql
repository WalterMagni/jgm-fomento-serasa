-- Recursos do Trello na esteira de liberação: cor do card e etiquetas.

-- Faixa colorida no topo do card. Nulo = sem cor. Valores da paleta em CorLiberacao.
ALTER TABLE liberacao_card ADD COLUMN cor VARCHAR(12);

-- Etiquetas da esteira toda, como no Trello: criadas na hora por qualquer um e reaproveitadas
-- entre cards ("Urgente", "Cliente novo").
CREATE TABLE liberacao_etiqueta (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(40)   NOT NULL,
    cor              VARCHAR(12)   NOT NULL,
    criado_por_nome  VARCHAR(200),
    criado_em        TIMESTAMP     NOT NULL DEFAULT now()
);

-- "Urgente" e "urgente" seriam duas etiquetas iguais no quadro.
CREATE UNIQUE INDEX ux_liberacao_etiqueta_nome ON liberacao_etiqueta (lower(nome));

CREATE TABLE liberacao_card_etiqueta (
    card_id      UUID NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    etiqueta_id  UUID NOT NULL REFERENCES liberacao_etiqueta (id) ON DELETE CASCADE,
    PRIMARY KEY (card_id, etiqueta_id)
);

CREATE INDEX idx_liberacao_card_etiqueta_etiqueta ON liberacao_card_etiqueta (etiqueta_id);
