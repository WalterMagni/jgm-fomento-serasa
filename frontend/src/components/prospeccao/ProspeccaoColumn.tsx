"use client";

import Icon from "@/components/ui/Icon";
import { EstagioProspeccao, Prospeccao, ROTULO_ESTAGIO } from "@/types/prospeccao";
import { ICONE_ESTAGIO } from "./formatters";
import ProspeccaoCard from "./ProspeccaoCard";

type Props = {
  estagio: EstagioProspeccao;
  cards: Prospeccao[];
  cardArrastando: string | null;
  /** Nulo quando o card arrastado não pode ir para esta coluna, segundo a máquina de estados. */
  aceitaDrop: boolean;
  onDragStart: (id: string) => void;
  onDragEnd: () => void;
  onDrop: (id: string, destino: EstagioProspeccao) => void;
  onAbrir: (id: string) => void;
  onMover: (card: Prospeccao) => void;
};

export default function ProspeccaoColumn({
  estagio,
  cards,
  cardArrastando,
  aceitaDrop,
  onDragStart,
  onDragEnd,
  onDrop,
  onAbrir,
  onMover,
}: Props) {
  const arrastandoAlgo = cardArrastando !== null;

  return (
    <section
      onDragOver={event => {
        // Só marca como alvo o que a máquina de estados aceita — evita o usuário soltar e
        // receber 409 por uma transição que a tela já sabia ser inválida.
        if (aceitaDrop) event.preventDefault();
      }}
      onDrop={event => {
        event.preventDefault();
        const id = event.dataTransfer.getData("text/plain");
        if (id && aceitaDrop) onDrop(id, estagio);
      }}
      className={`flex w-72 shrink-0 flex-col rounded-lg border border-slate-200 bg-slate-50 p-2
        transition-colors duration-200 dark:border-slate-700 dark:bg-slate-900/60
        ${arrastandoAlgo && aceitaDrop ? "border-[#612035]/50 bg-[#612035]/5 ring-2 ring-[#612035]/30" : ""}
        ${arrastandoAlgo && !aceitaDrop ? "opacity-40" : ""}`}
    >
      <header className="flex items-center justify-between px-1 pb-2">
        <h2 className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-600 dark:text-slate-300">
          <Icon name={ICONE_ESTAGIO[estagio]} className="text-[15px]" />
          {ROTULO_ESTAGIO[estagio]}
        </h2>
        <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-slate-600 dark:bg-slate-800 dark:text-slate-300">
          {cards.length}
        </span>
      </header>

      <div className="flex flex-1 flex-col gap-2 overflow-y-auto">
        {cards.map(card => (
          <ProspeccaoCard
            key={card.id}
            card={card}
            arrastando={cardArrastando === card.id}
            onAbrir={() => onAbrir(card.id)}
            onDragStart={() => onDragStart(card.id)}
            onDragEnd={onDragEnd}
            onMover={() => onMover(card)}
          />
        ))}
        {cards.length === 0 && (
          <p className="px-1 py-6 text-center text-[11px] text-slate-400">
            {arrastandoAlgo && aceitaDrop ? "solte aqui" : "nenhum card"}
          </p>
        )}
      </div>
    </section>
  );
}
