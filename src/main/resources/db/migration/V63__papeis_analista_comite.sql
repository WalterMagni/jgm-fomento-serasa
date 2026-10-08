-- Marcas de usuário da esteira de liberação de operações.
--
-- São booleanos, e não valores novos em users.role, por dois motivos. A Diretoria precisa ser
-- analista (edita em qualquer etapa) sem entrar na conta dos pareceres do Comitê — são duas
-- marcas independentes, e um papel único teria de enumerar as combinações. E users.role já tem
-- três grafias no banco (ROLE_USER, USER, ADMIN); somar um quarto valor ali herdaria a bagunça.
--
-- Quem marca é o admin da lista APP_USER_MANAGEMENT_ALLOWED_EMAILS, na tela Settings.
ALTER TABLE users
    ADD COLUMN analista BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN comite   BOOLEAN NOT NULL DEFAULT FALSE;

-- Parecer do Comitê é decisão de analista. Membro do Comitê sem a marca de analista daria
-- parecer mas não conseguiria mover o card que liberou.
ALTER TABLE users
    ADD CONSTRAINT ck_users_comite_exige_analista CHECK (NOT comite OR analista);
