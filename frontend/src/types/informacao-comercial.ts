// Ficha comercial: o que um parceiro (FIDC, factoring) informa sobre o cedente. Espelho do
// GET /company/commercial-information; valores e datas chegam como texto no formato brasileiro.

export type FichaComercialApi = {
  id: string;
  cnpj?: string | null;
  empresa?: string | null;
  authorName?: string | null;
  createdAt?: string | null;
  data?: string | null;
  tipo?: string | null;
  parceiro?: string | null;
  clienteDesde?: string | null;
  ultimaOperacaoData?: string | null;
  ultimaOperacaoValor?: string | null;
  limite?: string | null;
  riscoDuplicata?: string | null;
  riscoCheque?: string | null;
  riscoComissaria?: string | null;
  /** Data do valor vencido. */
  vencidosData?: string | null;
  /** Valor vencido, em reais. */
  vencidosValorMonetario?: string | null;
  /** Apesar do nome, é a data de vencimento da última duplicata. */
  vencidosValor?: string | null;
  vop?: string | null;
  pontual?: string | null;
  atraso?: string | null;
  recompra?: string | null;
  cartorio?: string | null;
  observacao?: string | null;
};

/** Ficha com os números já convertidos, para ordenar, somar e desenhar barras. */
export type FichaComercial = FichaComercialApi & {
  limiteN: number;
  riscoN: number;
  vencidoN: number;
  /** Momento da ficha (campo "data"); 0 quando não informada. */
  dataT: number;
  liquidacao: { chave: LiquidacaoChave; valor: number }[];
  /** Nome sem acento e CNPJ, para a busca. */
  chave: string;
};

export type LiquidacaoChave = "pontual" | "atraso" | "cartorio" | "recompra";

/** L1 a L4: como os títulos do cedente foram liquidados no parceiro. Somam perto de 100%. */
export const LIQUIDACAO: Record<LiquidacaoChave, { rotulo: string; nivel: string; cor: string }> = {
  pontual: { rotulo: "Pontual", nivel: "L1", cor: "#1F7A5C" },
  atraso: { rotulo: "Atraso", nivel: "L2", cor: "#D1732C" },
  cartorio: { rotulo: "Cartório", nivel: "L3", cor: "#B42345" },
  recompra: { rotulo: "Recompra", nivel: "L4", cor: "#612035" },
};

/** "1.000.000,00" → 1000000. Texto vazio ou inválido vira 0. */
export function numeroBr(valor: string | null | undefined) {
  if (!valor) return 0;
  const n = Number(valor.replace(/\./g, "").replace(",", ".").replace(/[^\d.-]/g, ""));
  return Number.isFinite(n) ? n : 0;
}

/** "31/08/2026" → timestamp; 0 quando vazio ou fora do formato. */
export function dataBr(valor: string | null | undefined) {
  const m = valor?.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  return m ? new Date(Number(m[3]), Number(m[2]) - 1, Number(m[1])).getTime() : 0;
}

export function prepararFicha(f: FichaComercialApi, normalizar: (t: string) => string): FichaComercial {
  return {
    ...f,
    limiteN: numeroBr(f.limite),
    riscoN: numeroBr(f.riscoDuplicata) + numeroBr(f.riscoCheque) + numeroBr(f.riscoComissaria),
    vencidoN: numeroBr(f.vencidosValorMonetario),
    dataT: dataBr(f.data),
    liquidacao: (Object.keys(LIQUIDACAO) as LiquidacaoChave[]).map(chave => ({ chave, valor: numeroBr(f[chave]) })),
    chave: `${normalizar(f.empresa ?? "")} ${f.cnpj ?? ""}`,
  };
}

/** Percentual com uma casa, sem zero sobrando: 99,34 → "99,3%", 10 → "10%". */
export function formatPct(valor: number) {
  return `${valor.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`;
}

const moedaComCentavos = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const moedaInteira = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });

/** Reais sem centavos, exceto valores pequenos, que arredondados virariam "R$ 0". */
export function formatMoedaFicha(valor: number) {
  return (valor < 100 ? moedaComCentavos : moedaInteira).format(valor);
}
