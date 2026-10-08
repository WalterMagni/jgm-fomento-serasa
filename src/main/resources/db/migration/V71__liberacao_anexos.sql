-- Anexos do card da esteira de liberação (pedido do time em 2026-10-08).
--
-- O byte vai para o compartilhamento de rede, na pasta do CNPJ do cedente
-- ({cnpj}/liberacao/{numero do card}/...), como os arquivos da prospecção: fica no backup que já
-- existe e o time encontra o arquivo fora do portal também. Aqui fica só o metadado.
--
-- Remoção lógica: a pasta é compartilhada com outras equipes, e apagar byte de lá é irreversível.
CREATE TABLE liberacao_anexo (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id            UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    caminho_relativo   TEXT          NOT NULL,
    nome_original      VARCHAR(255)  NOT NULL,
    mime_type          VARCHAR(120),
    tamanho_bytes      BIGINT        NOT NULL,
    enviado_por_id     UUID          REFERENCES users (id) ON DELETE SET NULL,
    enviado_por_nome   VARCHAR(200)  NOT NULL,
    enviado_em         TIMESTAMP     NOT NULL DEFAULT now(),
    removido_em        TIMESTAMP,
    removido_por_nome  VARCHAR(200)
);

CREATE INDEX idx_liberacao_anexo_card ON liberacao_anexo (card_id) WHERE removido_em IS NULL;
