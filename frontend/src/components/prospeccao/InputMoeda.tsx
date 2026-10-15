"use client";

import { useState } from "react";
import { CAMPO } from "@/components/liberacao/Dialogo";
import CampoNumero from "@/components/ui/CampoNumero";

const MOEDA = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Valor em reais digitado como no Excel: "250.000,00" ou "250000".
 *
 * <p>O texto digitado vive no estado local enquanto o campo tem foco, já com os pontos de milhar
 * (o {@link CampoNumero} cuida do cursor); ao sair, vira número e volta com duas casas.</p>
 */
export default function InputMoeda({
  valor,
  onMudar,
  desabilitado = false,
  rotulo,
  autoFocus,
}: {
  valor: number | null;
  onMudar: (v: number | null) => void;
  desabilitado?: boolean;
  /** Rótulo acessível quando o campo não tem label visível, como nas linhas de condição. */
  rotulo?: string;
  autoFocus?: boolean;
}) {
  const [rascunho, setRascunho] = useState<string | null>(null);
  const exibido = rascunho ?? (valor == null ? "" : MOEDA.format(valor));

  return (
    <div className="relative">
      <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-xs text-slate-400">R$</span>
      <CampoNumero
        aria-label={rotulo}
        data-autofocus={autoFocus ? true : undefined}
        valor={exibido}
        disabled={desabilitado}
        onFocus={() => setRascunho(valor == null ? "" : MOEDA.format(valor))}
        onMudar={texto => {
          setRascunho(texto);
          // Também avisa durante a digitação: quem soma as condições ao lado vê o total mudar.
          onMudar(paraNumero(texto));
        }}
        onBlur={() => setRascunho(null)}
        placeholder="0,00"
        className={`${CAMPO} pl-9 text-right tabular-nums`}
      />
    </div>
  );
}

/** "1.234,56" → 1234.56. Ponto é milhar, vírgula é decimal, como se digita no Brasil. */
export function paraNumero(texto: string): number | null {
  const limpo = texto.trim();
  if (!limpo) return null;
  const numero = Number(limpo.replace(/\./g, "").replace(",", "."));
  return Number.isFinite(numero) && numero >= 0 ? numero : null;
}
