/** Raiz do backend sem o /api/v1: as rotas de login e senha ficam em /api/auth. */
export const AUTH_API_BASE_URL = (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1").replace(/\/api\/v1\/?$/, "");

/** Botão principal das telas de entrada (login, cadastro, senha). Também serve para Link. */
export const BOTAO_ENTRADA =
  "group relative inline-flex h-[52px] w-full items-center justify-center gap-2 overflow-hidden rounded-[14px] bg-primary text-[15px] font-semibold text-white shadow-[0_14px_30px_-12px_rgba(97,32,53,0.65)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_18px_36px_-12px_rgba(97,32,53,0.75)] active:translate-y-0 disabled:cursor-not-allowed disabled:opacity-70 disabled:hover:translate-y-0";
