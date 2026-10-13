-- Redefinição de senha por e-mail (pedido do time em 2026-10-12). O link leva um código aleatório
-- de uso único; aqui fica só o SHA-256 dele, então quem lê a tabela não consegue usar o link.
CREATE TABLE redefinicao_senha (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_em TIMESTAMPTZ NOT NULL,
    usado_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_redefinicao_senha_user ON redefinicao_senha (user_id, criado_em);

-- Login emitido antes da troca de senha deixa de valer (ver SecurityFilter). Nulo: nunca trocou.
ALTER TABLE users ADD COLUMN senha_alterada_em TIMESTAMPTZ;
