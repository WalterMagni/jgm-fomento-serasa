-- Aprovação com ressalva (pedido do time em 2026-10-13), nas duas esteiras. O card continua em
-- Aprovado; a ressalva é o texto da analista explicando a condição, e a tela pinta o card de
-- verde-oliva com a etiqueta "Aprovado c/ ressalva". Nula: aprovação sem ressalva.

-- Prospecção: a ressalva é da decisão (a da análise, mantida quando o comitê ajusta o limite).
ALTER TABLE prospeccao_decisao ADD COLUMN ressalva TEXT;

-- Liberação: a ressalva é do resultado do card, registrada ao mover para Aprovado.
ALTER TABLE liberacao_card ADD COLUMN ressalva TEXT;
