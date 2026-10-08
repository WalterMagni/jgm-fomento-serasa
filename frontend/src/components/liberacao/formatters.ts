import type { EtapaLiberacao, LiberacaoCard, PosicaoParecer } from "@/types/liberacao";

export function formatDocumento(documento: string | null | undefined) {
  if (!documento) return "";
  const digitos = documento.replace(/\D/g, "");
  if (digitos.length === 14) return digitos.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
  if (digitos.length === 11) return digitos.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
  return documento;
}

/** Máscara progressiva enquanto digita: CPF até 11 dígitos, CNPJ a partir do 12º. */
export function mascararDocumento(valor: string) {
  const d = valor.replace(/\D/g, "").slice(0, 14);
  if (d.length <= 11) {
    return d
      .replace(/^(\d{3})(\d)/, "$1.$2")
      .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
      .replace(/\.(\d{3})(\d)/, ".$1-$2");
  }
  return d
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}

const MOEDA = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const MOEDA_CURTA = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", notation: "compact", maximumFractionDigits: 1 });

export function formatMoeda(valor: number | null | undefined) {
  return valor == null ? "—" : MOEDA.format(valor);
}

/** "R$ 1,2 mi" para o cabeçalho da coluna, onde o valor cheio não cabe. */
export function formatMoedaCurta(valor: number) {
  return MOEDA_CURTA.format(valor);
}

/** Entrada livre em pt-BR ("80.000,50", "80000", "R$ 1.234") para número. Vazio = nulo. */
export function parseMoeda(texto: string): number | null {
  const limpo = texto.replace(/[^\d,.-]/g, "");
  if (!limpo) return null;
  const normalizado = limpo.includes(",") ? limpo.replace(/\./g, "").replace(",", ".") : limpo.replace(/\.(?=\d{3}(\D|$))/g, "");
  const numero = Number(normalizado);
  return Number.isFinite(numero) ? Math.round(numero * 100) / 100 : null;
}

/** Número para o campo de edição, no formato que o próprio parseMoeda lê de volta. */
export function moedaParaCampo(valor: number | null | undefined) {
  return valor == null ? "" : valor.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatDataHora(iso: string | null | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/** "há 5 min", "há 3 h", "ontem", "12/09". Para atividade, onde a hora exata importa menos. */
export function tempoRelativo(iso: string | null | undefined) {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.round(diff / 60000);
  if (min < 1) return "agora";
  if (min < 60) return `há ${min} min`;
  const horas = Math.round(min / 60);
  if (horas < 24) return `há ${horas} h`;
  const dias = Math.round(horas / 24);
  if (dias === 1) return "ontem";
  if (dias < 7) return `há ${dias} dias`;
  return new Date(iso).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" });
}

/** Valor de `<input type="datetime-local">` a partir do ISO do servidor, e vice-versa. */
export function isoParaCampoData(iso: string | null | undefined) {
  return iso ? iso.slice(0, 16) : "";
}

export type SituacaoPrazo = "ok" | "perto" | "vencido";

/** Âmbar faltando menos de 24 h, vermelho depois do vencimento. Finalizado não corre prazo. */
export function situacaoPrazo(card: Pick<LiberacaoCard, "prazo" | "finalizadoEm">): SituacaoPrazo | null {
  if (!card.prazo || card.finalizadoEm) return null;
  const restante = new Date(card.prazo).getTime() - Date.now();
  if (restante < 0) return "vencido";
  if (restante < 24 * 60 * 60 * 1000) return "perto";
  return "ok";
}

export function textoPrazo(prazo: string) {
  const data = new Date(prazo);
  const hoje = new Date();
  const amanha = new Date(hoje);
  amanha.setDate(hoje.getDate() + 1);
  const hora = data.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  if (data.toDateString() === hoje.toDateString()) return `hoje ${hora}`;
  if (data.toDateString() === amanha.toDateString()) return `amanhã ${hora}`;
  return `${data.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })} ${hora}`;
}

/**
 * Cor de cada etapa. A mesma em cabeçalho de coluna, badge e botão de mover, para a pessoa
 * reconhecer a coluna pela cor sem ler o nome.
 */
export const COR_ETAPA: Record<EtapaLiberacao, { faixa: string; texto: string; fundo: string; anel: string; hex: string }> = {
  ORIGEM: {
    faixa: "bg-slate-500",
    texto: "text-slate-700 dark:text-slate-200",
    fundo: "bg-slate-100 dark:bg-slate-700/50",
    anel: "ring-slate-400/50",
    hex: "#64748b",
  },
  COMITE: {
    faixa: "bg-[#612035]",
    texto: "text-[#612035] dark:text-[#e8a3b6]",
    fundo: "bg-[#612035]/8 dark:bg-[#612035]/30",
    anel: "ring-[#612035]/40",
    hex: "#612035",
  },
  PENDENCIA: {
    faixa: "bg-[#D1732C]",
    texto: "text-[#a8551a] dark:text-[#f0a46b]",
    fundo: "bg-[#D1732C]/10 dark:bg-[#D1732C]/20",
    anel: "ring-[#D1732C]/40",
    hex: "#D1732C",
  },
  FINALIZADO: {
    faixa: "bg-slate-700 dark:bg-slate-300",
    texto: "text-slate-700 dark:text-slate-200",
    fundo: "bg-slate-200/70 dark:bg-slate-700/50",
    anel: "ring-slate-500/40",
    hex: "#334155",
  },
};

export const ICONE_ETAPA: Record<EtapaLiberacao, string> = {
  ORIGEM: "inbox",
  COMITE: "gavel",
  PENDENCIA: "hourglass_top",
  FINALIZADO: "task_alt",
};

/** Resultado e decisão de sacado: verde, vermelho e âmbar, nos dois temas. */
export const COR_RESULTADO: Record<"APROVADO" | "REPROVADO" | "PARCIAL", string> = {
  APROVADO: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200",
  REPROVADO: "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-200",
  PARCIAL: "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100",
};

export const ICONE_RESULTADO: Record<"APROVADO" | "REPROVADO" | "PARCIAL", string> = {
  APROVADO: "check_circle",
  REPROVADO: "cancel",
  PARCIAL: "remove_circle",
};

/** Cor do anel do avatar de parecer. Aguardando fica tracejado, sem cor. */
export const COR_POSICAO: Record<PosicaoParecer, string> = {
  FAVORAVEL: "bg-emerald-600 text-white",
  COM_RESSALVAS: "bg-amber-500 text-white",
  DESFAVORAVEL: "bg-rose-700 text-white",
};

export const ICONE_POSICAO: Record<PosicaoParecer, string> = {
  FAVORAVEL: "thumb_up",
  COM_RESSALVAS: "warning",
  DESFAVORAVEL: "block",
};

/** Quem ainda deve parecer na rodada vigente. Parecer de usuário removido não conta. */
export function aguardando(card: Pick<LiberacaoCard, "pareceres">) {
  return card.pareceres.filter(parecer => !parecer.posicao && parecer.usuarioId);
}

export function confirmar(mensagem: string) {
  return typeof window !== "undefined" && window.confirm(mensagem);
}
