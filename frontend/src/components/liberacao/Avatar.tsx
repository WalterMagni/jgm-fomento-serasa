"use client";

/**
 * Tons do avatar. Saturados o bastante para texto branco passar de 4.5:1 de contraste, e
 * distantes entre si para Andressa e Mychelly não se confundirem no card.
 */
const TONS = ["#612035", "#a8551a", "#1d4ed8", "#047857", "#7c3aed", "#0f766e", "#b45309", "#be123c", "#334155"];

function tom(nome: string) {
  let hash = 0;
  for (let i = 0; i < nome.length; i++) hash = (hash * 31 + nome.charCodeAt(i)) | 0;
  return TONS[Math.abs(hash) % TONS.length];
}

type Props = {
  nome: string;
  iniciais: string;
  tamanho?: "xs" | "sm" | "md";
  /** Anel tracejado: alguém que ainda deve algo (parecer aguardando). */
  aguardando?: boolean;
  className?: string;
};

const TAMANHO = {
  xs: "h-6 w-6 text-[10px]",
  sm: "h-7 w-7 text-[11px]",
  md: "h-9 w-9 text-xs",
};

export default function Avatar({ nome, iniciais, tamanho = "sm", aguardando = false, className = "" }: Props) {
  if (aguardando) {
    return (
      <span
        title={`${nome} · aguardando`}
        className={`inline-flex shrink-0 select-none items-center justify-center rounded-full border-2 border-dashed
          border-slate-400 bg-white font-semibold text-slate-500 dark:border-slate-500 dark:bg-slate-800
          dark:text-slate-300 ${TAMANHO[tamanho]} ${className}`}
      >
        {iniciais}
      </span>
    );
  }
  return (
    <span
      title={nome}
      style={{ backgroundColor: tom(nome) }}
      className={`inline-flex shrink-0 select-none items-center justify-center rounded-full font-semibold text-white
        ring-2 ring-white dark:ring-slate-800 ${TAMANHO[tamanho]} ${className}`}
    >
      {iniciais}
    </span>
  );
}
