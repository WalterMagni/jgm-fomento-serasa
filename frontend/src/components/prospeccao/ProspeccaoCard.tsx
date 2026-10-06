"use client";

import Icon from "@/components/ui/Icon";
import { Prospeccao, ROTULO_ESTAGIO } from "@/types/prospeccao";
import { CLASSE_BADGE_SEMAFORO, CLASSE_SEMAFORO, formatCnpj, semaforoDe, textoPrazo } from "./formatters";

type Props = {
  card: Prospeccao;
  onAbrir: () => void;
  /** Início do arraste. O drop é tratado pela coluna. */
  onDragStart: () => void;
  onDragEnd: () => void;
  arrastando: boolean;
  /** Fallback de teclado e de quem não quer arrastar: mover por menu. */
  onMover: () => void;
  /** Modo seleção: clicar marca o card em vez de abrir, e arrastar fica desligado. */
  selecionando?: boolean;
  selecionado?: boolean;
  onAlternarSelecao?: () => void;
};

export default function ProspeccaoCard({
  card,
  onAbrir,
  onDragStart,
  onDragEnd,
  arrastando,
  onMover,
  selecionando = false,
  selecionado = false,
  onAlternarSelecao,
}: Props) {
  const semaforo = semaforoDe(card);
  const progresso = card.documentosTotal > 0 ? `${card.documentosResolvidos}/${card.documentosTotal}` : null;

  return (
    <article
      draggable={!selecionando}
      onDragStart={event => {
        event.dataTransfer.setData("text/plain", card.id);
        event.dataTransfer.effectAllowed = "move";
        onDragStart();
      }}
      onDragEnd={onDragEnd}
      // Hover escurece o fundo e adensa a sombra. Sem transform: escala deslocaria os cards
      // vizinhos a cada passada de mouse, e a coluna inteira tremeria.
      className={`esteira-card-in group cursor-grab rounded-lg border border-l-4 bg-white p-3 text-left
        shadow-sm transition-[background-color,box-shadow,border-color] duration-200 ease-out
        hover:bg-slate-50 hover:shadow-md active:cursor-grabbing
        dark:bg-slate-800 dark:hover:bg-slate-700/70
        ${CLASSE_SEMAFORO[semaforo]} ${arrastando ? "opacity-40" : ""}
        ${selecionando ? "cursor-pointer" : ""} ${selecionado ? "ring-2 ring-[#612035] dark:ring-[#D1732C]" : ""}`}
    >
      <button
        type="button"
        onClick={selecionando ? onAlternarSelecao : onAbrir}
        aria-label={selecionando ? `Selecionar ${card.razaoSocial}` : `Abrir ${card.razaoSocial}`}
        aria-pressed={selecionando ? selecionado : undefined}
        className="w-full cursor-pointer rounded text-left focus:outline-none focus-visible:ring-2
          focus-visible:ring-[#612035] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-800"
      >
        <h3 className="flex items-start gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
          {selecionando && (
            <Icon
              name={selecionado ? "check_box" : "check_box_outline_blank"}
              className={`mt-px shrink-0 text-[18px] ${selecionado ? "text-[#612035] dark:text-[#D1732C]" : "text-slate-400"}`}
            />
          )}
          <span className="line-clamp-2">{card.razaoSocial}</span>
        </h3>
        <p className="mt-0.5 font-mono text-[11px] text-slate-500 dark:text-slate-400">{formatCnpj(card.cnpj)}</p>

        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
          <span className={`inline-flex items-center gap-1 ${CLASSE_BADGE_SEMAFORO[semaforo]}`}>
            <Icon name={semaforo === "ok" ? "schedule" : "warning"} className="text-[13px]" />
            {textoPrazo(card)}
          </span>
          {progresso && (
            <span className="inline-flex items-center gap-1 text-slate-500 dark:text-slate-400">
              <Icon name="checklist" className="text-[13px]" />
              {progresso} docs
            </span>
          )}
        </div>

        {card.silencioProlongado && (
          <p className="mt-1.5 inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-[11px] text-red-700 dark:bg-red-950/40 dark:text-red-300">
            <Icon name="notifications_off" className="text-[13px]" />
            {card.diasEmSilencio} dias sem retorno · encaminhar para inerte
          </p>
        )}

        <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-slate-500 dark:text-slate-400">
          <span className="truncate">
            {card.analistaNome ?? card.comercialNome ?? "sem responsável"}
          </span>
          {card.reaberturas > 0 && (
            <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 dark:bg-slate-700" title="Já passou pela esteira antes">
              {card.reaberturas}ª reanálise
            </span>
          )}
        </div>
      </button>

      <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-2 dark:border-slate-700">
        <span className="text-[10px] uppercase tracking-wide text-slate-400">
          {card.origem === "AUTOMATICA" ? "visão cedente" : "manual"}
        </span>
        {!selecionando && (
          <button
            type="button"
            onClick={onMover}
            className="cursor-pointer rounded px-1.5 py-0.5 text-[11px] text-slate-500 transition-colors
              hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2
              focus-visible:ring-[#612035] dark:text-slate-400 dark:hover:bg-slate-700"
            title={`Mover de ${ROTULO_ESTAGIO[card.estagio]}`}
          >
            Mover
          </button>
        )}
      </div>
    </article>
  );
}
