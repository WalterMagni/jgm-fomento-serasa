import { ACAO_PARA_ESTAGIO, EstagioProspeccao, Prospeccao } from "@/types/prospeccao";

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

const MOEDA = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function formatMoeda(valor: number | null | undefined) {
  return valor == null ? "—" : MOEDA.format(valor);
}

/** R$ 1,2 mi · R$ 350 mil — para selo, onde não cabe o valor inteiro. */
export function formatMoedaCurta(valor: number) {
  if (valor >= 1_000_000) return `R$ ${(valor / 1_000_000).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mi`;
  if (valor >= 1_000) return `R$ ${Math.round(valor / 1_000).toLocaleString("pt-BR")} mil`;
  return MOEDA.format(valor);
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
 * <p>No aprovado, na documentação e no aceite não existe prazo — depende do cliente — então o que
 * se mostra é há quanto tempo ele está em silêncio, que é o número pelo qual o time age.</p>
 */
export function textoPrazo(card: Prospeccao) {
  if (card.estagio === "APROVADO" || card.estagio === "DOCUMENTACAO" || card.estagio === "AGUARDANDO_ACEITE") {
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
  NOVA: "inbox",
  EM_ANALISE: "search",
  APROVADO: "thumb_up",
  DOCUMENTACAO: "folder_open",
  AGUARDANDO_ACEITE: "handshake",
  COMITE: "groups",
  CONTRATO: "contract",
  HABILITADO: "verified",
  REPROVADO: "block",
  RECUSADO_CLIENTE: "do_not_disturb_on",
  INERTE: "visibility_off",
};

/**
 * Texto do botão que move o card, conforme de onde ele sai.
 *
 * <p>O mesmo destino pode ter intenções diferentes — voltar ao comitê é contraproposta quando sai
 * do aceite e correção quando sai do contrato — e o botão precisa dizer qual.</p>
 */
export function rotuloAcao(de: EstagioProspeccao, para: EstagioProspeccao) {
  if (para === "EM_ANALISE" && de === "COMITE") return "Comitê devolveu para análise";
  if (para === "COMITE") {
    if (de === "AGUARDANDO_ACEITE") return "Contraproposta: voltar ao comitê";
    if (de === "CONTRATO") return "Voltar para o comitê";
  }
  if (para === "APROVADO" && de === "DOCUMENTACAO") return "Devolver ao comercial";
  return ACAO_PARA_ESTAGIO[para];
}

/**
 * Passagem que só acontece pela ação própria — decisão, resposta do cliente, cancelamento. Espelha
 * {@code EstagioProspeccao.acaoPropria} no servidor, que recusa a troca simples nesses casos.
 */
export type AcaoPropria = "decidir" | "aceite" | "comite" | "resultado" | "concluir" | "cancelar";

/** Do Aprovado ao Contrato o card pode ser cancelado e cair em Reprovado. Espelha {@code cancelavel()}. */
export function cancelavel(estagio: EstagioProspeccao) {
  return (["APROVADO", "DOCUMENTACAO", "COMITE", "AGUARDANDO_ACEITE", "CONTRATO"] as EstagioProspeccao[]).includes(estagio);
}

export function acaoPropria(de: EstagioProspeccao, para: EstagioProspeccao): AcaoPropria | null {
  if (de === "EM_ANALISE" && (para === "APROVADO" || para === "REPROVADO")) return "decidir";
  if (de === "AGUARDANDO_ACEITE" && (para === "CONTRATO" || para === "RECUSADO_CLIENTE" || para === "COMITE")) return "aceite";
  if (para === "COMITE") return "comite";
  if (de === "COMITE") return "resultado";
  if (de === "CONTRATO" && para === "HABILITADO") return "concluir";
  if (para === "REPROVADO" && cancelavel(de)) return "cancelar";
  return null;
}

/** Destinos que levam o card para trás ou para fora: aparecem depois, em botão discreto. */
export function ehAvanco(de: EstagioProspeccao, para: EstagioProspeccao) {
  const ordem: EstagioProspeccao[] = ["NOVA", "EM_ANALISE", "APROVADO", "DOCUMENTACAO", "COMITE", "AGUARDANDO_ACEITE", "CONTRATO", "HABILITADO"];
  const i = ordem.indexOf(de);
  const j = ordem.indexOf(para);
  return i >= 0 && j > i;
}

/**
 * Ordenação de uma coluna do quadro.
 *
 * <p>O padrão é por urgência: quem está parado há mais tempo aparece primeiro, que é a ordem em
 * que o trabalho deveria sair. Ordenar por nome serve para achar uma empresa específica numa
 * coluna cheia — em Nova, depois de puxar visão cedente, são dezenas de cards.</p>
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
 * vai sumir, e lembra a alternativa: quem desistiu da empresa deveria reprovar ou mover para inerte.
 */
export function confirmarExclusao(cards: { razaoSocial: string }[]): boolean {
  if (cards.length === 0) return false;
  const nomes = cards.slice(0, 5).map(card => `• ${card.razaoSocial}`).join("\n");
  const resto = cards.length > 5 ? `\n… e mais ${cards.length - 5}` : "";
  const titulo = cards.length === 1 ? "Apagar este card?" : `Apagar ${cards.length} cards?`;
  return window.confirm(
    `${titulo}\n\n${nomes}${resto}\n\n` +
      "Checklist e histórico somem junto e não dá para desfazer. Arquivos enviados continuam na pasta CLIENTES.\n\n" +
      "Se a empresa só não vai seguir, use Reprovar ou Mover para inerte.",
  );
}
