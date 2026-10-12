-- Esteira de prospecção: cor do card e etiquetas, como na esteira de liberação (V67).
--
-- Tabelas próprias, e não as da liberação: as duas esteiras organizam coisas diferentes
-- ("Cliente novo" na prospecção, "Urgente" na liberação) e não devem depender uma da outra. A
-- paleta é a mesma — o banco guarda só o nome da cor, o tom mora no frontend.

-- Faixa colorida no topo do card. Nulo = sem cor.
ALTER TABLE prospeccao ADD COLUMN cor VARCHAR(12);

CREATE TABLE prospeccao_etiqueta (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome             VARCHAR(40)   NOT NULL,
    cor              VARCHAR(12)   NOT NULL,
    criado_por_nome  VARCHAR(200),
    criado_em        TIMESTAMP     NOT NULL DEFAULT now()
);

-- "Cliente novo" e "cliente novo" seriam duas etiquetas iguais no quadro.
CREATE UNIQUE INDEX ux_prospeccao_etiqueta_nome ON prospeccao_etiqueta (lower(nome));

CREATE TABLE prospeccao_card_etiqueta (
    prospeccao_id  UUID NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    etiqueta_id    UUID NOT NULL REFERENCES prospeccao_etiqueta (id) ON DELETE CASCADE,
    PRIMARY KEY (prospeccao_id, etiqueta_id)
);

CREATE INDEX idx_prospeccao_card_etiqueta_etiqueta ON prospeccao_card_etiqueta (etiqueta_id);
