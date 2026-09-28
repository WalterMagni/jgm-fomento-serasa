import { EstagioProspeccao, Prospeccao } from "@/types/prospeccao";

export function formatCnpj(cnpj: string) {
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

export function formatDate(iso: string | null | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("pt-BR");
}

export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export function formatBytes(bytes: number | null | undefined) {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Semáforo do card. Lê o que o backend calculou em vez de recontar dias úteis aqui — duas
 * contas independentes divergiriam no primeiro feriado.
 */
export type Semaforo = "ok" | "atencao" | "estourado";

export function semaforoDe(card: Prospeccao): Semaforo {
  if (card.slaEstourado || card.silencioProlongado) return "estourado";
  if (card.slaEmAtencao) return "atencao";
  return "ok";
}

export const CLASSE_SEMAFORO: Record<Semaforo, string> = {
  ok: "border-l-transparent",
  atencao: "border-l-[#D1732C]",
  estourado: "border-l-red-600",
};

export const CLASSE_BADGE_SEMAFORO: Record<Semaforo, string> = {
  ok: "text-slate-500 dark:text-slate-400",
  atencao: "text-[#D1732C] font-medium",
  estourado: "text-red-600 dark:text-red-400 font-semibold",
};

/** Texto do prazo. "no prazo" em vez de um número nu, que não diz nada sozinho. */
export function textoPrazo(card: Prospeccao) {
  if (card.prazoEstagioDias === 0) return "sem prazo";
  const dias = card.diasNoEstagio;
  const sufixo = dias === 1 ? "dia útil" : "dias úteis";
  if (card.slaEstourado) return `${dias} ${sufixo} — atrasado`;
  return `${dias} de ${card.prazoEstagioDias} ${sufixo}`;
}

export const ICONE_ESTAGIO: Record<EstagioProspeccao, string> = {
  TRIAGEM: "inbox",
  EM_ANALISE: "search",
  APROVADO: "task_alt",
  DOCS_PENDENTES: "folder_open",
  DOCS_COMPLETOS: "folder_supervised",
  PRONTO_HABILITACAO: "flag",
  REPROVADO: "block",
  REMOVIDO_RADAR: "visibility_off",
};
