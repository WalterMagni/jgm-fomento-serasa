/** Tipos da esteira de prospecção. Espelham os DTOs de /api/v1/prospeccao. */

export type EstagioProspeccao =
  | "NOVA"
  | "EM_ANALISE"
  | "AGUARDANDO_ACEITE"
  | "DOCUMENTACAO"
  | "COMITE"
  | "CONTRATO"
  | "HABILITADO"
  | "REPROVADO"
  | "RECUSADO_CLIENTE"
  | "INERTE";

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
  | "REABERTURA"
  | "DADOS_COMERCIAIS"
  | "DECISAO"
  | "ACEITE"
  | "PENDENCIA_ABERTA"
  | "PENDENCIA_RESPONDIDA"
  | "PENDENCIA_CANCELADA";

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
  /** Migrado da esteira antiga sem decisão registrada: o time revisa e completa. */
  legado: boolean;
  /** Limite da decisão vigente, quando aprovada. */
  limiteVigente: number | null;
  /** Pendências abertas: o card espera o comercial e o prazo está parado. */
  pendenciasAbertas: number;
  version: number;
};

export type ResultadoDecisao = "APROVADO" | "REPROVADO";
export type RespostaCliente = "ACEITO" | "RECUSADO" | "CONTRAPROPOSTA";

export type Condicao = { produto: string; valor: number };

export type Decisao = {
  id: string;
  versao: number;
  resultado: ResultadoDecisao;
  limite: number | null;
  condicoes: Condicao[];
  parecer: string | null;
  motivoRecusa: MotivoRecusa | null;
  vigente: boolean;
  decididoPorNome: string | null;
  decididoEm: string;
};

export type Aceite = {
  id: string;
  decisaoId: string;
  resposta: RespostaCliente;
  limiteSugerido: number | null;
  condicoesSugeridas: Condicao[];
  mensagem: string | null;
  registradoPorNome: string | null;
  emNomeDoComercial: boolean;
  registradoEm: string;
};

export type StatusPendencia = "ABERTA" | "RESPONDIDA" | "CANCELADA";

export type Pendencia = {
  id: string;
  pergunta: string;
  /** Nomes dos campos de DadosComerciais exigidos. */
  camposExigidos: (keyof DadosComerciais)[];
  documentosPedidos: string[];
  status: StatusPendencia;
  abertaPorNome: string | null;
  abertaEm: string;
  resposta: string | null;
  respondidaPorNome: string | null;
  emNomeDoComercial: boolean;
  fechadaEm: string | null;
};

export type ComentarioProspeccao = {
  id: string;
  autorId: string | null;
  autorNome: string;
  texto: string;
  criadoEm: string;
  editadoEm: string | null;
};

/** Rótulo de cada campo comercial — usado na pendência para dizer o que falta. */
export const ROTULO_CAMPO_COMERCIAL: Record<keyof DadosComerciais, string> = {
  faturamentoMensal: "Faturamento mensal",
  ticketMedio: "Ticket médio",
  valorMedioOperacoes: "Valor médio das operações",
  prazoMedioVendas: "Prazo médio de vendas",
  prazoMedioPgtoFornecedor: "Prazo de pagamento a fornecedor",
  prazoMedioEntrega: "Prazo médio de entrega",
  possuiCanhotosDias: "Guarda canhotos há",
  operaMercadoriaEntregue: "Opera mercadoria entregue",
  operaMercadoriaEmbarcada: "Opera mercadoria embarcada",
  regiaoAtendimento: "Região de atendimento",
  produtos: "Produtos",
  parceirosAtuais: "Parceiros atuais",
  outros: "Outros",
};

export type ProdutoCatalogo = { id: string; nome: string; ordem: number; ativo: boolean };

export const ROTULO_RESPOSTA: Record<RespostaCliente, string> = {
  ACEITO: "Aceitou",
  RECUSADO: "Recusou",
  CONTRAPROPOSTA: "Contraproposta",
};

/** Perfil comercial da empresa. Todos opcionais; valores em reais, prazos em dias. */
export type DadosComerciais = {
  faturamentoMensal: number | null;
  ticketMedio: number | null;
  valorMedioOperacoes: number | null;
  prazoMedioVendas: number | null;
  prazoMedioPgtoFornecedor: number | null;
  prazoMedioEntrega: number | null;
  possuiCanhotosDias: number | null;
  operaMercadoriaEntregue: boolean | null;
  operaMercadoriaEmbarcada: boolean | null;
  regiaoAtendimento: string | null;
  produtos: string | null;
  parceirosAtuais: string | null;
  outros: string | null;
};

export const DADOS_COMERCIAIS_VAZIOS: DadosComerciais = {
  faturamentoMensal: null,
  ticketMedio: null,
  valorMedioOperacoes: null,
  prazoMedioVendas: null,
  prazoMedioPgtoFornecedor: null,
  prazoMedioEntrega: null,
  possuiCanhotosDias: null,
  operaMercadoriaEntregue: null,
  operaMercadoriaEmbarcada: null,
  regiaoAtendimento: null,
  produtos: null,
  parceirosAtuais: null,
  outros: null,
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
  dadosComerciais: DadosComerciais;
  documentos: ProspeccaoDocumento[];
  timeline: ProspeccaoEvento[];
  /** Destinos válidos vindos da máquina de estados do backend. */
  destinosPossiveis: EstagioProspeccao[];
  /** Da mais nova para a mais antiga; a vigente vem marcada. */
  decisoes: Decisao[];
  aceites: Aceite[];
  pendencias: Pendencia[];
  comentarios: ComentarioProspeccao[];
  /** Quem olha é a analista dona ou admin — mesma regra que autoriza no servidor. */
  podeDecidir: boolean;
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
  "NOVA",
  "EM_ANALISE",
  "AGUARDANDO_ACEITE",
  "DOCUMENTACAO",
  "COMITE",
  "CONTRATO",
];

/** Desfechos: ficam na seção recolhida abaixo do quadro. */
export const TERMINAIS: EstagioProspeccao[] = ["HABILITADO", "REPROVADO", "RECUSADO_CLIENTE", "INERTE"];

export function ehTerminal(estagio: EstagioProspeccao) {
  return TERMINAIS.includes(estagio);
}

export const ROTULO_ESTAGIO: Record<EstagioProspeccao, string> = {
  NOVA: "Nova",
  EM_ANALISE: "Em análise",
  AGUARDANDO_ACEITE: "Aguardando aceite",
  DOCUMENTACAO: "Documentação",
  COMITE: "Comitê",
  CONTRATO: "Contrato",
  HABILITADO: "Habilitado",
  REPROVADO: "Reprovado",
  RECUSADO_CLIENTE: "Recusado pelo cliente",
  INERTE: "Inerte",
};

/** Verbo do botão que leva o card a cada estágio, na barra de ações do detalhe. */
export const ACAO_PARA_ESTAGIO: Record<EstagioProspeccao, string> = {
  NOVA: "Devolver para Nova",
  EM_ANALISE: "Voltar para análise",
  AGUARDANDO_ACEITE: "Aprovar e aguardar aceite",
  DOCUMENTACAO: "Cliente aceitou",
  COMITE: "Enviar ao comitê",
  CONTRATO: "Aprovado no comitê",
  HABILITADO: "Concluir: habilitado",
  REPROVADO: "Reprovar",
  RECUSADO_CLIENTE: "Cliente recusou",
  INERTE: "Mover para inerte",
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
