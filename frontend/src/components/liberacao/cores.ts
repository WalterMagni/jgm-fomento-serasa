import type { CorLiberacao } from "@/types/liberacao";

/**
 * Tons de cada cor da paleta, no claro e no escuro.
 *
 * <p>O banco guarda só o nome da cor; o tom mora aqui. Etiqueta usa texto escuro sobre fundo
 * claro no tema claro e o inverso no escuro, para passar de 4.5:1 de contraste nos dois.</p>
 */
export const TOM: Record<CorLiberacao, { nome: string; faixa: string; chip: string; amostra: string }> = {
  VERMELHO: { nome: "Vermelho", faixa: "bg-rose-500", chip: "bg-rose-100 text-rose-800 dark:bg-rose-500/25 dark:text-rose-200", amostra: "bg-rose-500" },
  LARANJA: { nome: "Laranja", faixa: "bg-orange-500", chip: "bg-orange-100 text-orange-800 dark:bg-orange-500/25 dark:text-orange-200", amostra: "bg-orange-500" },
  AMARELO: { nome: "Amarelo", faixa: "bg-amber-400", chip: "bg-amber-100 text-amber-900 dark:bg-amber-400/25 dark:text-amber-100", amostra: "bg-amber-400" },
  VERDE: { nome: "Verde", faixa: "bg-emerald-500", chip: "bg-emerald-100 text-emerald-800 dark:bg-emerald-500/25 dark:text-emerald-200", amostra: "bg-emerald-500" },
  AZUL: { nome: "Azul", faixa: "bg-blue-500", chip: "bg-blue-100 text-blue-800 dark:bg-blue-500/25 dark:text-blue-200", amostra: "bg-blue-500" },
  ROXO: { nome: "Roxo", faixa: "bg-violet-500", chip: "bg-violet-100 text-violet-800 dark:bg-violet-500/25 dark:text-violet-200", amostra: "bg-violet-500" },
  ROSA: { nome: "Rosa", faixa: "bg-pink-500", chip: "bg-pink-100 text-pink-800 dark:bg-pink-500/25 dark:text-pink-200", amostra: "bg-pink-500" },
  CINZA: { nome: "Cinza", faixa: "bg-slate-400", chip: "bg-slate-200 text-slate-700 dark:bg-slate-500/30 dark:text-slate-200", amostra: "bg-slate-400" },
};
