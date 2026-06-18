# Guia para Execução do Projeto (Full Stack)

Este guia documenta como iniciar toda a stack (Banco de Dados, Backend Java e Frontend Next.js) de forma automatizada.

## 🚀 1. Executando Localmente (Modo Desenvolvimento)

Criei um script chamado `run-all.sh` que faz todo o trabalho pesado para você no ambiente de desenvolvimento.

Basta rodar no seu terminal:

```bash
bash run-all.sh
```

**O que esse script faz?**

1. Garante que o banco de Dados **PostgreSQL** esteja rodando via Docker (`docker compose up -d postgres`).
2. Se necessário, compila e inicia o **Backend Spring Boot** na porta `8080` em segundo plano (usando o recarregamento).
3. Entra na pasta `/frontend`, instala dependências e sobe o **Frontend Next.js** na porta `3000`.

_Para parar tudo depois, você pode apertar `Ctrl+C` no terminal._

---

## 🐳 2. Executando via Docker Completo (Modo Produção/Isolado)

Se você não quiser rodar nada na sua máquina (precisa apenas do Docker instalado), você pode subir as imagens completas do Frontend, Backend e Banco de Dados rodando de uma só vez:

```bash
docker compose up -d
```

> **Acesso neste modo:**
>
> - Frontend: http://localhost:80
> - Backend API: http://localhost:8080

---

## 🐛 O Problema do E-mail / CORS

A razão pela qual o login deu erro no seu navegador ("Failed to fetch" ou Erro de E-mail) foi devido à nossa **mudança na Classe de Segurança (CORS) do Java**.
O servidor Java antigo ainda estava rodando na sua máquina com as regras bloqueadas e precisava ser reiniciado para aceitar a nova regra do `http://localhost:3000`.

**Acesso Temporário de Testes:**
Para testar, você também pode criar um usuário fresco clicando em "Criar conta" na tela de login. Seu frontend e backend já estão conectados perfeitamente.
