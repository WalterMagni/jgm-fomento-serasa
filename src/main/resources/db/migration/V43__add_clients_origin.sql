-- Origem/papel da empresa para condicionar a exigência do código do cliente (4R).
-- CARTEIRA / MANUAL = cedente potencial (precisa de código 4R; PDF traz o cedente como código).
-- SACADO_PRACA = empresa criada a partir de um sacado da Praça de Pagamento (vem com CNPJ no PDF,
--                não precisa de código 4R, a menos que vire cliente da carteira depois).
-- Sem backfill: linhas existentes ficam NULL e seguem tratadas como "precisa de código" (âmbar).
ALTER TABLE clients ADD COLUMN origin VARCHAR(20);
