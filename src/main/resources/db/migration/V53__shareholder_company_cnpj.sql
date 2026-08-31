-- CNPJ completo da matriz da empresa vinculada, vindo de estabelecimentos (Receita).
-- Sem isso a tela só consegue mostrar a raiz de 8 dígitos ("08575056/****-**"), e não há como
-- cadastrar a empresa nem criar o vínculo de parceira, que exigem o CNPJ de 14 dígitos.
ALTER TABLE shareholder_companies ADD COLUMN company_cnpj VARCHAR(14);
