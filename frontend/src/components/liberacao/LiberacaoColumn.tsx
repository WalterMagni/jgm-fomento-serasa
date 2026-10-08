"use client";

import { useDroppable } from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import { ROTULO_ETAPA, type EtapaLiberacao, type LiberacaoCard } from "@/types/liberacao";
import LiberacaoCardItem from "./LiberacaoCardItem";
import { COR_ETAPA, ICONE_ETAPA, formatMoedaCurta } from "./formatters";

type Props = {
  etapa: EtapaLiberacao;
  cards: LiberacaoCard[];
  /**
   * Situação desta coluna para o card sendo arrastado: nulo sem arraste; senão se aceita e,
   * quando não, o motivo que o servidor mandou. `origem` marca a coluna de onde o card saiu,
   * que não aceita o drop mas também não deve apagar.
   */
  alvo: { permitido: boolean; motivo: string | null; origem: boolean } | null;
  onAbrir: (id: string) => void;
  onNovo?: () => void;
  /** No celular a coluna ocupa a largura toda. */
  larguraTotal?: boolean;
};

export default function LiberacaoColumn({ etapa, cards, alvo, onAbrir, onNovo, larguraTotal = false }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: etapa, disabled: !alvo?.permitido });
  const cor = COR_ETAPA[etapa];
  const soma = cards.reduce((total, card) => total + (card.valor ?? 0), 0);
  const ehOrigemDoArraste = alvo?.origem ?? false;

  return (
    <section
      ref={setNodeRef}
      aria-label={`${ROTULO_ETAPA[etapa]}: ${cards.length} card(s)`}
      className={`relative flex min-h-0 flex-col rounded-2xl border transition-[background-color,border-color,opacity,box-shadow] duration-200
        ${larguraTotal ? "w-full" : "min-w-[13.25rem] max-w-[22rem] flex-1 basis-0"}
        ${alvo && alvo.permitido && isOver
          ? `border-transparent bg-white/90 ring-2 ${cor.anel} shadow-lg dark:bg-slate-800/90`
          : alvo && alvo.permitido
            ? "border-dashed border-slate-300 bg-white/70 dark:border-slate-600 dark:bg-slate-800/50"
            : "border-slate-200/70 bg-slate-100/70 dark:border-slate-700/60 dark:bg-slate-900/50"}
        ${alvo && !alvo.permitido && !ehOrigemDoArraste ? "opacity-45" : ""}`}
    >
      <span className={`absolute inset-x-4 top-0 h-[3px] rounded-b-full ${cor.faixa}`} aria-hidden />

      <header className="px-3.5 pb-2 pt-3.5">
        <div className="flex items-center justify-between gap-2">
          <h2 className={`inline-flex min-h-6 items-center gap-1.5 font-sans text-[11px] font-bold uppercase tracking-[0.08em] ${cor.texto}`}>
            <Icon name={ICONE_ETAPA[etapa]} size={14} />
            {ROTULO_ETAPA[etapa]}
          </h2>
          {onNovo && (
            <button
              type="button"
              onClick={onNovo}
              aria-label="Novo card na Origem"
              title="Novo card"
              className="-my-1 inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-slate-500 transition-colors
                hover:bg-white hover:text-[#612035] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
                dark:hover:bg-slate-800 dark:hover:text-[#e8a3b6]"
            >
              <Icon name="add" size={16} />
            </button>
          )}
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="font-display text-2xl font-semibold leading-none text-slate-900 dark:text-white">{cards.length}</span>
          <span className="text-[11px] text-slate-500 dark:text-slate-400">
            {soma > 0 ? formatMoedaCurta(soma) : cards.length === 1 ? "card" : "cards"}
          </span>
        </div>
      </header>

      <div className="flex min-h-[6rem] flex-1 flex-col gap-2 overflow-y-auto px-2.5 pb-3">
        {cards.map(card => (
          <LiberacaoCardItem key={card.id} card={card} onAbrir={() => onAbrir(card.id)} />
        ))}

        {alvo && !alvo.permitido && !ehOrigemDoArraste && alvo.motivo && (
          <p className="mx-1 rounded-lg border border-dashed border-slate-300 bg-white/80 px-2.5 py-2 text-center text-[11px]
            text-slate-600 dark:border-slate-600 dark:bg-slate-800/80 dark:text-slate-300">
            {alvo.motivo}
          </p>
        )}

        {cards.length === 0 && !alvo && (
          <div className="flex flex-1 flex-col items-center justify-center gap-1 py-8 text-center text-[11px] text-slate-400">
            <Icon name={ICONE_ETAPA[etapa]} size={20} className="opacity-50" />
            {etapa === "ORIGEM" ? "Nenhuma análise aguardando" : "Vazio"}
          </div>
        )}
        {cards.length === 0 && alvo?.permitido && (
          <p className="flex flex-1 items-center justify-center py-8 text-[11px] font-medium text-slate-500">solte aqui</p>
        )}
      </div>
    </section>
  );
}
