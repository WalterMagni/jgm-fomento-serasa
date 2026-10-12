"use client";

import { useRef, useState, type ReactNode } from "react";

/**
 * Balão de explicação no hover ou no foco. Abre para cima quando não cabe embaixo.
 * A posição é calculada ao abrir, não num efeito, para não renderizar duas vezes.
 */
export default function Dica({ conteudo, children }: { conteudo: ReactNode; children: ReactNode }) {
  const ancora = useRef<HTMLSpanElement>(null);
  const [aberta, setAberta] = useState(false);
  const [acima, setAcima] = useState(false);

  const abrir = () => {
    const caixa = ancora.current?.getBoundingClientRect();
    setAcima(!!caixa && caixa.bottom + 180 > window.innerHeight);
    setAberta(true);
  };

  return (
    <span
      ref={ancora}
      className="relative inline-flex items-center"
      onMouseEnter={abrir}
      onMouseLeave={() => setAberta(false)}
      onFocus={abrir}
      onBlur={() => setAberta(false)}
    >
      {children}
      {aberta && (
        <span
          role="tooltip"
          className={`pointer-events-none absolute left-1/2 z-50 w-64 -translate-x-1/2 rounded-xl bg-grafite p-3 text-xs leading-relaxed text-white shadow-xl animate-surgir motion-reduce:animate-none ${
            acima ? "bottom-full mb-2" : "top-full mt-2"
          }`}
        >
          {conteudo}
        </span>
      )}
    </span>
  );
}
