import type { TipoNotificacao } from "@/types/notificacao";

/** Ícone e cor de cada tipo de notificação — no sino e no aviso de rajada. */
export const VISUAL: Record<TipoNotificacao, { icone: string; cor: string }> = {
  CARD_CRIADO: { icone: "inbox", cor: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-200" },
  PARECER_ESPERADO: { icone: "gavel", cor: "bg-[#612035]/10 text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]" },
  PARECER_REGISTRADO: { icone: "gavel", cor: "bg-[#612035]/10 text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]" },
  COMITE_COMPLETO: { icone: "check_circle", cor: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
  PENDENCIA_ABERTA: { icone: "hourglass_top", cor: "bg-[#D1732C]/15 text-[#a8551a] dark:text-[#f0a46b]" },
  PENDENCIA_RESPONDIDA: { icone: "reply", cor: "bg-[#D1732C]/15 text-[#a8551a] dark:text-[#f0a46b]" },
  MENCAO: { icone: "alternate_email", cor: "bg-[#2956E0]/10 text-[#2956E0] dark:bg-[#2956E0]/30 dark:text-[#b8caff]" },
  COMENTARIO: { icone: "chat", cor: "bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-200" },
  DECISAO: { icone: "fact_check", cor: "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200" },
  RESPOSTA_CLIENTE: { icone: "handshake", cor: "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300" },
  DOCUMENTOS_COMPLETOS: { icone: "folder_open", cor: "bg-teal-50 text-teal-700 dark:bg-teal-900/40 dark:text-teal-300" },
  // Verde da Judit (judit.io), o mesmo do card de processos.
  JUDIT_PROCESSO_PRONTO: { icone: "gavel", cor: "bg-[#15BE62]/15 text-[#047857] dark:bg-[#15BE62]/25 dark:text-[#15BE62]" },
  JUDIT_PROCESSO_FALHOU: { icone: "warning", cor: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300" },
  JUDIT_CONSULTA_PRONTA: { icone: "balance", cor: "bg-[#15BE62]/15 text-[#047857] dark:bg-[#15BE62]/25 dark:text-[#15BE62]" },
  JUDIT_CONSULTA_FALHOU: { icone: "warning", cor: "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300" },
};
