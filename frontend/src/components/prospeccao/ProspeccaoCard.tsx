"use client";

import { useDraggable } from "@dnd-kit/core";
import Icon from "@/components/ui/Icon";
import { RESSALVA } from "@/lib/ressalva";
import Avatar from "@/components/liberacao/Avatar";
import { TOM } from "@/components/liberacao/cores";
import type { Prospeccao } from "@/types/prospeccao";
import { COR_ESTAGIO } from "./estilo";
import { formatCnpj, formatMoedaCurta, semaforoDe, textoPrazo } from "./formatters";

function iniciais(nome: string) {
  return nome.split(/\s+/).filter(Boolean).slice(0, 2).map(parte => parte[0]?.toUpperCase() ?? "").join("");
}

const PRAZO = {
  ok: "text-slate-500 dark:text-slate-400",
  atencao: "bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-200",
  estourado: "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-200",
} as const;

/**
 * Face do card, sem o comportamento de arraste — reaproveitada na cópia que segue o ponteiro.
 *
 * <p>Lê de cima para baixo na ordem em que a analista decide o que fazer: etiquetas e cor para
 * organizar, a empresa, o número que importa naquele estágio (limite, documentos, pareceres), o
 * prazo e quem está com o card.</p>
 */
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
  const estilo = COR_ESTAGIO[card.estagio];
  // No Aprovado o comercial anexa: conta o que falta enviar. Na documentação a analista confere:
  // conta o que já foi resolvido.
  const coleta = card.estagio === "APROVADO";
  const feitos = coleta ? card.documentosTotal - card.documentosFaltamAnexar : card.documentosResolvidos;
  const progresso = card.documentosTotal > 0 ? Math.max(0, feitos) / card.documentosTotal : 0;
  const mostraDocumentos = card.documentosTotal > 0 && (coleta || card.estagio === "DOCUMENTACAO");
  const mostraComite = card.estagio === "COMITE" && card.pareceresEsperados > 0;

  return (
    <div
      className={`relative overflow-hidden rounded-xl border bg-white text-left transition-shadow dark:bg-slate-800
        ${semaforo === "estourado" ? "border-rose-300 dark:border-rose-900/70" : "border-slate-200/90 dark:border-slate-700"}
        ${card.cor ? "pt-1.5" : ""}
        ${sobreposicao ? "rotate-[1.5deg] shadow-2xl ring-1 ring-black/5" : "shadow-[0_1px_2px_rgba(15,23,42,0.06)]"}
        ${selecionado ? "ring-2 ring-[#612035] dark:ring-[#e8a3b6]" : ""}`}
    >
      {card.cor && <span className={`absolute inset-x-0 top-0 h-1.5 ${TOM[card.cor].faixa}`} aria-label={`Cor ${TOM[card.cor].nome}`} />}
      {card.ressalva && <span aria-hidden className="absolute inset-y-0 left-0 w-1" style={{ background: RESSALVA.hex }} />}

      <div className="space-y-2 p-3">
        {card.etiquetas.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {card.etiquetas.map(etiqueta => (
              <span key={etiqueta.id} className={`max-w-full truncate rounded-full px-2 py-px text-[10px] font-semibold ${TOM[etiqueta.cor].chip}`}>
                {etiqueta.nome}
              </span>
            ))}
          </div>
        )}

        <div>
          <h3 className="flex items-start gap-1.5 text-[13px] font-semibold leading-snug text-slate-900 dark:text-white">
            {selecionando && (
              <Icon
                name={selecionado ? "check_circle" : "add_circle"}
                size={15}
                className={`mt-px shrink-0 ${selecionado ? "text-[#612035] dark:text-[#e8a3b6]" : "text-slate-300"}`}
              />
            )}
            <span className="line-clamp-2">{card.razaoSocial}</span>
          </h3>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 font-mono text-[10.5px] text-slate-400">
            <span className="whitespace-nowrap">{formatCnpj(card.cnpj)}</span>
            {card.origem === "AUTOMATICA" && (
              <span className="rounded bg-[#2956E0]/10 px-1 font-sans text-[9px] font-bold uppercase tracking-wide text-[#2956E0] dark:text-[#9db4ff]" title="Entrou por visão cedente SIM">
                VC
              </span>
            )}
            {card.legado && (
              <span className="rounded bg-amber-100 px-1 font-sans text-[9px] font-bold uppercase tracking-wide text-amber-800 dark:bg-amber-900/40 dark:text-amber-200"
                title="Migrado da esteira antiga sem decisão registrada">
                legado
              </span>
            )}
            {card.reaberturas > 0 && (
              <span className="rounded bg-slate-100 px-1 font-sans text-[9px] font-bold uppercase text-slate-500 dark:bg-slate-700" title="Já passou pela esteira antes">
                {card.reaberturas}ª vez
              </span>
            )}
          </p>
        </div>

        {card.limiteVigente != null && (
          <p className="flex items-baseline gap-1.5">
            <span className="font-display text-lg font-semibold leading-none text-slate-900 dark:text-white">{formatMoedaCurta(card.limiteVigente)}</span>
            <span className="text-[10px] font-medium uppercase tracking-wide text-slate-400">limite</span>
          </p>
        )}
        {card.ressalva && (
          <p
            title={`Ressalva: ${card.ressalva}`}
            className={`inline-flex max-w-full items-center gap-1 rounded-md px-1.5 py-0.5 text-[10.5px] font-semibold ${RESSALVA.chip}`}
          >
            <Icon name={RESSALVA.icone} size={11} className="shrink-0" />
            <span className="truncate">{RESSALVA.rotulo}</span>
          </p>
        )}

        {mostraDocumentos && (
          <div
            title={
              coleta
                ? `${card.documentosFaltamAnexar} obrigatório(s) ainda por anexar; com todos, o card segue para a documentação`
                : `${card.documentosResolvidos} de ${card.documentosTotal} documentos resolvidos`
            }
          >
            <div className="mb-1 flex items-center justify-between text-[10.5px] text-slate-500">
              <span className="inline-flex items-center gap-1"><Icon name="folder_open" size={11} /> Documentos</span>
              <span className="tabular-nums">
                {coleta
                  ? card.documentosFaltamAnexar === 0
                    ? "todos anexados"
                    : `faltam ${card.documentosFaltamAnexar}`
                  : `${card.documentosResolvidos}/${card.documentosTotal}`}
              </span>
            </div>
            <div className="h-1 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
              <div className={`h-full rounded-full ${estilo.faixa} transition-[width] duration-500`} style={{ width: `${Math.round(progresso * 100)}%` }} />
            </div>
          </div>
        )}

        {mostraComite && (
          <div className="flex items-center gap-2 text-[10.5px] text-slate-500" title="Pareceres do comitê nesta rodada">
            <span className="inline-flex items-center gap-1"><Icon name="groups" size={11} /> Comitê</span>
            <span className="flex gap-0.5">
              {Array.from({ length: card.pareceresEsperados }, (_, i) => (
                <span key={i} className={`h-1.5 w-4 rounded-full ${i < card.pareceresRegistrados ? estilo.faixa : "bg-slate-200 dark:bg-slate-700"}`} />
              ))}
            </span>
            <span className="tabular-nums">{card.pareceresRegistrados}/{card.pareceresEsperados}</span>
          </div>
        )}

        <div className="flex flex-wrap items-center gap-1.5 text-[10.5px]">
          <span className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 font-medium ${PRAZO[semaforo]}`}>
            <Icon name={semaforo === "ok" ? "schedule" : "warning"} size={11} />
            {textoPrazo(card)}
          </span>
          {card.pendenciasAbertas > 0 && (
            <span className="inline-flex items-center gap-1 rounded-md bg-[#D1732C]/12 px-1.5 py-0.5 font-semibold text-[#8f4714] dark:bg-[#D1732C]/25 dark:text-[#f7c9a6]"
              title="Pendência aberta: o prazo está parado até o comercial responder">
              <Icon name="hourglass_top" size={11} />
              aguardando comercial
            </span>
          )}
        </div>
      </div>

      <footer className="flex items-center justify-between gap-2 border-t border-slate-100 px-3 py-2 dark:border-slate-700/70">
        <span className="flex min-w-0 items-center gap-1.5 text-[11px]">
          {card.analistaNome ? (
            <>
              <Avatar nome={card.analistaNome} iniciais={iniciais(card.analistaNome)} tamanho="xs" />
              <span className="truncate font-medium text-slate-700 dark:text-slate-200">{card.analistaNome.split(" ")[0]}</span>
            </>
          ) : (
            <span className="inline-flex items-center gap-1 italic text-slate-400">
              <span className="h-5 w-5 rounded-full border border-dashed border-slate-300 dark:border-slate-600" />
              ninguém assumiu
            </span>
          )}
        </span>
        <span className="truncate text-[10.5px] text-slate-400" title="Comercial">
          {card.comercialNome ?? "direto"}
        </span>
      </footer>
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
      className={`esteira-card-in block w-full rounded-xl text-left transition-[opacity,box-shadow,transform] duration-200
        hover:-translate-y-px hover:shadow-[0_6px_16px_-6px_rgba(15,23,42,0.18)] motion-reduce:hover:translate-y-0
        focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2 dark:focus-visible:ring-offset-slate-900
        ${selecionando ? "cursor-pointer" : "cursor-grab active:cursor-grabbing"} ${isDragging ? "opacity-30" : ""}`}
    >
      <CardFace card={card} selecionando={selecionando} selecionado={selecionado} />
    </button>
  );
}
