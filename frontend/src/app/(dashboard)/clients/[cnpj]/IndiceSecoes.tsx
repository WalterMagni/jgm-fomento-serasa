"use client";

import { useEffect, useState } from "react";

export type SecaoIndice = { id: string; rotulo: string };

/** Leva a uma seção da página: rola o conteúdo até ela, descontando o índice fixo. */
export function irParaSecao(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

/**
 * Índice fixo da Visão 360º: um clique leva à seção e o item da seção visível fica marcado.
 * As seções precisam de `scroll-mt-*` para não ficarem embaixo do próprio índice.
 */
export default function IndiceSecoes({ secoes }: { secoes: SecaoIndice[] }) {
  const [ativa, setAtiva] = useState(secoes[0]?.id ?? "");
  const chave = secoes.map(s => s.id).join("|");

  useEffect(() => {
    const ids = chave.split("|").filter(Boolean);
    // Marcada = a última seção cujo topo já passou da linha do índice: é a que está sendo lida.
    // "A primeira visível" errava quando o fim da seção anterior ainda aparecia no alto da tela.
    const LINHA = 180;
    let quadro = 0;
    const medir = () => {
      quadro = 0;
      let atual = ids[0];
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el && el.getBoundingClientRect().top <= LINHA) atual = id;
      }
      if (atual) setAtiva(atual);
    };
    const aoRolar = () => {
      if (!quadro) quadro = requestAnimationFrame(medir);
    };
    // O conteúdo rola dentro do <main>, não na janela: escuta em captura para pegar qualquer rolagem.
    document.addEventListener("scroll", aoRolar, true);
    quadro = requestAnimationFrame(medir);
    return () => {
      document.removeEventListener("scroll", aoRolar, true);
      cancelAnimationFrame(quadro);
    };
  }, [chave]);

  return (
    <nav
      aria-label="Seções da página"
      // top negativo = padding do <main>: o sticky para na borda do conteúdo, não do topo.
      // Fundo opaco: o Street View escuro passaria por baixo de um fundo translúcido.
      className="sticky -top-5 z-10 -mx-5 mb-6 border-b border-linha bg-papel px-5 py-2.5 lg:-top-8 lg:-mx-8 lg:px-8 print:hidden"
    >
      <ul className="flex gap-1 overflow-x-auto">
        {secoes.map(s => (
          <li key={s.id} className="shrink-0">
            <a
              href={`#${s.id}`}
              onClick={e => {
                e.preventDefault();
                setAtiva(s.id);
                irParaSecao(s.id);
              }}
              aria-current={ativa === s.id ? "location" : undefined}
              className={`inline-flex h-9 items-center rounded-full px-3.5 text-[13px] font-semibold transition-colors ${
                ativa === s.id ? "bg-grafite text-white" : "text-[#5E5C55] hover:bg-white hover:text-grafite"
              }`}
            >
              {s.rotulo}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
