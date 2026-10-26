-- Dois motivos de reprovação a mais (pedido do time em 2026-10-26): "Cedente com Liminar" e
-- "Sacado com Liminar". Cada um entra logo depois do motivo do mesmo lado; se o admin tiver
-- apagado ou renomeado esse vizinho, vai para o fim da lista. Se o admin já tiver criado o motivo
-- pela tela, fica como está.

UPDATE liberacao_motivo_reprovacao SET ordem = ordem + 1
 WHERE ordem > (SELECT ordem FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Cedente em Saída de Risco'))
   AND NOT EXISTS (SELECT 1 FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Cedente com Liminar'));

INSERT INTO liberacao_motivo_reprovacao (nome, ordem)
SELECT 'Cedente com Liminar',
       COALESCE((SELECT ordem + 1 FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Cedente em Saída de Risco')),
                (SELECT COALESCE(max(ordem), 0) + 1 FROM liberacao_motivo_reprovacao))
ON CONFLICT ((upper(nome))) DO NOTHING;

UPDATE liberacao_motivo_reprovacao SET ordem = ordem + 1
 WHERE ordem > (SELECT ordem FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Sacado sem Porte / Concentração'))
   AND NOT EXISTS (SELECT 1 FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Sacado com Liminar'));

INSERT INTO liberacao_motivo_reprovacao (nome, ordem)
SELECT 'Sacado com Liminar',
       COALESCE((SELECT ordem + 1 FROM liberacao_motivo_reprovacao WHERE upper(nome) = upper('Sacado sem Porte / Concentração')),
                (SELECT COALESCE(max(ordem), 0) + 1 FROM liberacao_motivo_reprovacao))
ON CONFLICT ((upper(nome))) DO NOTHING;
