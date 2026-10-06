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
  if (card.slaEmAtencao || card.silencioEmAtencao) return "atencao";
  return "ok";
}

/**
 * Borda do card. A cor do semáforo vive na borda esquerda, mais grossa; as outras três bordas
 * existem sempre, para o card ter contorno visível no claro e no escuro — antes o cartão sem
 * alerta flutuava sem limite sobre o fundo da coluna.
 */
export const CLASSE_SEMAFORO: Record<Semaforo, string> = {
  ok: "border-slate-200 border-l-slate-300 dark:border-slate-700 dark:border-l-slate-600",
  atencao: "border-[#D1732C]/30 border-l-[#D1732C] dark:border-[#D1732C]/40",
  estourado: "border-red-300 border-l-red-600 dark:border-red-900/60",
};

export const CLASSE_BADGE_SEMAFORO: Record<Semaforo, string> = {
  ok: "text-slate-500 dark:text-slate-400",
  atencao: "text-[#D1732C] font-medium",
  estourado: "text-red-600 dark:text-red-400 font-semibold",
};

/**
 * Texto do prazo.
 *
 * <p>Na coleta de documentos não existe prazo — depende do cliente — então o que se mostra é há
 * quanto tempo ele está em silêncio, que é o número pelo qual o time age.</p>
 */
export function textoPrazo(card: Prospeccao) {
  if (card.estagio === "DOCS_PENDENTES") {
    if (card.diasEmSilencio <= 0) return "aguardando o cliente";
    const dias = `${card.diasEmSilencio} dia${card.diasEmSilencio > 1 ? "s" : ""}`;
    if (card.silencioProlongado) return `${dias} sem retorno — encaminhar para inerte`;
    return `${dias} sem retorno`;
  }
  if (card.prazoEstagioHoras === 0) return "sem prazo";
  const horas = card.horasNoEstagio;
  const sufixo = horas === 1 ? "hora útil" : "horas úteis";
  if (card.slaEstourado) return `${horas} ${sufixo} — atrasado`;
  return `${horas} de ${card.prazoEstagioHoras} ${sufixo}`;
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

/**
 * Ordenação de uma coluna do quadro.
 *
 * <p>O padrão é por urgência: quem está parado há mais tempo aparece primeiro, que é a ordem em
 * que o trabalho deveria sair. Ordenar por nome serve para achar uma empresa específica numa
 * coluna cheia — em Triagem, depois de puxar visão cedente, são dezenas de cards.</p>
 */
export type Ordenacao = "urgencia" | "nome" | "nome_desc";

export const PROXIMA_ORDENACAO: Record<Ordenacao, Ordenacao> = {
  urgencia: "nome",
  nome: "nome_desc",
  nome_desc: "urgencia",
};

export const ROTULO_ORDENACAO: Record<Ordenacao, string> = {
  urgencia: "Por urgência (mais parado primeiro)",
  nome: "Por nome (A a Z)",
  nome_desc: "Por nome (Z a A)",
};

export const ICONE_ORDENACAO: Record<Ordenacao, string> = {
  urgencia: "sort",
  nome: "arrow_downward",
  nome_desc: "arrow_upward",
};

export function ordenarCards<T extends { razaoSocial: string; horasNoEstagio: number; estagioDesde: string }>(
  cards: T[],
  ordenacao: Ordenacao,
): T[] {
  const copia = [...cards];
  if (ordenacao === "urgencia") {
    // Mais tempo parado primeiro; empate desempata pela entrada no estágio.
    return copia.sort(
      (a, b) => b.horasNoEstagio - a.horasNoEstagio || a.estagioDesde.localeCompare(b.estagioDesde),
    );
  }
  const direcao = ordenacao === "nome" ? 1 : -1;
  // localeCompare com "pt-BR" para acento não jogar "Ávila" para o fim da lista.
  return copia.sort((a, b) => direcao * a.razaoSocial.localeCompare(b.razaoSocial, "pt-BR"));
}

/**
 * Confirmação antes de apagar. Lista até cinco nomes para quem selecionou em lote enxergar o que
 * vai sumir, e lembra a alternativa: quem desistiu da empresa deveria reprovar ou remover do radar.
 */
export function confirmarExclusao(cards: { razaoSocial: string }[]): boolean {
  if (cards.length === 0) return false;
  const nomes = cards.slice(0, 5).map(card => `• ${card.razaoSocial}`).join("\n");
  const resto = cards.length > 5 ? `\n… e mais ${cards.length - 5}` : "";
  const titulo = cards.length === 1 ? "Apagar este card?" : `Apagar ${cards.length} cards?`;
  return window.confirm(
    `${titulo}\n\n${nomes}${resto}\n\n` +
      "Checklist e histórico somem junto e não dá para desfazer. Arquivos enviados continuam na pasta CLIENTES.\n\n" +
      "Se a empresa só não vai seguir, use Reprovar ou Remover do radar.",
  );
}
