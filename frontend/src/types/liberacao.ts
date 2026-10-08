/** Esteira de liberação de operações. Espelha os DTOs de /api/v1/liberacao. */

export type EtapaLiberacao = "ORIGEM" | "COMITE" | "PENDENCIA" | "APROVADO" | "REPROVADO";

export type TipoOperacao = "DUPLICATA" | "CHEQUE" | "COMISSARIA" | "INTERCOMPANY" | "OUTROS";

export type PosicaoParecer = "FAVORAVEL" | "COM_RESSALVAS" | "DESFAVORAVEL";

export type TipoEventoLiberacao =
  | "CRIACAO"
  | "EDICAO"
  | "TRANSICAO"
  | "REABERTURA"
  | "PARECER"
  | "PENDENCIA_ABERTA"
  | "PENDENCIA_RESPONDIDA"
  | "EXCLUSAO";

export const ETAPAS: EtapaLiberacao[] = ["ORIGEM", "COMITE", "PENDENCIA", "APROVADO", "REPROVADO"];

export const ROTULO_ETAPA: Record<EtapaLiberacao, string> = {
  ORIGEM: "Origem da análise",
  COMITE: "Comitê",
  PENDENCIA: "Pendência",
  APROVADO: "Aprovado",
  REPROVADO: "Reprovado",
};

/** Rótulo curto para botões e abas de celular. */
export const ROTULO_ETAPA_CURTO: Record<EtapaLiberacao, string> = {
  ORIGEM: "Origem",
  COMITE: "Comitê",
  PENDENCIA: "Pendência",
  APROVADO: "Aprovado",
  REPROVADO: "Reprovado",
};

export const TIPOS_OPERACAO: TipoOperacao[] = ["DUPLICATA", "CHEQUE", "COMISSARIA", "INTERCOMPANY", "OUTROS"];

export const ROTULO_TIPO: Record<TipoOperacao, string> = {
  DUPLICATA: "Duplicata",
  CHEQUE: "Cheque",
  COMISSARIA: "Comissária",
  INTERCOMPANY: "Intercompany",
  OUTROS: "Outros",
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

export type Destino = { etapa: EtapaLiberacao; permitido: boolean; motivo: string | null };

export type LiberacaoCard = {
  id: string;
  numero: number;
  etapa: EtapaLiberacao;
  etapaDesde: string;
  rodada: number;
  cedenteCnpj: string;
  cedenteNome: string;
  cedenteCadastrado: boolean;
  tipoOperacao: TipoOperacao | null;
  valor: number | null;
  prazo: string | null;
  parecerOrigem: string | null;
  criadoPorNome: string;
  criadoEm: string;
  atualizadoPorNome: string;
  atualizadoEm: string;
  finalizadoEm: string | null;
  version: number;
  sacadosQtd: number;
  somaSacados: number | null;
  pareceres: Parecer[];
  pendenciasAbertas: number;
  comentarios: number;
  membros: Pessoa[];
  podeEditar: boolean;
  destinos: Destino[];
};

export type Sacado = { documento: string; nome: string | null; valor: number | null; cadastrado: boolean };

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

export type LiberacaoDetalhe = {
  card: LiberacaoCard;
  sacados: Sacado[];
  pareceres: Parecer[];
  pendencias: Pendencia[];
  eventos: EventoLiberacao[];
  comentarios: Comentario[];
  /** CNPJ citado em algum texto do card → tem página de empresa. */
  empresas: Record<string, boolean>;
};

export type LiberacaoResumo = { pareceresAguardando: number; pendenciasParaMim: number; total: number };

/** Corpo de criação e edição. `version` só na edição. */
export type DadosCard = {
  cedenteCnpj: string;
  cedenteNome?: string | null;
  tipoOperacao: TipoOperacao | null;
  valor: number | null;
  prazo: string | null;
  parecerOrigem: string | null;
  sacados: { documento: string; nome?: string | null; valor?: number | null }[];
  version?: number;
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
};
