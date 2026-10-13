import type { CanalContato, EstagioProspeccao } from "@/types/prospeccao";

/**
 * Identidade visual de cada estágio da esteira.
 *
 * <p>As cores contam o caminho: cinza na entrada, azul de análise (o mesmo do CNPJ Já), verde-limão
 * no aprovado (o comercial junta os documentos), verde-azulado na conferência, bordô da casa no
 * comitê — que é a decisão da JGM —, laranja da marca enquanto a proposta está com o cliente e verde
 * do contrato em diante. Desfecho negativo é rosa; inerte volta ao cinza. {@code hex} alimenta a
 * variável CSS das animações da trilha.</p>
 */
export const COR_ESTAGIO: Record<
  EstagioProspeccao,
  { hex: string; faixa: string; texto: string; fundo: string; anel: string; chip: string }
> = {
  NOVA: {
    hex: "#64748b",
    faixa: "bg-slate-500",
    texto: "text-slate-700 dark:text-slate-200",
    fundo: "bg-slate-100 dark:bg-slate-700/40",
    anel: "ring-slate-400/60",
    chip: "bg-slate-100 text-slate-700 ring-slate-300 dark:bg-slate-700/60 dark:text-slate-100 dark:ring-slate-600",
  },
  EM_ANALISE: {
    hex: "#2956E0",
    faixa: "bg-[#2956E0]",
    texto: "text-[#2956E0] dark:text-[#9db4ff]",
    fundo: "bg-[#2956E0]/8 dark:bg-[#2956E0]/20",
    anel: "ring-[#2956E0]/45",
    chip: "bg-[#2956E0]/10 text-[#1f43b8] ring-[#2956E0]/30 dark:bg-[#2956E0]/25 dark:text-[#c3d1ff] dark:ring-[#2956E0]/50",
  },
  APROVADO: {
    hex: "#65a30d",
    faixa: "bg-lime-600",
    texto: "text-lime-700 dark:text-lime-300",
    fundo: "bg-lime-50 dark:bg-lime-900/30",
    anel: "ring-lime-500/45",
    chip: "bg-lime-50 text-lime-800 ring-lime-300 dark:bg-lime-900/40 dark:text-lime-100 dark:ring-lime-700",
  },
  AGUARDANDO_ACEITE: {
    hex: "#D1732C",
    faixa: "bg-[#D1732C]",
    texto: "text-[#a8551a] dark:text-[#f0a46b]",
    fundo: "bg-[#D1732C]/10 dark:bg-[#D1732C]/20",
    anel: "ring-[#D1732C]/45",
    chip: "bg-[#D1732C]/12 text-[#8f4714] ring-[#D1732C]/35 dark:bg-[#D1732C]/25 dark:text-[#f7c9a6] dark:ring-[#D1732C]/50",
  },
  DOCUMENTACAO: {
    hex: "#0d9488",
    faixa: "bg-teal-600",
    texto: "text-teal-700 dark:text-teal-300",
    fundo: "bg-teal-50 dark:bg-teal-900/30",
    anel: "ring-teal-500/45",
    chip: "bg-teal-50 text-teal-800 ring-teal-300 dark:bg-teal-900/40 dark:text-teal-100 dark:ring-teal-700",
  },
  COMITE: {
    hex: "#612035",
    faixa: "bg-[#612035]",
    texto: "text-[#612035] dark:text-[#e8a3b6]",
    fundo: "bg-[#612035]/8 dark:bg-[#612035]/30",
    anel: "ring-[#612035]/45",
    chip: "bg-[#612035]/10 text-[#612035] ring-[#612035]/30 dark:bg-[#612035]/40 dark:text-[#f2c4d1] dark:ring-[#e8a3b6]/40",
  },
  CONTRATO: {
    hex: "#059669",
    faixa: "bg-emerald-600",
    texto: "text-emerald-700 dark:text-emerald-300",
    fundo: "bg-emerald-50 dark:bg-emerald-900/30",
    anel: "ring-emerald-500/45",
    chip: "bg-emerald-50 text-emerald-800 ring-emerald-300 dark:bg-emerald-900/40 dark:text-emerald-100 dark:ring-emerald-700",
  },
  HABILITADO: {
    hex: "#047857",
    faixa: "bg-emerald-700",
    texto: "text-emerald-800 dark:text-emerald-200",
    fundo: "bg-emerald-100 dark:bg-emerald-900/40",
    anel: "ring-emerald-600/45",
    chip: "bg-emerald-600 text-white ring-emerald-700 dark:bg-emerald-600 dark:text-white dark:ring-emerald-500",
  },
  REPROVADO: {
    hex: "#be123c",
    faixa: "bg-rose-700",
    texto: "text-rose-700 dark:text-rose-300",
    fundo: "bg-rose-50 dark:bg-rose-950/40",
    anel: "ring-rose-500/45",
    chip: "bg-rose-50 text-rose-800 ring-rose-300 dark:bg-rose-950/50 dark:text-rose-100 dark:ring-rose-800",
  },
  RECUSADO_CLIENTE: {
    hex: "#e11d48",
    faixa: "bg-rose-500",
    texto: "text-rose-700 dark:text-rose-300",
    fundo: "bg-rose-50 dark:bg-rose-950/40",
    anel: "ring-rose-400/45",
    chip: "bg-rose-50 text-rose-800 ring-rose-300 dark:bg-rose-950/50 dark:text-rose-100 dark:ring-rose-800",
  },
  INERTE: {
    hex: "#94a3b8",
    faixa: "bg-slate-400",
    texto: "text-slate-500 dark:text-slate-400",
    fundo: "bg-slate-100 dark:bg-slate-800",
    anel: "ring-slate-300/60",
    chip: "bg-slate-100 text-slate-600 ring-slate-300 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-600",
  },
};

/**
 * Canais de cobrança com a cor de cada marca: verde do WhatsApp, azul do e-mail. Quem bate o olho
 * no histórico sabe por onde o cliente foi cobrado sem ler.
 */
export const COR_CANAL: Record<CanalContato, { tile: string; texto: string; ativo: string; rotulo: string }> = {
  WHATSAPP: {
    tile: "bg-[#25D366] text-white",
    texto: "text-[#128C7E] dark:text-[#5fe39a]",
    ativo: "bg-[#25D366] text-white shadow-[0_2px_10px_-2px_rgba(37,211,102,0.55)]",
    rotulo: "WhatsApp",
  },
  EMAIL: {
    tile: "bg-[#2956E0] text-white",
    texto: "text-[#2956E0] dark:text-[#9db4ff]",
    ativo: "bg-[#2956E0] text-white shadow-[0_2px_10px_-2px_rgba(41,86,224,0.5)]",
    rotulo: "E-mail",
  },
  LIGACAO: { tile: "bg-slate-600 text-white", texto: "text-slate-600 dark:text-slate-300", ativo: "bg-slate-700 text-white", rotulo: "Ligação" },
  REUNIAO: { tile: "bg-slate-600 text-white", texto: "text-slate-600 dark:text-slate-300", ativo: "bg-slate-700 text-white", rotulo: "Reunião" },
  SISTEMA: { tile: "bg-slate-400 text-white", texto: "text-slate-500", ativo: "bg-slate-500 text-white", rotulo: "Sistema" },
};
