-- Aviso por e-mail da esteira de liberação. O navegador só mostra aviso do sistema em HTTPS, e o
-- portal roda em HTTP na rede interna; o e-mail chega pelo Outlook, que avisa no Windows com o
-- portal aberto ou não. Cada pessoa liga e desliga no sino. Ligado por padrão.
ALTER TABLE users ADD COLUMN email_liberacao BOOLEAN NOT NULL DEFAULT TRUE;
