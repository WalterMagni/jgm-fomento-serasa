-- Comentários do card da esteira de liberação. Abertos a todo usuário em qualquer etapa: comentar
-- e ser marcado não depende de ser analista.
--
-- O texto guarda as menções como marcação — @[Nome](user:<uuid>) e @[Empresa](cnpj:<14 dígitos>)
-- — para a menção sobreviver à troca de nome da pessoa e o texto continuar legível sem a tela.
--
-- Fica fora de liberacao_evento de propósito: comentário é editável pelo autor, e a timeline de
-- eventos é append-only. A tela junta as duas na aba Atividade.
CREATE TABLE liberacao_comentario (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id      UUID          NOT NULL REFERENCES liberacao_card (id) ON DELETE CASCADE,
    autor_id     UUID          REFERENCES users (id) ON DELETE SET NULL,
    autor_nome   VARCHAR(200)  NOT NULL,
    texto        TEXT          NOT NULL,
    criado_em    TIMESTAMP     NOT NULL DEFAULT now(),
    editado_em   TIMESTAMP,
    -- Apagar esconde, não remove: mesma regra do card.
    excluido_em  TIMESTAMP
);

CREATE INDEX idx_liberacao_comentario_card ON liberacao_comentario (card_id, criado_em DESC) WHERE excluido_em IS NULL;
