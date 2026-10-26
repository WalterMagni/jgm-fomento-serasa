"use client";

import type { ReactNode } from "react";
import Icon from "@/components/ui/Icon";
import { VERDE, moeda } from "@/components/judit/JuditPecas";
import type { JuditGrupoPolo, JuditPorPeriodo, JuditSoma } from "@/types/judit";

/**
 * Gráficos do painel de processos, no desenho do Vadu: barras simples em CSS e SVG, sem biblioteca.
 * Polo nas cores do portal — ré no vinho, autora no azul, outros no cinza.
 */

export const COR_POLO: Record<JuditGrupoPolo, string> = { REU: "#612035", AUTOR: "#2956E0", OUTROS: "#B9B4A8" };
export const ROTULO_POLO: Record<JuditGrupoPolo, string> = { REU: "Como ré", AUTOR: "Como autora", OUTROS: "Outros" };

const numero = (n: number) => n.toLocaleString("pt-BR");

/** Faixa cinza com ícone e título, como as seções do Vadu. */
export function Secao({ icone, titulo, extra, children }: { icone: string; titulo: string; extra?: ReactNode; children: ReactNode }) {
  return (
    <section className="break-inside-avoid space-y-3">
      <div className="flex flex-wrap items-center gap-2 rounded-lg bg-[#F2F1EC] px-3 py-2 dark:bg-white/5">
        <Icon name={icone} size={18} className="text-[#5E5C55] dark:text-gray-300" />
        <h3 className="text-sm font-bold text-grafite dark:text-white">{titulo}</h3>
        {extra && <div className="ml-auto">{extra}</div>}
      </div>
      {children}
    </section>
  );
}

/** Card de total: quantidade grande, valor embaixo. */
export function CardTotal({ rotulo, soma, apoio = "Total processos", destaque }: { rotulo: string; soma: JuditSoma; apoio?: string; destaque?: string }) {
  return (
    <div className="rounded-lg border border-border-light bg-white px-4 py-3.5 dark:border-border-dark dark:bg-transparent">
      <div className="flex items-start justify-between gap-2">
        <span className="text-[11px] font-bold uppercase tracking-wide text-gray-500">{rotulo}</span>
        <Icon name="balance" size={22} className="text-gray-400" />
      </div>
      <div className="mt-1 text-2xl font-semibold tabular-nums" style={{ color: destaque ?? "#262833" }}>
        {numero(soma.quantidade)}
      </div>
      <div className="text-[11px] font-semibold text-gray-500">{apoio}</div>
      <div className="text-[11px] text-gray-400">Valor {moeda(soma.valor)}</div>
    </div>
  );
}

export function Legenda({ itens }: { itens: { rotulo: string; cor: string }[] }) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-[11px] text-gray-500">
      {itens.map(i => (
        <span key={i.rotulo} className="inline-flex items-center gap-1">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: i.cor }} aria-hidden />
          {i.rotulo}
        </span>
      ))}
    </div>
  );
}

export const LEGENDA_POLO = (["REU", "AUTOR", "OUTROS"] as const).map(p => ({ rotulo: ROTULO_POLO[p], cor: COR_POLO[p] }));

/** Uma barra por item, do maior para o menor. */
export function BarrasHorizontais({
  itens,
  vazio = "Sem dados.",
}: {
  itens: { rotulo: string; valor: number; cor?: string }[];
  vazio?: string;
}) {
  if (itens.length === 0 || itens.every(i => i.valor === 0)) {
    return <p className="text-xs text-gray-400">{vazio}</p>;
  }
  const maior = Math.max(...itens.map(i => i.valor), 1);
  return (
    <ul className="space-y-1.5">
      {itens.map(i => (
        <li key={i.rotulo} className="grid grid-cols-[minmax(0,40%)_1fr_auto] items-center gap-2 text-[11px]">
          <span className="truncate text-right text-gray-600 dark:text-gray-300" title={i.rotulo}>
            {i.rotulo}
          </span>
          <span className="h-3.5 rounded-sm bg-[#F2F1EC] dark:bg-white/5">
            <span
              className="block h-full rounded-sm"
              style={{ width: `${(i.valor / maior) * 100}%`, backgroundColor: i.cor ?? VERDE }}
            />
          </span>
          <span className="w-8 text-right font-semibold tabular-nums text-gray-600 dark:text-gray-300">{numero(i.valor)}</span>
        </li>
      ))}
    </ul>
  );
}

/** Um período por linha, com ré, autora e outros empilhados. */
export function BarrasPorPolo({
  periodos,
  rotulo = p => p,
  vazio = "Sem processos no período.",
}: {
  periodos: JuditPorPeriodo[];
  rotulo?: (periodo: string) => string;
  vazio?: string;
}) {
  if (periodos.length === 0) {
    return <p className="text-xs text-gray-400">{vazio}</p>;
  }
  const maior = Math.max(...periodos.map(p => p.reu + p.autor + p.outros), 1);
  return (
    <div className="space-y-2">
      <div className="flex justify-end">
        <Legenda itens={LEGENDA_POLO} />
      </div>
      <ul className="space-y-1.5">
        {periodos.map(p => {
          const total = p.reu + p.autor + p.outros;
          return (
            <li key={p.periodo} className="grid grid-cols-[64px_1fr_auto] items-center gap-2 text-[11px]">
              <span className="text-right tabular-nums text-gray-500">{rotulo(p.periodo)}</span>
              <span
                className="flex h-3.5 overflow-hidden rounded-sm bg-[#F2F1EC] dark:bg-white/5"
                title={`Como ré: ${p.reu} · Como autora: ${p.autor} · Outros: ${p.outros}`}
              >
                {(["REU", "AUTOR", "OUTROS"] as const).map(g => {
                  const qtd = g === "REU" ? p.reu : g === "AUTOR" ? p.autor : p.outros;
                  return qtd > 0 ? (
                    <span key={g} className="h-full" style={{ width: `${(qtd / maior) * 100}%`, backgroundColor: COR_POLO[g] }} />
                  ) : null;
                })}
              </span>
              <span className="w-8 text-right font-semibold tabular-nums text-gray-600 dark:text-gray-300">{total}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

/** Colunas verticais, como o gráfico de Estado do Vadu. */
export function Colunas({ itens, vazio = "Sem dados." }: { itens: { rotulo: string; valor: number }[]; vazio?: string }) {
  if (itens.length === 0) {
    return <p className="text-xs text-gray-400">{vazio}</p>;
  }
  const maior = Math.max(...itens.map(i => i.valor), 1);
  const colunas = { gridTemplateColumns: `repeat(${itens.length}, minmax(0, 120px))` };
  return (
    <div>
      <div className="grid h-40 items-end gap-3 border-b border-l border-[#E7E4DC] px-3 pt-5 dark:border-white/10" style={colunas}>
        {itens.map(i => (
          <div key={i.rotulo} className="flex h-full flex-col items-center justify-end gap-1">
            <span className="text-[11px] font-semibold tabular-nums text-gray-600 dark:text-gray-300">{numero(i.valor)}</span>
            <span className="w-full rounded-t-sm" style={{ height: `${(i.valor / maior) * 100}%`, backgroundColor: VERDE }} />
          </div>
        ))}
      </div>
      <div className="grid gap-3 px-3 pt-1" style={colunas}>
        {itens.map(i => (
          <span key={i.rotulo} className="truncate text-center text-[11px] text-gray-500" title={i.rotulo}>
            {i.rotulo}
          </span>
        ))}
      </div>
    </div>
  );
}

/** A fatia da linha, como a pizza pequena da tabela de áreas do Vadu. */
export function Pizza({ porcentagem, cor = "#D1732C" }: { porcentagem: number; cor?: string }) {
  const raio = 7;
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-label={`${Math.round(porcentagem)}%`} role="img">
      <circle cx="9" cy="9" r={raio} fill="#FBEBDD" />
      <circle
        cx="9"
        cy="9"
        r={raio / 2}
        fill="none"
        stroke={cor}
        strokeWidth={raio}
        strokeDasharray={`${(porcentagem / 100) * (Math.PI * raio)} ${Math.PI * raio}`}
        transform="rotate(-90 9 9)"
      />
      <title>{`${Math.round(porcentagem)}%`}</title>
    </svg>
  );
}
