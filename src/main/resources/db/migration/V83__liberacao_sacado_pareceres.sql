-- Parecer por sacado (pedido do time em 2026-10-12): um texto para cada quadrado — O (Origem),
-- C (Comitê) e D (Diretoria) — escrito por quem pode marcar aquele quadrado. Aceita @menção.
ALTER TABLE liberacao_sacado ADD COLUMN parecer_origem TEXT;
ALTER TABLE liberacao_sacado ADD COLUMN parecer_comite TEXT;
ALTER TABLE liberacao_sacado ADD COLUMN parecer_diretoria TEXT;
