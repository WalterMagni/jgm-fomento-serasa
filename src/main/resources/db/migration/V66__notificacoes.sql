-- Notificações do portal. Nasceram com a esteira de liberação, mas a tabela é genérica: o
-- destino é um link, não uma chave para card, para a prospecção e a praça poderem notificar
-- pelo mesmo sino depois.
CREATE TABLE notificacao (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destinatario_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tipo             VARCHAR(30)   NOT NULL,
    titulo           VARCHAR(300)  NOT NULL,
    resumo           TEXT,
    link             VARCHAR(500)  NOT NULL,
    -- Quem agiu, denormalizado como nas timelines: o nome sobrevive à remoção do usuário.
    ator_id          UUID          REFERENCES users (id) ON DELETE SET NULL,
    ator_nome        VARCHAR(200),
    criada_em        TIMESTAMP     NOT NULL DEFAULT now(),
    lida_em          TIMESTAMP
);

-- O sino pergunta sempre a mesma coisa: as últimas de uma pessoa e quantas ela não leu.
CREATE INDEX idx_notificacao_destinatario ON notificacao (destinatario_id, criada_em DESC);
CREATE INDEX idx_notificacao_nao_lida ON notificacao (destinatario_id) WHERE lida_em IS NULL;

-- Som do sino. Fica no perfil, e não no navegador, para valer em qualquer computador.
ALTER TABLE users ADD COLUMN som_notificacao BOOLEAN NOT NULL DEFAULT TRUE;
