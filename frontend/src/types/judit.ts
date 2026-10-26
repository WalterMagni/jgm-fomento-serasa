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
  /** Estadual, Federal, do Trabalho... */
  justicas: JuditContagem[];
};

/**
 * Algo no resumo que merece olhar antes da lista. Os agregados não se cruzam com o polo: "processo
 * criminal" pode ser a empresa como vítima.
 */
export type JuditPontoDeAtencao = {
  tipo: "CRIMINAL" | "RECUPERACAO_FALENCIA" | "EXECUCAO_FISCAL" | "TITULO_CONTESTADO" | "TRABALHISTA";
  titulo: string;
  quantidade: number;
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
  /** Pelo número CNJ. */
  justica: JuditJustica;
  /** Deduzida do último andamento — a base histórica da Judit não traz. Nula sem andamento. */
  situacao: JuditSituacaoProcesso | null;
  /** Banco, financeira, FIDC ou securitizadora do outro lado. */
  segmentoFinanceiro: boolean;
};

export type JuditJustica = "ESTADUAL" | "TRABALHO" | "FEDERAL" | "ELEITORAL" | "MILITAR" | "SUPERIOR" | "OUTRA";

export type JuditSituacaoProcesso = "EM_TRAMITACAO" | "SUSPENSO" | "ARQUIVADO";

// ── Painel no desenho do Vadu (espelha JuditPainel no backend) ─────────────────────────────────

export type JuditSoma = { quantidade: number; valor: number };

export type JuditArea = {
  /** Nula quando a Judit não informou. */
  area: string | null;
  quantidade: number;
  porcentagem: number;
  primeiroProcesso: string | null;
  ultimaMovimentacao: string | null;
};

/** `periodo` é o ano ("2026") ou o mês ("2026-08"). */
export type JuditPorPeriodo = { periodo: string; reu: number; autor: number; outros: number };

export type JuditPorSituacao = { situacao: JuditSituacaoProcesso | null; quantidade: number; valor: number };

export type JuditPorPolo = {
  soma: JuditSoma;
  situacoes: JuditPorSituacao[];
  classes: JuditContagem[];
  assuntos: JuditContagem[];
};

export type JuditParteResumo = { nome: string; quantidade: number; empresaRe: number; empresaAutora: number; valor: number };

export type JuditUltimo = {
  cnj: string | null;
  tribunal: string | null;
  uf: string | null;
  classe: string | null;
  polo: JuditPolo;
  situacao: JuditSituacaoProcesso | null;
  distribuidoEm: string | null;
  ultimaMovimentacao: string | null;
};

export type JuditGrupoPolo = "REU" | "AUTOR" | "OUTROS";

/** Um recorte da lista: todos os processos, ou os de uma justiça. */
export type JuditBloco = {
  total: JuditSoma;
  comoReu: JuditSoma;
  comoAutor: JuditSoma;
  outros: JuditSoma;
  porJustica: Partial<Record<JuditJustica, JuditSoma>>;
  areas: JuditArea[];
  porUf: JuditContagem[];
  porAno: JuditPorPeriodo[];
  ultimos12Meses: JuditPorPeriodo[];
  porPolo: Record<JuditGrupoPolo, JuditPorPolo>;
  segmentoFinanceiro: JuditSoma;
  /** Vazia no card da Visão 360º. */
  partes: JuditParteResumo[];
  ultimos: JuditUltimo[];
};

/** `porJustica` vazio no card: lá só o geral. */
export type JuditPainel = { geral: JuditBloco; porJustica: Partial<Record<JuditJustica, JuditBloco>> };

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

export type JuditResumoConsultado = Consultado & { dados: JuditResumo; pontosDeAtencao: JuditPontoDeAtencao[] };

export type JuditProcessosConsultados = Consultado & {
  /** A empresa passou de 1.000 processos no resumo: a lista pediu só os de ré. */
  somenteReu: boolean;
  totalNoResumo: number | null;
  dados: JuditProcessos;
  /** Os blocos no desenho do Vadu, calculados da lista. */
  painel: JuditPainel;
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
  /** Mês do calendário. `limite` já soma o `liberado` pelo admin; a franquia é o piso da conta. */
  consumo: { gastoMes: number; franquia: number; limite: number; liberado: number };
  precos: { resumo: number; processosPorMil: number; detalhe: number };
  resumo: JuditResumoConsultado | null;
  processos: JuditProcessosConsultados | null;
};

/** Aba Consumo Judit das Configurações. Espelha JuditConsumoService.Painel. */
export type JuditConsultaDoPainel = {
  id: string;
  usuarioNome: string | null;
  documento: string;
  nomeEmpresa: string | null;
  servico: "RESUMO" | "PROCESSOS" | "DETALHE";
  /** Só no detalhe de processo. */
  cnj: string | null;
  situacao: JuditSituacaoConsulta;
  custo: number;
  totalProcessos: number | null;
  criadoEm: string;
};

export type JuditLiberacao = { id: string; valor: number; motivo: string | null; adminNome: string | null; criadoEm: string };

export type JuditPainelConsumo = {
  /** Sempre o mês do calendário, não o período da tabela. */
  mes: JuditSituacao["consumo"];
  mesReferencia: string;
  /** Quem vê é admin do portal: mostra o botão de liberar. */
  podeLiberar: boolean;
  liberacoes: JuditLiberacao[];
  dias: number;
  consultas: JuditConsultaDoPainel[];
};
