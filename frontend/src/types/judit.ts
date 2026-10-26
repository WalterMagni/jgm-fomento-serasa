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
 * Algo na pré-consulta que merece olhar antes do On Demand. Os agregados não se cruzam com o polo:
 * "processo criminal" pode ser a empresa como vítima.
 */
export type JuditPontoDeAtencao = {
  tipo: "CRIMINAL" | "RECUPERACAO_FALENCIA" | "EXECUCAO_FISCAL" | "TITULO_CONTESTADO" | "TRABALHISTA";
  titulo: string;
  quantidade: number;
};

/** O que um On Demand deve custar. `processos` nulo: sem pré-consulta, vale um bloco. */
export type JuditEstimativa = {
  processos: number | null;
  blocos: number;
  custo: number;
  /** Perto da virada do bloco: o tribunal pode trazer o bastante para mais um. */
  podeVirarBloco: boolean;
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

/** A pré-consulta: o resumo do Data Lake, com o que olhar e o custo estimado do On Demand. */
export type JuditResumoConsultado = Consultado & {
  dados: JuditResumo;
  pontosDeAtencao: JuditPontoDeAtencao[];
  estimativaReu: JuditEstimativa;
  estimativaAutor: JuditEstimativa;
};

export type JuditProcessosConsultados = Consultado & {
  /** Veio do On Demand. Falso é a lista antiga do Data Lake (fase 1), que continua legível. */
  onDemand: boolean;
  /** Só os processos em que a empresa é ré: On Demand sem o pedido como autora, ou Data Lake acima de 1.000. */
  somenteReu: boolean;
  /** Só na lista antiga do Data Lake: o total do resumo, que a lista podia não alcançar. */
  totalNoResumo: number | null;
  dados: JuditProcessos;
  /** Situação do último detalhe de cada processo detalhado, por número CNJ. Vazio no card. */
  detalhes: Record<string, JuditSituacaoConsulta>;
};

/** Detalhe e On Demand são assíncronos: nascem PENDENTE (já cobrados) e o backend acompanha até a Judit terminar. */
export type JuditSituacaoConsulta = "PENDENTE" | "CONCLUIDA" | "FALHOU";

/** Polo pedido no On Demand: ré (o padrão) ou autora, num pedido à parte. */
export type JuditPoloConsulta = "PASSIVO" | "ATIVO";

/** O último On Demand de um polo. Enquanto um novo está pendente, a lista segue sendo a do último concluído. */
export type JuditOnDemand = {
  polo: JuditPoloConsulta;
  situacao: JuditSituacaoConsulta;
  pedidoEm: string;
  pedidoPor: string | null;
  custoEstimado: number | null;
  /** O estimado enquanto pendente; o real depois que a resposta chega. */
  custo: number;
  /** A estimativa saiu de uma pré-consulta; falso foi "consultar direto". */
  comPreConsulta: boolean;
  concluidoEm: string | null;
  erro: string | null;
  /** Registros devolvidos (o que a Judit cobra). Nulo enquanto pendente. */
  total: number | null;
  desatualizado: boolean;
};

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
  /** `resumo` é a pré-consulta; o On Demand custa `onDemandPorMil` a cada 1.000 processos devolvidos. */
  precos: { resumo: number; onDemandPorMil: number; detalhe: number };
  resumo: JuditResumoConsultado | null;
  processos: JuditProcessosConsultados | null;
  onDemandReu: JuditOnDemand | null;
  onDemandAutor: JuditOnDemand | null;
};

/** Aba Consumo Judit das Configurações. Espelha JuditConsumoService.Painel. */
export type JuditConsultaDoPainel = {
  id: string;
  usuarioNome: string | null;
  documento: string;
  nomeEmpresa: string | null;
  servico: "RESUMO" | "PROCESSOS" | "DETALHE" | "ON_DEMAND";
  polo: JuditPoloConsulta | null;
  cnj: string | null;
  situacao: JuditSituacaoConsulta;
  custoEstimado: number | null;
  custo: number;
  /** Só no On Demand: a estimativa saiu de uma pré-consulta. Nulo nos outros. */
  comPreConsulta: boolean | null;
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
