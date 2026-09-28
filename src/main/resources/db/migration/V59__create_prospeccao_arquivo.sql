-- Metadado do arquivo no banco; o byte vive no compartilhamento de rede que o backend já
-- monta (docker-compose: DOCUMENTS_BASE_HOST_PATH -> /mnt/clientes). É a pasta CLIENTES onde
-- as analistas já guardam documento hoje.
--
-- Guardar ali é melhor que um bucket, não apenas mais simples: o arquivo cai onde a equipe já
-- trabalha, aparece no Explorer e entra no backup que já existe. Um bucket criaria um segundo
-- lugar onde documento mora — exatamente o problema que a esteira quer resolver.
CREATE TABLE prospeccao_arquivo (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    prospeccao_id     UUID         NOT NULL REFERENCES prospeccao (id) ON DELETE CASCADE,
    documento_id      UUID         REFERENCES prospeccao_documento (id) ON DELETE SET NULL,

    -- Sempre relativo à base. A base é configurável em runtime
    -- (SystemSettingService.requireDocumentStorageBasePath) e guardar caminho absoluto
    -- quebraria a tabela inteira se ela mudasse.
    caminho_relativo  TEXT         NOT NULL,
    nome_original     VARCHAR(300) NOT NULL,
    mime_type         VARCHAR(120) NOT NULL,
    tamanho_bytes     BIGINT       NOT NULL,
    versao            INTEGER      NOT NULL DEFAULT 1,

    enviado_por       UUID         REFERENCES users (id) ON DELETE SET NULL,
    enviado_em        TIMESTAMP    NOT NULL DEFAULT now(),

    -- Remoção lógica. O compartilhamento é usado por outras equipes; apagar byte de lá é
    -- destrutivo e irreversível. O registro sai da tela, o arquivo fica no disco.
    removido_em       TIMESTAMP,
    removido_por      UUID         REFERENCES users (id) ON DELETE SET NULL
);

-- Dois arquivos não podem ocupar o mesmo caminho, mesmo que um deles já tenha sido removido
-- logicamente — o byte continua lá.
CREATE UNIQUE INDEX ux_prospeccao_arquivo_caminho ON prospeccao_arquivo (caminho_relativo);

CREATE INDEX idx_prospeccao_arquivo_card
    ON prospeccao_arquivo (prospeccao_id) WHERE removido_em IS NULL;
CREATE INDEX idx_prospeccao_arquivo_documento
    ON prospeccao_arquivo (documento_id) WHERE removido_em IS NULL;
