"use client";

import { useEffect, useState, type CSSProperties, type ReactNode } from "react";
import Icon from "@/components/ui/Icon";

/** Conta de 0 até o alvo com desaceleração. Quem pediu menos movimento vê o número direto. */
function useContagem(alvo: number, duracao = 1100) {
  const [valor, setValor] = useState(0);
  useEffect(() => {
    const semMovimento = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const total = semMovimento ? 0 : duracao;
    const inicio = performance.now();
    let quadro = 0;
    const passo = (agora: number) => {
      const t = total === 0 ? 1 : Math.min(1, (agora - inicio) / total);
      setValor(Math.round(alvo * (1 - Math.pow(1 - t, 3))));
      if (t < 1) quadro = requestAnimationFrame(passo);
    };
    quadro = requestAnimationFrame(passo);
    return () => cancelAnimationFrame(quadro);
  }, [alvo, duracao]);
  return valor;
}

/** Cartão de indicador: ícone e rótulo, número grande que conta, um visual e uma linha de apoio. */
export default function KpiCard({
  icone,
  cor,
  rotulo,
  valor,
  rodape,
  atraso = 0,
  children,
}: {
  icone: string;
  cor: string;
  rotulo: string;
  valor: number;
  rodape?: ReactNode;
  /** Atraso da entrada, em ms, para os cartões chegarem em sequência. */
  atraso?: number;
  children?: ReactNode;
}) {
  const numero = useContagem(valor);
  return (
    <div
      className="flex flex-col gap-2.5 overflow-hidden rounded-cartao border border-linha bg-white p-5 transition-[transform,box-shadow] duration-300 ease-saida hover:-translate-y-0.5 hover:shadow-elevado animate-subir motion-reduce:animate-none motion-reduce:hover:translate-y-0"
      style={{ animationDelay: `${atraso}ms` }}
    >
      <p className="flex items-center gap-2 text-[12.5px] font-semibold text-suave">
        <Icon name={icone} size={20} style={{ color: cor }} />
        {rotulo}
      </p>
      <p className="font-display text-[40px] font-semibold leading-none tracking-[-0.02em] text-grafite">
        {numero.toLocaleString("pt-BR")}
      </p>
      {children}
      {rodape && <p className="text-[12.5px] text-suave">{rodape}</p>}
    </div>
  );
}

/** Barra fina de proporção que cresce da esquerda. */
export function Medidor({ fracao, cor }: { fracao: number; cor: string }) {
  return (
    <div className="h-[7px] overflow-hidden rounded-full bg-areia">
      <i
        className="block h-full origin-left rounded-full animate-crescer-x motion-reduce:animate-none"
        style={{ width: `${Math.round(fracao * 100)}%`, background: cor, animationDelay: "250ms" }}
      />
    </div>
  );
}

/** Barrinhas por período, a última em destaque. */
export function MiniBarras({ valores, rotulo }: { valores: number[]; rotulo: string }) {
  const maximo = Math.max(1, ...valores);
  return (
    <div className="flex h-[34px] items-end gap-[3px]" role="img" aria-label={rotulo}>
      {valores.map((v, i) => {
        const ultima = i === valores.length - 1;
        const estilo: CSSProperties = {
          height: `${Math.max(8, (v / maximo) * 100)}%`,
          animationDelay: `${300 + i * 40}ms`,
        };
        return (
          <span
            key={i}
            className={`flex-1 origin-bottom rounded-t-[3px] rounded-b-[1px] animate-crescer-y motion-reduce:animate-none ${
              ultima ? "bg-primary" : "bg-primary/[0.17]"
            }`}
            style={estilo}
          />
        );
      })}
    </div>
  );
}
