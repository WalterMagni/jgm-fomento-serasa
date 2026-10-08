"use client";

import { useDraggable } from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import { ROTULO_POSICAO, ROTULO_TIPO, type LiberacaoCard, type Parecer } from "@/types/liberacao";
import Avatar from "./Avatar";
import {
  COR_POSICAO,
  ICONE_POSICAO,
  aguardando,
  formatDocumento,
  formatMoeda,
  situacaoPrazo,
  textoPrazo,
} from "./formatters";

const CLASSE_PRAZO = {
  ok: "text-slate-500 dark:text-slate-400",
  perto: "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200",
  vencido: "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-200",
} as const;

/** Avatar do membro do Comitê com o selo da posição; tracejado enquanto não deu parecer. */
function PipParecer({ parecer }: { parecer: Parecer }) {
  if (!parecer.posicao) {
    return <Avatar nome={parecer.usuarioNome} iniciais={parecer.iniciais} tamanho="xs" aguardando />;
  }
  return (
    <span className="relative inline-flex" title={`${parecer.usuarioNome}: ${ROTULO_POSICAO[parecer.posicao]}`}>
      <Avatar nome={parecer.usuarioNome} iniciais={parecer.iniciais} tamanho="xs" />
      <span
        className={`absolute -bottom-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full
          ring-2 ring-white dark:ring-slate-800 ${COR_POSICAO[parecer.posicao]}`}
      >
        <Icon name={ICONE_POSICAO[parecer.posicao]} size={8} strokeWidth={3} />
      </span>
    </span>
  );
}

type Props = {
  card: LiberacaoCard;
  onAbrir: () => void;
  /** Cópia que segue o ponteiro durante o arraste. Não registra arraste próprio. */
  sobreposicao?: boolean;
};

export function CardFace({ card, sobreposicao = false }: { card: LiberacaoCard; sobreposicao?: boolean }) {
  const prazo = situacaoPrazo(card);
  const faltam = aguardando(card);
  const mostraPareceres = card.pareceres.length > 0 && card.etapa !== "ORIGEM";
  // Quem dá parecer já aparece nos selos do Comitê; repetir no canto só faria barulho.
  const doComite = new Set(mostraPareceres ? card.pareceres.map(parecer => parecer.usuarioId) : []);
  const outrosMembros = card.membros.filter(membro => !doComite.has(membro.id));
  const membrosVisiveis = outrosMembros.slice(0, 2);

  return (
    <div
      className={`rounded-xl border border-slate-200/80 bg-white p-3 text-left dark:border-slate-700 dark:bg-slate-800
        ${sobreposicao ? "rotate-[1.5deg] shadow-2xl ring-1 ring-black/5" : "shadow-[0_1px_2px_rgba(15,23,42,0.06)]"}`}
    >
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <span className="font-mono text-[10px] font-medium text-slate-400">#{card.numero}</span>
          {card.tipoOperacao && (
            <span className="truncate rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide
              text-slate-600 dark:bg-slate-700 dark:text-slate-300">
              {ROTULO_TIPO[card.tipoOperacao]}
            </span>
          )}
        </div>
        {prazo && card.prazo && (
          <span
            className={`inline-flex shrink-0 items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-medium ${CLASSE_PRAZO[prazo]}`}
            title={prazo === "vencido" ? "Prazo vencido" : prazo === "perto" ? "Vence em menos de 24 h" : "Prazo"}
          >
            <Icon name="schedule" size={11} />
            {textoPrazo(card.prazo)}
          </span>
        )}
      </div>

      <h3 className="mt-1.5 line-clamp-2 font-sans text-[13px] font-semibold leading-snug text-slate-800 dark:text-slate-100">
        {card.cedenteNome}
      </h3>
      <p className="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">{formatDocumento(card.cedenteCnpj)}</p>

      {card.valor != null && (
        <p className="font-display mt-2 text-[15px] font-semibold tracking-tight text-slate-900 dark:text-white">
          {formatMoeda(card.valor)}
        </p>
      )}

      {card.etapa === "COMITE" && faltam.length > 0 && (
        <p className="mt-2 inline-flex items-center gap-1 rounded-md bg-[#612035]/8 px-1.5 py-0.5 text-[10.5px]
          font-medium text-[#612035] dark:bg-[#612035]/30 dark:text-[#e8a3b6]">
          <Icon name="lock" size={11} />
          {faltam.length === 1 ? `falta parecer de ${faltam[0].usuarioNome.split(" ")[0]}` : `faltam ${faltam.length} pareceres`}
        </p>
      )}

      <div className="mt-2.5 flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-2.5 text-[11px] text-slate-500 dark:text-slate-400">
          {mostraPareceres && (
            <span className="flex items-center gap-1" aria-label="Pareceres do Comitê">
              {card.pareceres.map(parecer => (
                <PipParecer key={parecer.id} parecer={parecer} />
              ))}
            </span>
          )}
          {card.sacadosQtd > 0 && (
            <span className="inline-flex items-center gap-0.5" title={`${card.sacadosQtd} sacado(s)`}>
              <Icon name="people" size={12} />
              {card.sacadosQtd}
            </span>
          )}
          {card.comentarios > 0 && (
            <span className="inline-flex items-center gap-0.5" title={`${card.comentarios} comentário(s)`}>
              <Icon name="chat" size={12} />
              {card.comentarios}
            </span>
          )}
          {card.pendenciasAbertas > 0 && (
            <span
              className="inline-flex items-center gap-0.5 rounded-md bg-[#D1732C]/12 px-1.5 py-0.5 font-medium text-[#a8551a] dark:text-[#f0a46b]"
              title="Pendências abertas"
            >
              <Icon name="hourglass_top" size={11} />
              {card.pendenciasAbertas}
            </span>
          )}
        </div>
        {membrosVisiveis.length > 0 && (
          <span className="flex shrink-0 -space-x-1.5">
            {membrosVisiveis.map(membro => (
              <Avatar key={membro.id} nome={membro.nome} iniciais={membro.iniciais} tamanho="xs" />
            ))}
            {outrosMembros.length > 2 && (
              <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-slate-200 text-[10px]
                font-semibold text-slate-600 ring-2 ring-white dark:bg-slate-600 dark:text-slate-200 dark:ring-slate-800">
                +{outrosMembros.length - 2}
              </span>
            )}
          </span>
        )}
      </div>
    </div>
  );
}

/**
 * Card no quadro. Arrasta quando há para onde ir; senão só abre.
 *
 * <p>Sem destino permitido (auxiliar olhando um card no Comitê), o card não finge que arrasta:
 * o cursor fica de ponteiro e o clique abre o detalhe, onde o motivo aparece.</p>
 */
export default function LiberacaoCardItem({ card, onAbrir }: Props) {
  const podeArrastar = card.destinos.some(destino => destino.permitido);
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: card.id,
    data: { card },
    disabled: !podeArrastar,
  });

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      role="button"
      tabIndex={0}
      aria-label={`Card ${card.numero}: ${card.cedenteNome}. Enter abre.`}
      aria-roledescription={podeArrastar ? "card arrastável" : "card"}
      onClick={onAbrir}
      onKeyDown={event => {
        if (event.key === "Enter") onAbrir();
        listeners?.onKeyDown?.(event);
      }}
      // Hover só mexe em sombra e borda: escala deslocaria a coluna inteira a cada passada.
      className={`esteira-card-in group rounded-xl outline-none transition-[box-shadow,opacity] duration-200
        hover:[&>div]:border-slate-300 hover:[&>div]:shadow-md dark:hover:[&>div]:border-slate-600
        focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900
        ${podeArrastar ? "cursor-grab active:cursor-grabbing" : "cursor-pointer"}
        ${isDragging ? "opacity-30" : ""}`}
    >
      <CardFace card={card} />
    </div>
  );
}
