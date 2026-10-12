"use client";

import { useDraggable } from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import type { Prospeccao } from "@/types/prospeccao";
import { CLASSE_BADGE_SEMAFORO, CLASSE_SEMAFORO, formatCnpj, formatMoedaCurta, semaforoDe, textoPrazo } from "./formatters";

/** O que o card mostra, sem o comportamento de arraste — reaproveitado na cópia que segue o ponteiro. */
export function CardFace({
  card,
  sobreposicao = false,
  selecionando = false,
  selecionado = false,
}: {
  card: Prospeccao;
  sobreposicao?: boolean;
  selecionando?: boolean;
  selecionado?: boolean;
}) {
  const semaforo = semaforoDe(card);
  const progresso = card.documentosTotal > 0 ? `${card.documentosResolvidos}/${card.documentosTotal}` : null;

  return (
    <div
      className={`rounded-xl border border-l-4 bg-white p-3 text-left dark:bg-slate-800 ${CLASSE_SEMAFORO[semaforo]}
        ${sobreposicao ? "rotate-[1.5deg] shadow-2xl ring-1 ring-black/5" : "shadow-[0_1px_2px_rgba(15,23,42,0.06)]"}
        ${selecionado ? "ring-2 ring-[#612035] dark:ring-[#D1732C]" : ""}`}
    >
      <h3 className="flex items-start gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
        {selecionando && (
          <Icon
            name={selecionado ? "check_circle" : "add_circle"}
            size={16}
            className={`mt-px shrink-0 ${selecionado ? "text-[#612035] dark:text-[#D1732C]" : "text-slate-300"}`}
          />
        )}
        <span className="line-clamp-2">{card.razaoSocial}</span>
      </h3>
      <p className="mt-0.5 font-mono text-[11px] text-slate-500 dark:text-slate-400">{formatCnpj(card.cnpj)}</p>

      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
        <span className={`inline-flex items-center gap-1 ${CLASSE_BADGE_SEMAFORO[semaforo]}`}>
          <Icon name={semaforo === "ok" ? "schedule" : "warning"} size={13} />
          {textoPrazo(card)}
        </span>
        {card.pendenciasAbertas > 0 && (
          <span className="inline-flex items-center gap-1 rounded bg-[#D1732C]/15 px-1.5 py-px font-medium text-[#a8551a] dark:text-[#f0a46b]"
            title="Pendência aberta: o prazo está parado até o comercial responder">
            <Icon name="hourglass_top" size={12} />
            aguardando comercial
          </span>
        )}
        {card.limiteVigente != null && (
          <span className="inline-flex items-center gap-1 font-medium text-emerald-700 dark:text-emerald-300" title="Limite da decisão vigente">
            <Icon name="account_balance_wallet" size={13} />
            {formatMoedaCurta(card.limiteVigente)}
          </span>
        )}
        {progresso && (
          <span className="inline-flex items-center gap-1 text-slate-500 dark:text-slate-400" title="Documentos resolvidos">
            <Icon name="checklist" size={13} />
            {progresso}
          </span>
        )}
      </div>

      <div className="mt-2 flex items-center justify-between gap-2 border-t border-slate-100 pt-2 text-[11px] text-slate-500 dark:border-slate-700 dark:text-slate-400">
        <span className="min-w-0 truncate" title={`Comercial: ${card.comercialNome ?? "direto"} · Analista: ${card.analistaNome ?? "—"}`}>
          {card.analistaNome ?? <span className="italic">ninguém assumiu</span>}
          {card.comercialNome && <span className="text-slate-400"> · {card.comercialNome}</span>}
        </span>
        <span className="flex shrink-0 items-center gap-1">
          {card.legado && (
            <span className="rounded bg-amber-100 px-1.5 py-px text-[10px] font-semibold uppercase text-amber-800 dark:bg-amber-900/40 dark:text-amber-200"
              title="Migrado da esteira antiga sem decisão registrada">
              legado
            </span>
          )}
          {card.reaberturas > 0 && (
            <span className="rounded bg-slate-100 px-1.5 py-px dark:bg-slate-700" title="Já passou pela esteira antes">
              {card.reaberturas}ª
            </span>
          )}
          {card.origem === "AUTOMATICA" && (
            <span className="rounded bg-[#2956E0]/10 px-1.5 py-px text-[10px] font-medium text-[#2956E0] dark:text-sky-300" title="Entrou por visão cedente SIM">
              VC
            </span>
          )}
        </span>
      </div>
    </div>
  );
}

type Props = {
  card: Prospeccao;
  onAbrir: () => void;
  /** Modo seleção: clicar marca o card em vez de abrir, e arrastar fica desligado. */
  selecionando?: boolean;
  selecionado?: boolean;
  onAlternarSelecao?: () => void;
};

/**
 * Card arrastável do quadro.
 *
 * <p>Arraste pelo dnd-kit, como na liberação: o arraste nativo do HTML não funciona com toque.
 * Clique abre o card; Espaço pega e solta pelo teclado; Enter abre.</p>
 */
export default function ProspeccaoCard({ card, onAbrir, selecionando = false, selecionado = false, onAlternarSelecao }: Props) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.id,
    data: { card },
    disabled: selecionando,
  });

  return (
    <button
      ref={setNodeRef}
      type="button"
      {...attributes}
      {...listeners}
      onClick={selecionando ? onAlternarSelecao : onAbrir}
      aria-label={selecionando ? `Selecionar ${card.razaoSocial}` : `Abrir ${card.razaoSocial}`}
      aria-pressed={selecionando ? selecionado : undefined}
      className={`esteira-card-in block w-full rounded-xl text-left transition-[opacity,box-shadow] duration-200 hover:shadow-md
        focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900
        ${selecionando ? "cursor-pointer" : "cursor-grab active:cursor-grabbing"} ${isDragging ? "opacity-30" : ""}`}
    >
      <CardFace card={card} selecionando={selecionando} selecionado={selecionado} />
    </button>
  );
}
