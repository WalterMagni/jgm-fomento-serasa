/**
 * Classes dos botões da repaginação. Strings (não componente) para servir a `<button>`,
 * `<Link>` e `<a>` sem embrulho.
 */
const BASE =
  "inline-flex h-11 shrink-0 items-center justify-center gap-2 whitespace-nowrap rounded-xl px-[18px] text-sm font-semibold " +
  "transition-[background-color,border-color,box-shadow,transform] duration-150 active:translate-y-px " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 " +
  "disabled:cursor-not-allowed disabled:opacity-50";

export const BOTAO = {
  primario: `${BASE} bg-primary text-white shadow-[0_10px_22px_-12px_rgba(97,32,53,0.8)] hover:bg-primary-hover`,
  fantasma: `${BASE} border border-linha bg-white text-grafite hover:border-[#C9C4B8]`,
  laranja: `${BASE} bg-secondary text-[#1D1408] hover:brightness-105`,
  /** Cores das marcas dos provedores, as mesmas dos botões de consulta da Visão 360º. */
  cnpja: `${BASE} bg-[#2956E0] text-white hover:bg-[#2148c0]`,
  serasa: `${BASE} bg-[#E4006F] text-white hover:bg-[#c4005f]`,
  /** Verde do logo da Judit. Texto escuro: branco sobre esse verde não tem contraste. */
  judit: `${BASE} bg-[#15BE62] text-[#05301A] hover:bg-[#13AB58]`,
} as const;
