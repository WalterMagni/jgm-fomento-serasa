"use client";

import type { ReactNode } from "react";
import Icon from "@/components/ui/Icon";

/**
 * Bloco da faixa de resumo no topo das páginas de análise: rótulo com ícone, valor em destaque
 * e uma linha de apoio. O clique leva à seção com o detalhe.
 */
export default function BlocoResumo({
  icone,
  cor,
  rotulo,
  valor,
  apoio,
  corValor,
  atraso,
  onClick,
}: {
  icone: string;
  cor: string;
  rotulo: string;
  valor: ReactNode;
  apoio: ReactNode;
  corValor?: string;
  atraso: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="group flex flex-col gap-1.5 rounded-cartao border border-linha bg-white px-5 py-4 text-left transition-[transform,box-shadow] duration-300 ease-saida hover:-translate-y-0.5 hover:shadow-elevado focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary animate-subir motion-reduce:animate-none motion-reduce:hover:translate-y-0"
      style={{ animationDelay: `${atraso}ms` }}
    >
      <span className="flex items-center gap-2 text-[12.5px] font-semibold text-suave">
        <Icon name={icone} size={18} style={{ color: cor }} />
        {rotulo}
        <Icon name="chevron_right" size={14} className="ml-auto text-[#B5B1A6] transition-transform group-hover:translate-x-0.5" />
      </span>
      <span className="font-display text-[26px] font-semibold leading-tight tracking-[-0.015em]" style={{ color: corValor ?? "#262833" }}>
        {valor}
      </span>
      <span className="text-[12.5px] text-suave">{apoio}</span>
    </button>
  );
}
