-- Prazo de Nova (alguém assumir a análise) sobe de 12 para 24 horas úteis. Pedido do time em
-- 2026-10-23: "nosso prazo é de 24 horas, porém não são contabilizados finais de semana e
-- feriados" — com 12, card que entrava às 17h já aparecia atrasado no meio da manhã seguinte.
--
-- O prazo é copiado para o card ao entrar no estágio, então mudar o número no código não alcança
-- quem já está em Nova. Estes ganham o novo prazo aqui.
UPDATE prospeccao SET prazo_estagio_horas = 24 WHERE estagio = 'NOVA' AND prazo_estagio_horas = 12;
