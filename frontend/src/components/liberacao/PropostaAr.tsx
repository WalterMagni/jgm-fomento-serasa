"use client";

import { useRef, useState, type DragEvent } from "react";
import Icon from "@/components/ui/Icon";
import { useLerProposta } from "@/hooks/useLiberacao";
import type { CarteiraSacado, PropostaAr, PropostaImportada } from "@/types/liberacao";
import { formatDataHora, formatMoeda, formatMoedaCurta } from "./formatters";

/**
 * Análise de Risco (AR) do sistema de operações: importação no formulário, resumo no card e a
 * linha de carteira de cada sacado. Os números são foto do relatório, não se recalculam.
 */

const UMA_CASA = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const DUAS_CASAS = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Comprometimento com uma casa ("255,8%"); concentração, que fica perto de zero, com duas. */
export function formatPercentual(valor: number | null | undefined, casas: 1 | 2 = 1) {
  return valor == null ? "—" : `${(casas === 2 ? DUAS_CASAS : UMA_CASA).format(valor)}%`;
}

function rotuloAr(proposta: PropostaAr) {
  return proposta.emitidaEm ? `AR de ${formatDataHora(proposta.emitidaEm)}` : "AR importada";
}

// ------------------------------------------------------------------ formulário

type Props = {
  /** AR atual do formulário: a recém-importada ou a que o card já tinha. */
  proposta: PropostaAr | null;
  /** Nome do PDF importado agora; nulo quando a AR veio do card salvo. */
  arquivo: string | null;
  avisos: string[];
  onLida: (resultado: PropostaImportada, arquivo: File) => void;
  onRemover: () => void;
};

export function ImportarProposta({ proposta, arquivo, avisos, onLida, onRemover }: Props) {
  const ler = useLerProposta();
  const entrada = useRef<HTMLInputElement>(null);
  const [arrastando, setArrastando] = useState(false);

  function receber(lista: FileList | null) {
    const pdf = lista?.[0];
    if (!pdf) return;
    ler.mutate(pdf, { onSuccess: resultado => onLida(resultado, pdf) });
  }

  const soltar = {
    onDragOver: (event: DragEvent) => {
      event.preventDefault();
      setArrastando(true);
    },
    onDragLeave: () => setArrastando(false),
    onDrop: (event: DragEvent) => {
      event.preventDefault();
      setArrastando(false);
      receber(event.dataTransfer.files);
    },
  };

  const seletor = (
    <input
      ref={entrada}
      type="file"
      accept="application/pdf,.pdf"
      className="sr-only"
      tabIndex={-1}
      aria-hidden
      onChange={event => {
        receber(event.target.files);
        event.target.value = "";
      }}
    />
  );

  if (!proposta) {
    return (
      <div {...soltar}>
        {seletor}
        <button
          type="button"
          onClick={() => entrada.current?.click()}
          disabled={ler.isPending}
          className={`group flex w-full cursor-pointer items-center gap-3 rounded-xl border border-dashed px-4 py-3 text-left transition-colors
            focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] disabled:cursor-wait ${
              arrastando
                ? "border-[#612035] bg-[#612035]/5"
                : "border-slate-300 hover:border-[#612035] hover:bg-[#612035]/[0.03] dark:border-slate-600"
            }`}
        >
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#612035]/8 text-[#612035] dark:bg-[#612035]/30 dark:text-[#e8a3b6]">
            <Icon name={ler.isPending ? "hourglass_empty" : "upload_file"} size={18} />
          </span>
          <span className="min-w-0">
            <span className="block text-sm font-semibold text-slate-800 dark:text-slate-100">
              {ler.isPending ? "Lendo a proposta…" : "Importar proposta (PDF da AR)"}
            </span>
            <span className="block text-[11px] text-slate-500">
              Preenche cedente, valor e sacados com a carteira de cada um. Arraste o PDF aqui ou clique.
            </span>
          </span>
        </button>
      </div>
    );
  }

  return (
    <div {...soltar} className={`rounded-xl border ${arrastando ? "border-[#612035]" : "border-slate-200 dark:border-slate-700"}`}>
      {seletor}
      <div className="flex items-center gap-3 px-3 py-2.5">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[#612035] text-white">
          <Icon name="picture_as_pdf" size={16} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-slate-800 dark:text-slate-100">
            {rotuloAr(proposta)}
            {arquivo && <span className="font-normal text-slate-400"> · {arquivo}</span>}
          </p>
          <p className="truncate text-[11px] text-slate-500">
            {proposta.qtdLiberados ?? "—"} título{proposta.qtdLiberados === 1 ? "" : "s"} · {formatMoeda(proposta.faceLiberados)} · comprometimento{" "}
            {formatPercentual(proposta.comprometimentoAtual)} → {formatPercentual(proposta.comprometimentoApos)}
          </p>
        </div>
        <button
          type="button"
          onClick={() => entrada.current?.click()}
          disabled={ler.isPending}
          className="cursor-pointer rounded-md px-2 py-1 text-xs font-medium text-[#612035] hover:bg-[#612035]/8 disabled:opacity-60 dark:text-[#e8a3b6]"
        >
          {ler.isPending ? "lendo…" : arquivo ? "trocar" : "reimportar"}
        </button>
        {arquivo && (
          <button
            type="button"
            onClick={onRemover}
            aria-label="Desfazer importação"
            title="Desfazer importação (os campos preenchidos ficam)"
            className="inline-flex h-7 w-7 cursor-pointer items-center justify-center rounded-md text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:hover:bg-slate-800"
          >
            <Icon name="close" size={14} />
          </button>
        )}
      </div>
      {avisos.length > 0 && (
        <ul className="space-y-1 border-t border-amber-200/70 bg-amber-50 px-3 py-2 text-[11.5px] text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-100">
          {avisos.map(aviso => (
            <li key={aviso} className="flex gap-1.5">
              <Icon name="warning" size={12} className="mt-0.5 shrink-0" />
              <span>{aviso}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ------------------------------------------------------------------ card

function Numero({ rotulo, valor, detalhe, tom = "neutro" }: { rotulo: string; valor: string; detalhe?: string; tom?: "neutro" | "alerta" | "risco" }) {
  const cor = tom === "risco" ? "text-rose-700 dark:text-rose-300" : tom === "alerta" ? "text-amber-700 dark:text-amber-300" : "text-slate-800 dark:text-slate-100";
  return (
    <div className="min-w-0">
      <p className="text-[10.5px] font-medium uppercase tracking-wide text-slate-400">{rotulo}</p>
      <p className={`truncate font-display text-[15px] font-semibold tabular-nums ${cor}`}>{valor}</p>
      {detalhe && <p className="truncate text-[11px] text-slate-500">{detalhe}</p>}
    </div>
  );
}

/**
 * Comprometimento do limite antes e depois da compra, numa régua com o 100% marcado: passar do
 * limite é o que o Comitê olha primeiro.
 */
function ReguaComprometimento({ atual, apos }: { atual: number | null; apos: number | null }) {
  if (atual == null && apos == null) return null;
  const topo = Math.max(atual ?? 0, apos ?? 0, 100) * 1.08;
  const pos = (valor: number) => `${Math.min(100, (valor / topo) * 100)}%`;
  const estourou = (apos ?? atual ?? 0) > 100;
  return (
    <div>
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-[10.5px] font-medium uppercase tracking-wide text-slate-400">Comprometimento do limite</p>
        <p className={`font-display text-[15px] font-semibold tabular-nums ${estourou ? "text-rose-700 dark:text-rose-300" : "text-slate-800 dark:text-slate-100"}`}>
          {formatPercentual(atual)} <span className="text-slate-400">→</span> {formatPercentual(apos)}
        </p>
      </div>
      <div className="relative mt-1.5 h-2 rounded-full bg-slate-100 dark:bg-slate-800" role="img"
        aria-label={`Comprometimento de ${formatPercentual(atual)} hoje e ${formatPercentual(apos)} após a compra`}>
        {apos != null && (
          <div className={`absolute inset-y-0 left-0 rounded-full ${estourou ? "bg-rose-400/70" : "bg-[#612035]/40"}`} style={{ width: pos(apos) }} />
        )}
        {atual != null && (
          <div className={`absolute inset-y-0 left-0 rounded-full ${atual > 100 ? "bg-rose-600" : "bg-[#612035]"}`} style={{ width: pos(atual) }} />
        )}
        <div className="absolute -inset-y-1 w-px bg-slate-500 dark:bg-slate-300" style={{ left: pos(100) }} />
      </div>
      <div className="relative mt-0.5 h-3 text-[10px] text-slate-400">
        <span className="absolute -translate-x-1/2" style={{ left: pos(100) }}>limite</span>
      </div>
    </div>
  );
}

export function ResumoProposta({ proposta }: { proposta: PropostaAr }) {
  const parcialDosTitulos = proposta.qtdTotal != null && proposta.qtdLiberados != null && proposta.qtdTotal !== proposta.qtdLiberados;
  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 dark:border-slate-700">
      <div className="grid gap-4 px-4 py-3.5 sm:grid-cols-[1.4fr_1fr]">
        <ReguaComprometimento atual={proposta.comprometimentoAtual} apos={proposta.comprometimentoApos} />
        <div className="grid grid-cols-2 gap-3">
          <Numero rotulo="Limite individual" valor={formatMoeda(proposta.limiteIndividual)}
            detalhe={proposta.limiteGrupo ? `grupo ${formatMoeda(proposta.limiteGrupo)}` : proposta.grupo ?? undefined} />
          <Numero rotulo="Concentração" valor={`${formatPercentual(proposta.concentracaoAtual, 2)} → ${formatPercentual(proposta.concentracaoApos, 2)}`} />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 border-t border-slate-100 px-4 py-3.5 sm:grid-cols-4 dark:border-slate-800">
        <Numero rotulo="Títulos liberados" valor={`${proposta.qtdLiberados ?? "—"}${parcialDosTitulos ? ` de ${proposta.qtdTotal}` : ""}`}
          detalhe={proposta.prazoMedio != null ? `prazo médio ${Math.round(proposta.prazoMedio)} dias` : undefined} />
        <Numero rotulo="Face" valor={formatMoeda(proposta.faceLiberados)}
          detalhe={parcialDosTitulos ? `proposta ${formatMoeda(proposta.valorTotal)}` : undefined} />
        <Numero rotulo="Desconto" valor={formatMoeda(proposta.desconto)} />
        <Numero rotulo="Líquido" valor={formatMoeda(proposta.liquido)} />
      </div>

      <div className="border-t border-slate-100 bg-slate-50/70 px-4 py-3.5 dark:border-slate-800 dark:bg-slate-800/30">
        <p className="mb-2 text-[10.5px] font-bold uppercase tracking-[0.08em] text-slate-500">Carteira do cedente</p>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Numero rotulo="A vencer" valor={formatMoeda(proposta.vincendos)} />
          <Numero rotulo="Vencidos" valor={formatMoeda(proposta.vencidos)} tom={(proposta.vencidos ?? 0) > 0 ? "risco" : "neutro"} />
          <Numero rotulo="Liquidados" valor={formatMoeda(proposta.liquidados)}
            detalhe={proposta.liquidadosEmAtraso ? `${formatMoeda(proposta.liquidadosEmAtraso)} em atraso` : undefined} />
          <Numero rotulo="Recomprados" valor={formatMoeda(proposta.recomprados)} tom={(proposta.recomprados ?? 0) > 0 ? "alerta" : "neutro"} />
        </div>
      </div>
    </div>
  );
}

export function rotuloProposta(proposta: PropostaAr) {
  return `${rotuloAr(proposta)}${proposta.clienteCodigo ? ` · cód. ${proposta.clienteCodigo}` : ""}`;
}

// ------------------------------------------------------------------ sacado

/** O que o sacado já tem com o cedente, numa linha. Vencido e recompra ganham cor. */
export function CarteiraLinha({ carteira }: { carteira: CarteiraSacado | null | undefined }) {
  if (!carteira) return null;
  const partes = [
    carteira.titulos != null && `${carteira.titulos} título${carteira.titulos === 1 ? "" : "s"}`,
    carteira.vincendos != null && `a vencer ${formatMoedaCurta(carteira.vincendos)}`,
    carteira.liquidados != null && `liquidados ${formatMoedaCurta(carteira.liquidados)}`,
  ].filter(Boolean);
  const vencidos = (carteira.vencidos ?? 0) > 0;
  const recomprados = (carteira.recomprados ?? 0) > 0;
  return (
    <p
      className="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-[10.5px] text-slate-500"
      title={`Na AR: vencidos ${formatMoeda(carteira.vencidos)} · a vencer ${formatMoeda(carteira.vincendos)} · abertos ${formatMoeda(carteira.abertos)} · liquidados ${formatMoeda(carteira.liquidados)} · recomprados ${formatMoeda(carteira.recomprados)}`}
    >
      <Icon name="account_balance_wallet" size={11} className="text-slate-400" />
      <span>{partes.join(" · ")}</span>
      {vencidos && (
        <span className="rounded bg-rose-100 px-1 font-semibold text-rose-800 dark:bg-rose-900/40 dark:text-rose-200">
          vencidos {formatMoedaCurta(carteira.vencidos ?? 0)}
        </span>
      )}
      {recomprados && (
        <span className="rounded bg-amber-100 px-1 font-semibold text-amber-900 dark:bg-amber-900/40 dark:text-amber-100">
          recomprados {formatMoedaCurta(carteira.recomprados ?? 0)}
        </span>
      )}
    </p>
  );
}
