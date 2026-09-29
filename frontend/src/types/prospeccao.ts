/** Tipos da esteira de prospecção. Espelham os DTOs de /api/v1/prospeccao. */

export type EstagioProspeccao =
  | "TRIAGEM"
  | "EM_ANALISE"
  | "APROVADO"
  | "DOCS_PENDENTES"
  | "DOCS_COMPLETOS"
  | "PRONTO_HABILITACAO"
  | "REPROVADO"
  | "REMOVIDO_RADAR";

export type MotivoRecusa =
  | "QUANTIDADE_DE_RESTRICOES"
  | "SEGMENTO"
  | "PEFIN_COM_FUNDO"
  | "PROCESSO_COM_FIDC"
  | "RECUPERACAO_JUDICIAL"
  | "DECISAO_DIRETORIA"
  | "OUTRO";

export type CanalContato = "EMAIL" | "WHATSAPP" | "LIGACAO" | "REUNIAO" | "SISTEMA";

export type StatusDocumento =
  | "PENDENTE"
  | "RECEBIDO"
  | "VALIDADO"
  | "REJEITADO"
  | "NAO_APLICAVEL"
  | "DISPENSADO";

export type EscopoDocumento = "EMPRESA" | "SOCIO";

export type TipoEvento =
  | "CRIACAO"
  | "TRANSICAO"
  | "COBRANCA"
  | "NOTA"
  | "DOC_RECEBIDO"
  | "DOC_VALIDADO"
  | "DOC_REJEITADO"
  | "DOC_DISPENSADO"
  | "ARQUIVO_ENVIADO"
  | "ARQUIVO_REMOVIDO"
  | "ANALISTA_ALTERADO"
  | "REABERTURA";

export type Prospeccao = {
  id: string;
  cnpj: string;
  razaoSocial: string;
  estagio: EstagioProspeccao;
  origem: "MANUAL" | "AUTOMATICA";
  comercialId: string | null;
  comercialNome: string | null;
  analistaId: string | null;
  analistaNome: string | null;
  estagioDesde: string;
  /** Prazo do estágio em horas úteis. Zero quer dizer estágio sem prazo. */
  prazoEstagioHoras: number;
  /** Horas úteis, calculadas no backend para as duas pontas não divergirem. */
  horasNoEstagio: number;
  slaEstourado: boolean;
  slaEmAtencao: boolean;
  /** 30 dias corridos sem retorno do cliente. */
  silencioEmAtencao: boolean;
  /** 45 dias: o time encaminha para inerte. */
  silencioProlongado: boolean;
  diasEmSilencio: number;
  motivoRecusa: MotivoRecusa | null;
  observacao: string | null;
  reaberturas: number;
  ultimoContatoEm: string | null;
  documentosResolvidos: number;
  documentosTotal: number;
  createdAt: string;
  closedAt: string | null;
};

export type ProspeccaoArquivo = {
  id: string;
  documentoId: string | null;
  nomeOriginal: string;
  mimeType: string | null;
  tamanhoBytes: number | null;
  versao: number;
  enviadoEm: string;
};

export type ProspeccaoDocumento = {
  id: string;
  codigo: string;
  nome: string;
  obrigatorio: boolean;
  /** Pergunta em vez de documento: aceita resposta e nunca trava o avanço. */
  informativo: boolean;
  /**
   * Obrigatório que o cliente pode legitimamente não ter — endividamento, curva ABC — ou não
   * aceitar assinar, como a autorização SCR. Libera com justificativa, em vez de ser ignorado.
   */
  admiteExcecao: boolean;
  escopo: EscopoDocumento;
  /** SOCIO ou AVALISTA. O avalista entrega os mesmos documentos, mas não é sócio. */
  pessoaPapel: "SOCIO" | "AVALISTA";
  socioNome: string | null;
  /** Participação no capital, quando conhecida. Só o Serasa informa. */
  socioParticipacao: number | null;
  socioDocumento: string | null;
  socioAtivo: boolean;
  socioInativoMotivo: string | null;
  status: StatusDocumento;
  observacao: string | null;
  motivo: string | null;
  recebidoEm: string | null;
  validadoEm: string | null;
  arquivos: ProspeccaoArquivo[];
};

export type ProspeccaoEvento = {
  id: string;
  tipo: TipoEvento;
  canal: CanalContato | null;
  estagioDe: EstagioProspeccao | null;
  estagioPara: EstagioProspeccao | null;
  texto: string | null;
  usuarioNome: string | null;
  criadoEm: string;
};

export type ProspeccaoDetalhe = {
  card: Prospeccao;
  documentos: ProspeccaoDocumento[];
  timeline: ProspeccaoEvento[];
  /** Destinos válidos vindos da máquina de estados do backend. */
  destinosPossiveis: EstagioProspeccao[];
};

export type ProspeccaoResumo = {
  porEstagio: Record<string, number>;
  total: number;
  slaEstourado: number;
  slaEmAtencao: number;
  silencioProlongado: number;
  /** Sem dupla contagem: atraso e silêncio costumam cair no mesmo card. */
  precisamAtencao: number;
};

export type DocumentoTipo = {
  id: string;
  codigo: string;
  nome: string;
  escopo: EscopoDocumento;
  obrigatorio: boolean;
  informativo: boolean;
  admiteExcecao: boolean;
  somenteUf: string | null;
  ativo: boolean;
  ordem: number;
};

/** Colunas do kanban, na ordem do fluxo. Terminais ficam fora do board. */
export const COLUNAS: EstagioProspeccao[] = [
  "TRIAGEM",
  "EM_ANALISE",
  "APROVADO",
  "DOCS_PENDENTES",
  "DOCS_COMPLETOS",
  "PRONTO_HABILITACAO",
];

export const ROTULO_ESTAGIO: Record<EstagioProspeccao, string> = {
  TRIAGEM: "Triagem",
  EM_ANALISE: "Em análise",
  APROVADO: "Aprovado",
  DOCS_PENDENTES: "Documentos pendentes",
  DOCS_COMPLETOS: "Documentos completos",
  PRONTO_HABILITACAO: "Pronto p/ habilitação",
  REPROVADO: "Reprovado",
  REMOVIDO_RADAR: "Inerte",
};

export const ROTULO_MOTIVO: Record<MotivoRecusa, string> = {
  QUANTIDADE_DE_RESTRICOES: "Quantidade de restrições",
  SEGMENTO: "Segmento",
  PEFIN_COM_FUNDO: "Pefin com fundo",
  PROCESSO_COM_FIDC: "Processo com FIDC",
  RECUPERACAO_JUDICIAL: "Recuperação judicial",
  DECISAO_DIRETORIA: "Decisão da diretoria",
  OUTRO: "Outro",
};

export const ROTULO_STATUS_DOC: Record<StatusDocumento, string> = {
  PENDENTE: "Pendente",
  RECEBIDO: "Recebido, a conferir",
  VALIDADO: "Validado",
  REJEITADO: "Rejeitado",
  NAO_APLICAVEL: "Não se aplica",
  DISPENSADO: "Liberado com justificativa",
};

export const ROTULO_CANAL: Record<CanalContato, string> = {
  EMAIL: "E-mail",
  WHATSAPP: "WhatsApp",
  LIGACAO: "Ligação",
  REUNIAO: "Reunião",
  SISTEMA: "Sistema",
};
