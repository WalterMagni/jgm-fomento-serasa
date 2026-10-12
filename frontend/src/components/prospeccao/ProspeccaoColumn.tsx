"use client";

import { useDroppable } from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import { ROTULO_ESTAGIO, type EstagioProspeccao, type Prospeccao } from "@/types/prospeccao";
import { COR_ESTAGIO } from "./estilo";
import { ICONE_ESTAGIO, ICONE_ORDENACAO, PROXIMA_ORDENACAO, ROTULO_ORDENACAO, formatMoedaCurta, type Ordenacao } from "./formatters";
import ProspeccaoCard from "./ProspeccaoCard";

type Props = {
  estagio: EstagioProspeccao;
  cards: Prospeccao[];
  /**
   * Situação desta coluna para o card sendo arrastado: nulo sem arraste; `origem` marca a coluna
   * de onde ele saiu, que não aceita o drop mas também não deve apagar.
   */
  alvo: { permitido: boolean; origem: boolean } | null;
  onAbrir: (id: string) => void;
  ordenacao: Ordenacao;
  onOrdenar: (ordenacao: Ordenacao) => void;
  selecionando: boolean;
  selecionados: Set<string>;
  onAlternarSelecao: (id: string) => void;
  /** Marca ou desmarca de uma vez os cards visíveis da coluna. */
  onSelecionarColuna: (ids: string[], marcar: boolean) => void;
  onNovo?: () => void;
  /** No celular a coluna ocupa a largura toda. */
  larguraTotal?: boolean;
};

export default function ProspeccaoColumn({
  estagio,
  cards,
  alvo,
  onAbrir,
  ordenacao,
  onOrdenar,
  selecionando,
  selecionados,
  onAlternarSelecao,
  onSelecionarColuna,
  onNovo,
  larguraTotal = false,
}: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: estagio, disabled: !alvo?.permitido });
  const todosMarcados = cards.length > 0 && cards.every(card => selecionados.has(card.id));
  const ehOrigem = alvo?.origem ?? false;
  const cor = COR_ESTAGIO[estagio];
  const somaLimites = cards.reduce((total, card) => total + (card.limiteVigente ?? 0), 0);
  const atrasados = cards.filter(card => card.slaEstourado || card.silencioProlongado).length;

  return (
    <section
      ref={setNodeRef}
      aria-label={`${ROTULO_ESTAGIO[estagio]}: ${cards.length} card(s)`}
      className={`relative flex min-h-0 flex-col rounded-2xl border transition-[background-color,border-color,opacity,box-shadow] duration-200
        ${larguraTotal ? "w-full" : "min-w-[12.5rem] max-w-[22rem] flex-1 basis-0"}
        ${alvo?.permitido && isOver
          ? `border-transparent bg-white/90 shadow-lg ring-2 ${cor.anel} dark:bg-slate-800/90`
          : alvo?.permitido
            ? "border-dashed border-slate-300 bg-white/70 dark:border-slate-600 dark:bg-slate-800/50"
            : "border-slate-200/70 bg-slate-100/70 dark:border-slate-700/60 dark:bg-slate-900/50"}
        ${alvo && !alvo.permitido && !ehOrigem ? "opacity-45" : ""}`}
    >
      <span className={`absolute inset-x-4 top-0 h-[3px] rounded-b-full ${cor.faixa}`} aria-hidden />

      <header className="px-3.5 pb-2 pt-3.5">
        <div className="flex items-center justify-between gap-2">
          <h2 className={`inline-flex min-w-0 items-center gap-1.5 text-[11px] font-bold uppercase tracking-[0.08em] ${cor.texto}`}>
            <Icon name={ICONE_ESTAGIO[estagio]} size={14} />
            <span className="truncate" title={ROTULO_ESTAGIO[estagio]}>{ROTULO_ESTAGIO[estagio]}</span>
          </h2>
          <div className="-my-1 flex shrink-0 items-center">
            {selecionando && cards.length > 0 && (
              <button
                type="button"
                onClick={() => onSelecionarColuna(cards.map(card => card.id), !todosMarcados)}
                title={todosMarcados ? "Desmarcar todos da coluna" : "Marcar todos da coluna"}
                aria-label={`${todosMarcados ? "Desmarcar" : "Marcar"} todos de ${ROTULO_ESTAGIO[estagio]}`}
                className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-slate-500 transition-colors
                  hover:bg-white focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] dark:hover:bg-slate-800"
              >
                <Icon name={todosMarcados ? "check_circle" : "done_all"} size={15} />
              </button>
            )}
            <button
              type="button"
              onClick={() => onOrdenar(PROXIMA_ORDENACAO[ordenacao])}
              title={ROTULO_ORDENACAO[ordenacao]}
              aria-label={`Ordenar ${ROTULO_ESTAGIO[estagio]}. Atual: ${ROTULO_ORDENACAO[ordenacao]}`}
              className={`inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg transition-colors focus:outline-none
                focus-visible:ring-2 focus-visible:ring-[#612035] ${
                  ordenacao === "urgencia"
                    ? "text-slate-400 hover:bg-white hover:text-slate-600 dark:hover:bg-slate-800"
                    : "bg-white text-[#612035] dark:bg-slate-800 dark:text-[#D1732C]"
                }`}
            >
              <Icon name={ICONE_ORDENACAO[ordenacao]} size={14} />
            </button>
            {onNovo && (
              <button
                type="button"
                onClick={onNovo}
                aria-label="Nova prospecção"
                title="Nova prospecção"
                className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-lg text-slate-500 transition-colors
                  hover:bg-white hover:text-[#612035] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
                  dark:hover:bg-slate-800 dark:hover:text-[#e8a3b6]"
              >
                <Icon name="add" size={16} />
              </button>
            )}
          </div>
        </div>
        <div className="mt-1 flex items-baseline gap-2">
          <span className="font-display text-2xl font-semibold leading-none text-slate-900 dark:text-white">{cards.length}</span>
          <span className="truncate text-[11px] text-slate-500 dark:text-slate-400">
            {somaLimites > 0 ? `${formatMoedaCurta(somaLimites)} em limites` : cards.length === 1 ? "card" : "cards"}
          </span>
          {atrasados > 0 && (
            <span className="ml-auto shrink-0 rounded-full bg-rose-100 px-1.5 text-[10px] font-semibold text-rose-700 dark:bg-rose-900/40 dark:text-rose-200"
              title="Atrasados ou sem resposta do cliente">
              {atrasados}
            </span>
          )}
        </div>
      </header>

      <div className="flex min-h-[6rem] flex-1 flex-col gap-2 overflow-y-auto px-2.5 pb-3">
        {cards.map(card => (
          <ProspeccaoCard
            key={card.id}
            card={card}
            onAbrir={() => onAbrir(card.id)}
            selecionando={selecionando}
            selecionado={selecionados.has(card.id)}
            onAlternarSelecao={() => onAlternarSelecao(card.id)}
          />
        ))}
        {cards.length === 0 && (
          <p className="flex flex-1 flex-col items-center justify-center gap-1 py-8 text-center text-[11px] text-slate-400">
            {alvo?.permitido ? (
              <span className="font-medium text-slate-500">solte aqui</span>
            ) : (
              <>
                <Icon name={ICONE_ESTAGIO[estagio]} size={20} className="opacity-50" />
                vazio
              </>
            )}
          </p>
        )}
      </div>
    </section>
  );
}
