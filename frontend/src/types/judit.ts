/** Espelha JuditService.Situacao e JuditLeitura no backend. */

/** Valor de um agregado. `valor` nulo = "não informado" pela Judit. */
export type JuditContagem = { valor: string | null; quantidade: number };

export type JuditResumo = {
  total: number;
  emSegredo: number;
  comoReu: number;
  comoAutor: number;
  /** A Judit respondeu 404: nada na base dela para o documento. */
  naoEncontrado: boolean;
  tribunais: JuditContagem[];
  areas: JuditContagem[];
  classes: JuditContagem[];
  assuntos: JuditContagem[];
  fases: JuditContagem[];
  estados: JuditContagem[];
};

export type JuditPolo = "REU" | "AUTOR" | "TERCEIRO" | "DESCONHECIDO";

export type JuditProcesso = {
  cnj: string | null;
  instancias: number[];
  autuacao: string | null;
  polo: JuditPolo;
  contrapartes: string[];
  tribunal: string | null;
  /** Vara, câmara, gabinete — o órgão julgador, das instâncias juntas. */
  orgaos: string[];
  uf: string | null;
  cidade: string | null;
  area: string | null;
  classe: string | null;
  assuntos: string[];
  /** Nulo quando a Judit traz 0 — é ausência de dado, não causa de valor zero. */
  valor: number | null;
  distribuidoEm: string | null;
  ultimoAndamento: { data: string | null; texto: string | null } | null;
  emSegredo: boolean;
  fonteAtualizadaEm: string | null;
};

export type JuditProcessos = {
  /** Linhas devolvidas (uma por instância). É o que a Judit cobra. */
  registros: number;
  /** Processos únicos pelo número CNJ. */
  quantidade: number;
  comoReu: number;
  comoAutor: number;
  valorComoReu: number;
  /** Como ré em assunto de protesto/negativação/duplicata contestada. */
  tituloContestadoComoReu: number;
  assuntosComoReu: JuditContagem[];
  /** Vazia no card da Visão 360º; inteira na página de processos. */
  lista: JuditProcesso[];
};

type Consultado = {
  consultadoEm: string;
  consultadoPor: string | null;
  custo: number;
  /** Passou da validade (30 dias). Não reconsulta sozinho. */
  desatualizado: boolean;
};

export type JuditResumoConsultado = Consultado & { dados: JuditResumo };

export type JuditProcessosConsultados = Consultado & {
  /** A empresa passou de 1.000 processos no resumo: a lista pediu só os de ré. */
  somenteReu: boolean;
  totalNoResumo: number | null;
  dados: JuditProcessos;
  /** Situação do último detalhe de cada processo detalhado, por número CNJ. Vazio no card. */
  detalhes: Record<string, JuditSituacaoConsulta>;
};

/** O detalhe é assíncrono: nasce PENDENTE (já cobrado) e o backend acompanha até a Judit terminar. */
export type JuditSituacaoConsulta = "PENDENTE" | "CONCLUIDA" | "FALHOU";

export type JuditParte = { nome: string; polo: "ATIVO" | "PASSIVO" | "INTERESSADO" | "OUTRO"; tipo: string | null; auxiliar: boolean };

export type JuditMovimentacao = { data: string | null; texto: string | null };

/** O processo direto do tribunal: com status e fase, que a base histórica não traz, e todos os andamentos. */
export type JuditProcessoDetalhado = {
  status: string | null;
  fase: string | null;
  situacao: string | null;
  autuacao: string | null;
  classe: string | null;
  assuntos: string[];
  valor: number | null;
  distribuidoEm: string | null;
  tribunal: string | null;
  orgaos: string[];
  juiz: string | null;
  instancias: number[];
  partes: JuditParte[];
  movimentacoes: JuditMovimentacao[];
  anexos: number;
};

export type JuditDetalheConsultado = {
  cnj: string;
  situacao: JuditSituacaoConsulta;
  pedidoEm: string;
  pedidoPor: string | null;
  custo: number;
  concluidoEm: string | null;
  erro: string | null;
  desatualizado: boolean;
  /** Nulo enquanto pendente ou se falhou. */
  dados: { resumoIa: string | null; processo: JuditProcessoDetalhado | null } | null;
};

export type JuditSituacao = {
  cnpj: string;
  /** O usuário logado tem a marca Judit. */
  podeUsar: boolean;
  /** A chave da Judit está no servidor. */
  configurada: boolean;
  validadeDias: number;
  consumo: { gastoMes: number; aviso: number; limite: number };
  precos: { resumo: number; processosPorMil: number; detalhe: number };
  resumo: JuditResumoConsultado | null;
  processos: JuditProcessosConsultados | null;
};
