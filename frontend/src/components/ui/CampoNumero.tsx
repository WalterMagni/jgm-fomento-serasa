"use client";

import { useLayoutEffect, useRef, type InputHTMLAttributes } from "react";

type Opcoes = {
  /** Máximo de casas depois da vírgula: 2 em reais, 4 na taxa. */
  casas: number;
  /** Pontos de milhar. A taxa não tem: lá o ponto digitado é vírgula. */
  milhar: boolean;
};

/**
 * Pontua o número enquanto se digita: "300000000" vira "300.000.000" e "1234,5" vira "1.234,5".
 * A vírgula é de quem digita; os pontos são da tela. Zeros à esquerda caem.
 */
export function pontuar(texto: string, { casas, milhar }: Opcoes): string {
  const comVirgula = milhar ? texto : texto.replace(/\./g, ",");
  const limpo = comVirgula.replace(/[^\d,]/g, "");
  const virgula = limpo.indexOf(",");
  let inteiro = virgula < 0 ? limpo : limpo.slice(0, virgula);
  const decimais = virgula < 0 ? "" : limpo.slice(virgula + 1).replace(/,/g, "").slice(0, casas);
  inteiro = inteiro.replace(/^0+(?=\d)/, "");
  if (virgula >= 0 && inteiro === "") inteiro = "0";
  const agrupado = milhar ? inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, ".") : inteiro;
  return virgula >= 0 && casas > 0 ? `${agrupado},${decimais}` : agrupado;
}

/** Quantos dígitos e vírgulas há no texto: é o que o cursor conta, já que os pontos mudam de lugar. */
function significativos(texto: string) {
  return texto.replace(/[^\d,]/g, "").length;
}

/** Posição logo depois do n-ésimo dígito ou vírgula. */
function posicaoDepois(texto: string, n: number) {
  if (n <= 0) return 0;
  let vistos = 0;
  for (let i = 0; i < texto.length; i++) {
    if (/[\d,]/.test(texto[i]) && ++vistos === n) return i + 1;
  }
  return texto.length;
}

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "inputMode"> &
  Partial<Opcoes> & {
    valor: string;
    onMudar: (texto: string) => void;
  };

/**
 * Campo de valor em reais ou de taxa que pontua enquanto se digita, sem pular o cursor para o fim.
 *
 * <p>Quem sai do campo continua formatando como antes (duas casas, por exemplo): aqui só entram os
 * pontos de milhar e o limite de casas. Em reais, ponto digitado é ignorado — quem escreve
 * "1.000" à mão chega no mesmo lugar, e o numérico do teclado ABNT2 já solta vírgula.</p>
 */
export default function CampoNumero({ valor, onMudar, casas = 2, milhar = true, ...resto }: Props) {
  const campo = useRef<HTMLInputElement>(null);
  const cursor = useRef<number | null>(null);

  useLayoutEffect(() => {
    const el = campo.current;
    if (cursor.current == null || !el || document.activeElement !== el) return;
    el.setSelectionRange(cursor.current, cursor.current);
    cursor.current = null;
  });

  return (
    <input
      {...resto}
      ref={campo}
      inputMode="decimal"
      value={valor}
      onChange={event => {
        // Sem milhar (taxa), todo ponto é vírgula: troca antes de contar onde o cursor está.
        const bruto = milhar ? event.target.value : event.target.value.replace(/\./g, ",");
        const pos = event.target.selectionStart ?? bruto.length;
        const novo = pontuar(bruto, { casas, milhar });
        cursor.current = posicaoDepois(novo, significativos(bruto.slice(0, pos)));
        onMudar(novo);
      }}
    />
  );
}
