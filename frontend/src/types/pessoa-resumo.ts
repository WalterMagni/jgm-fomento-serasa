// Linha da lista de Pessoas Físicas — espelho do PessoaResumoResponse do backend.

export type SituacaoPessoa = "LIMPO" | "COM_RESTRICAO" | "CRITICO";

export type TipoRestricao =
  | "PEFIN"
  | "REFIN"
  | "CHEQUE"
  | "PROTESTO"
  | "DIVIDA_VENCIDA"
  | "ACAO_JUDICIAL"
  | "FALENCIA";

export interface PessoaResumo {
  cpf: string;
  nome: string | null;
  cidade: string | null;
  uf: string | null;
  consultaEm: string;
  situacao: SituacaoPessoa;
  totalOcorrencias: number;
  dividaTotal: number;
  /** Só os tipos com ao menos uma ocorrência. */
  restricoes: { tipo: TipoRestricao; quantidade: number; valor: number }[];
  /** Empresas da carteira em que a pessoa é sócia. */
  vinculos: { cnpj: string; razaoSocial: string | null }[];
}

/** Rótulos iguais aos da tela de análise da pessoa; cor da fatia na barra de restrições. */
export const TIPO_RESTRICAO: Record<TipoRestricao, { rotulo: string; cor: string }> = {
  PEFIN: { rotulo: "Pefin", cor: "#D1732C" },
  REFIN: { rotulo: "Refin", cor: "#E8A86F" },
  CHEQUE: { rotulo: "Cheques", cor: "#2956E0" },
  PROTESTO: { rotulo: "Protestos", cor: "#B42345" },
  DIVIDA_VENCIDA: { rotulo: "Dívidas vencidas", cor: "#0F766E" },
  ACAO_JUDICIAL: { rotulo: "Ações judiciais", cor: "#612035" },
  FALENCIA: { rotulo: "Falências", cor: "#262833" },
};

export const SITUACAO: Record<SituacaoPessoa, { rotulo: string; cor: string; fundo: string; texto: string }> = {
  LIMPO: { rotulo: "Sem restrições", cor: "#1F7A5C", fundo: "#E2F0E9", texto: "#17583F" },
  COM_RESTRICAO: { rotulo: "Com restrição", cor: "#B4601F", fundo: "#FBEBDD", texto: "#7A3E12" },
  CRITICO: { rotulo: "Crítico", cor: "#B42345", fundo: "#F8E1E6", texto: "#8E1B37" },
};

/**
 * Mesma regra do SituacaoPessoa do backend, para telas que montam a situação a partir da análise
 * completa (a lista já recebe pronta do resumo): crítico com 3+ ocorrências ou dívida > R$ 50 mil.
 */
export function classificarSituacao(ocorrencias: number, divida: number): SituacaoPessoa {
  if (ocorrencias <= 0) return "LIMPO";
  if (ocorrencias >= 3 || divida > 50000) return "CRITICO";
  return "COM_RESTRICAO";
}

/** Consulta com mais dias que isso pede reconsulta antes de decidir crédito. */
export const DIAS_CONSULTA_VELHA = 90;

/** Custo de uma consulta Serasa PF (billing.serasa.pf-cost-per-query no backend). */
export const CUSTO_CONSULTA_PF = "R$ 15,52";
