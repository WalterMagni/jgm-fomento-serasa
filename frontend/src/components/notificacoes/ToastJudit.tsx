"use client";

import Icon from "@/components/ui/Icon";
import { SelosJudit } from "@/components/judit/JuditPecas";
import type { Notificacao } from "@/types/notificacao";

/** Aviso que vem da Judit — detalhe de processo pronto ou falho. As outras notificações usam o toast padrão. */
export function ehDaJudit(notificacao: Pick<Notificacao, "tipo">) {
  return notificacao.tipo === "JUDIT_PROCESSO_PRONTO" || notificacao.tipo === "JUDIT_PROCESSO_FALHOU";
}

/**
 * Toast da Judit: borda e faixa no verde do logo (judit.io), como o card de processos, para o aviso
 * se distinguir dos da esteira. Fica mais tempo na tela: a pessoa pediu e saiu fazendo outra coisa.
 */
export default function ToastJudit({
  notificacao,
  onAbrir,
  onFechar,
}: {
  notificacao: Notificacao;
  onAbrir: () => void;
  onFechar: () => void;
}) {
  const falhou = notificacao.tipo === "JUDIT_PROCESSO_FALHOU";
  return (
    <div
      role="status"
      className="w-[min(380px,calc(100vw-32px))] overflow-hidden rounded-xl border border-[#15BE62]/60 bg-white shadow-[0_18px_40px_-18px_rgba(21,190,98,0.6)] dark:bg-[#1a1c23]"
    >
      <div className={`h-1 ${falhou ? "bg-amber-400" : "bg-[#15BE62]"}`} aria-hidden />
      <div className="flex gap-3 p-4">
        <span
          className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${
            falhou ? "bg-amber-100 text-amber-700" : "bg-[#15BE62]/15 text-[#047857] dark:text-[#15BE62]"
          }`}
        >
          <Icon name={falhou ? "warning" : "gavel"} size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <SelosJudit />
          </div>
          <p className="mt-1.5 text-sm font-semibold leading-snug text-grafite dark:text-white">{notificacao.titulo}</p>
          {notificacao.resumo && (
            <p className="mt-0.5 line-clamp-3 text-xs leading-snug text-gray-500 dark:text-gray-400">{notificacao.resumo}</p>
          )}
          <div className="mt-3 flex items-center gap-2">
            <button
              type="button"
              onClick={onAbrir}
              className="inline-flex h-8 items-center gap-1.5 rounded-lg bg-[#15BE62] px-3 text-xs font-semibold text-[#05301A] transition-colors hover:bg-[#13AB58]"
            >
              <Icon name="open_in_new" size={13} /> Abrir processo
            </button>
            <button
              type="button"
              onClick={onFechar}
              className="h-8 rounded-lg px-2 text-xs font-semibold text-gray-500 hover:text-grafite dark:hover:text-white"
            >
              Fechar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
