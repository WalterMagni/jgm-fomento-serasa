/** Esteira de liberação de operações. Espelha os DTOs de /api/v1/liberacao. */

export type EtapaLiberacao = "ORIGEM" | "COMITE" | "PENDENCIA" | "FINALIZADO";

/** Decisão de um sacado e resultado do card, que sai das decisões dos sacados. */
export type Resultado = "APROVADO" | "REPROVADO" | "PARCIAL";

export const ROTULO_RESULTADO: Record<Resultado, string> = {
  APROVADO: "Aprovado",
  REPROVADO: "Reprovado",
  PARCIAL: "Parcialmente aprovado",
};

/** Rótulo curto, para botões e chips apertados. */
export const ROTULO_RESULTADO_CURTO: Record<Resultado, string> = {
  APROVADO: "Aprovado",
  REPROVADO: "Reprovado",
  PARCIAL: "Parcial",
};

/** Texto livre: os padrões vêm de GET /liberacao/tipos junto com os criados pelo time. */
export type TipoOperacao = string;

export type PosicaoParecer = "FAVORAVEL" | "COM_RESSALVAS" | "DESFAVORAVEL";

export type CorLiberacao = "VERMELHO" | "LARANJA" | "AMARELO" | "VERDE" | "AZUL" | "ROXO" | "ROSA" | "CINZA";

export const CORES: CorLiberacao[] = ["VERMELHO", "LARANJA", "AMARELO", "VERDE", "AZUL", "ROXO", "ROSA", "CINZA"];

export type Etiqueta = { id: string; nome: string; cor: CorLiberacao };

export type TipoEventoLiberacao =
  | "CRIACAO"
  | "EDICAO"
  | "TRANSICAO"
  | "REABERTURA"
  | "PARECER"
  | "PENDENCIA_ABERTA"
  | "PENDENCIA_RESPONDIDA"
  | "ANEXO_ADICIONADO"
  | "ANEXO_REMOVIDO"
  | "EXCLUSAO";

export const ETAPAS: EtapaLiberacao[] = ["ORIGEM", "COMITE", "PENDENCIA", "FINALIZADO"];

export const ROTULO_ETAPA: Record<EtapaLiberacao, string> = {
  ORIGEM: "Origem da análise",
  COMITE: "Comitê",
  PENDENCIA: "Pendência",
  FINALIZADO: "Finalizados",
};

/** Rótulo curto para botões e abas de celular. */
export const ROTULO_ETAPA_CURTO: Record<EtapaLiberacao, string> = {
  ORIGEM: "Origem",
  COMITE: "Comitê",
  PENDENCIA: "Pendência",
  FINALIZADO: "Finalizados",
};

export const ROTULO_POSICAO: Record<PosicaoParecer, string> = {
  FAVORAVEL: "Favorável",
  COM_RESSALVAS: "Com ressalvas",
  DESFAVORAVEL: "Desfavorável",
};

export type Pessoa = { id: string; nome: string; iniciais: string };

export type PessoaDiretorio = Pessoa & { analista: boolean; comite: boolean };

export type Parecer = {
  id: string;
  rodada: number;
  usuarioId: string | null;
  usuarioNome: string;
  iniciais: string;
  posicao: PosicaoParecer | null;
  texto: string | null;
  registradoEm: string | null;
};

/** {@code aviso}: pode mover, mas a tela confirma antes (parecer faltando no Comitê). */
export type Destino = { etapa: EtapaLiberacao; permitido: boolean; motivo: string | null; aviso: string | null };

export type LiberacaoCard = {
  id: string;
  numero: number;
  etapa: EtapaLiberacao;
  etapaDesde: string;
  rodada: number;
  cedenteCnpj: string;
  cedenteNome: string;
  cedenteCadastrado: boolean;
  /** Cidade/UF do cedente. */
  cedentePraca: string | null;
  tipoOperacao: TipoOperacao | null;
  valor: number | null;
  prazo: string | null;
  parecerOrigem: string | null;
  posicaoOrigem: PosicaoParecer | null;
  /** Números da Análise de Risco (AR) importada do PDF; nulo em card digitado à mão. */
  proposta: PropostaAr | null;
  cor: CorLiberacao | null;
  etiquetas: Etiqueta[];
  criadoPorId: string | null;
  criadoPorNome: string;
  criadoEm: string;
  atualizadoPorNome: string;
  atualizadoEm: string;
  finalizadoEm: string | null;
  resultado: Resultado | null;
  /** Aprovados inteiros mais o aprovado dos parciais; nulo sem nenhuma decisão. */
  valorAprovado: number | null;
  ultimaAtividade: string | null;
  version: number;
  sacadosQtd: number;
  sacados: { documento: string; nome: string | null; praca: string | null }[];
  somaSacados: number | null;
  pareceres: Parecer[];
  pendenciasAbertas: number;
  comentarios: number;
  anexos: number;
  membros: Pessoa[];
  podeEditar: boolean;
  destinos: Destino[];
};

/** Decisão sobre o mesmo documento em outro card. */
export type DecisaoAnterior = {
  cardId: string;
  numero: number;
  cedenteNome: string;
  situacao: Resultado;
  valorAprovado: number | null;
  decididoPor: string | null;
  decididoEm: string | null;
};

export type Sacado = {
  documento: string;
  nome: string | null;
  valor: number | null;
  cadastrado: boolean;
  praca: string | null;
  situacao: Resultado | null;
  valorAprovado: number | null;
  situacaoPorNome: string | null;
  situacaoEm: string | null;
  historico: DecisaoAnterior[];
  /** Linha do sacado na AR, quando o card veio do PDF. */
  carteira: CarteiraSacado | null;
};

/**
 * Foto da Análise de Risco (AR) do sistema de operações no momento da importação. "Vincendos" é o
 * "a vencer" do relatório; percentuais como o relatório imprime (255,8 = 255,8%).
 */
export type PropostaAr = {
  clienteCodigo: string | null;
  emitidaEm: string | null;
  grupo: string | null;
  limiteIndividual: number | null;
  limiteGrupo: number | null;
  qtdLiberados: number | null;
  prazoMedio: number | null;
  faceLiberados: number | null;
  desconto: number | null;
  liquido: number | null;
  qtdTotal: number | null;
  valorTotal: number | null;
  liquidados: number | null;
  liquidadosEmAtraso: number | null;
  recomprados: number | null;
  vencidos: number | null;
  vincendos: number | null;
  emAberto: number | null;
  comprometimentoAtual: number | null;
  comprometimentoGrupoAtual: number | null;
  concentracaoAtual: number | null;
  comprometimentoApos: number | null;
  comprometimentoGrupoApos: number | null;
  concentracaoApos: number | null;
};

/** O que o sacado já tem com o cedente, segundo a AR. `titulos`: quantos entram nesta proposta. */
export type CarteiraSacado = {
  titulos: number | null;
  vencidos: number | null;
  vincendos: number | null;
  abertos: number | null;
  liquidados: number | null;
  recomprados: number | null;
};

export type SacadoImportado = { documento: string; nome: string | null; valor: number | null; carteira: CarteiraSacado | null };

/** Resultado da leitura do PDF. Nada foi gravado ainda. `cedenteCnpj` nulo = código sem vínculo. */
export type PropostaImportada = {
  cedenteCnpj: string | null;
  cedenteNome: string | null;
  /** O CNPJ tem página de empresa no portal. */
  cedenteCadastrado: boolean;
  clienteCodigo: string | null;
  clienteNome: string | null;
  proposta: PropostaAr;
  sacados: SacadoImportado[];
  semTitulo: SacadoImportado[];
  avisos: string[];
};

/** O que o portal sabe de um CNPJ (CNPJ Já ou Serasa). */
export type EmpresaConhecida = {
  documento: string;
  nome: string | null;
  praca: string | null;
  cadastrada: boolean;
  historico: DecisaoAnterior[];
};

/** Decisão de sacado enviada ao finalizar ou pela lista do card. */
export type DecisaoSacado = { documento: string; situacao: Resultado | null; valorAprovado?: number | null };

export type Pendencia = {
  id: string;
  abertaPorNome: string;
  destinatarioId: string | null;
  destinatarioNome: string;
  texto: string;
  resposta: string | null;
  abertaEm: string;
  respondidaEm: string | null;
  respondidaPorNome: string | null;
  podeResponder: boolean;
};

export type EventoLiberacao = {
  id: string;
  tipo: TipoEventoLiberacao;
  etapaDe: EtapaLiberacao | null;
  etapaPara: EtapaLiberacao | null;
  campo: string | null;
  valorAntes: string | null;
  valorDepois: string | null;
  texto: string | null;
  usuarioNome: string;
  criadoEm: string;
};

export type Comentario = {
  id: string;
  autorId: string | null;
  autorNome: string;
  iniciais: string;
  /** Com marcação de menção. */
  texto: string;
  criadoEm: string;
  editadoEm: string | null;
};

export type Anexo = {
  id: string;
  nome: string;
  mimeType: string | null;
  tamanhoBytes: number;
  enviadoPorNome: string;
  enviadoEm: string;
  podeRemover: boolean;
};

export type LiberacaoDetalhe = {
  card: LiberacaoCard;
  sacados: Sacado[];
  pareceres: Parecer[];
  pendencias: Pendencia[];
  eventos: EventoLiberacao[];
  comentarios: Comentario[];
  anexos: Anexo[];
  /** CNPJ citado em algum texto do card → tem página de empresa. */
  empresas: Record<string, boolean>;
};

export type LiberacaoResumo = {
  pareceresAguardando: number;
  pendenciasParaMim: number;
  /** Parecer seu + pendência para você. */
  total: number;
  /** Cards em aberto com prazo vencido, iguais para todo mundo. */
  atrasados: number;
  /** Atrasados e os seus, sem repetir card: o número vermelho do menu. */
  precisamAtencao: number;
};

/** Corpo de criação e edição. `version` só na edição. */
export type DadosCard = {
  cedenteCnpj: string;
  cedenteNome?: string | null;
  tipoOperacao: TipoOperacao | null;
  valor: number | null;
  prazo: string | null;
  parecerOrigem: string | null;
  posicaoOrigem: PosicaoParecer | null;
  sacados: { documento: string; nome?: string | null; valor?: number | null; carteira?: CarteiraSacado | null }[];
  /** Na edição, ausente mantém a AR que o card já tem. */
  proposta?: PropostaAr | null;
  version?: number;
  /** Só na tela: o PDF da AR, anexado ao card depois de salvar. Não vai no corpo da API. */
  arquivoProposta?: File | null;
};

export type NovaPendencia = { destinatarioId: string; texto: string };

export type EmpresaEncontrada = { cnpj: string; nome: string; fantasia: string | null; cidade: string | null; uf: string | null };

export type UsuarioAtual = {
  id: string;
  name: string;
  email: string;
  canManageUsers: boolean;
  analista: boolean;
  comite: boolean;
  somNotificacao: boolean;
  emailLiberacao: boolean;
};
