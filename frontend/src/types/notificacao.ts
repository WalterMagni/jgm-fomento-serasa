export type TipoNotificacao =
  | "CARD_CRIADO"
  | "PARECER_ESPERADO"
  | "PARECER_REGISTRADO"
  | "COMITE_COMPLETO"
  | "PENDENCIA_ABERTA"
  | "PENDENCIA_RESPONDIDA"
  | "MENCAO"
  | "COMENTARIO"
  | "DECISAO"
  | "RESPOSTA_CLIENTE"
  | "DOCUMENTOS_COMPLETOS"
  | "JUDIT_PROCESSO_PRONTO"
  | "JUDIT_PROCESSO_FALHOU"
  | "JUDIT_CONSULTA_PRONTA"
  | "JUDIT_CONSULTA_FALHOU";

export type Notificacao = {
  id: string;
  tipo: TipoNotificacao;
  titulo: string;
  resumo: string | null;
  /** Caminho dentro do portal, ex. /liberacao?card=<id>. */
  link: string;
  atorNome: string | null;
  criadaEm: string;
  lidaEm: string | null;
};

export type ListagemNotificacoes = { naoLidas: number; itens: Notificacao[] };

/** O que chega pelo canal quando nasce uma notificação. */
export type EntregaNotificacao = { notificacao: Notificacao; naoLidas: number };
