"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

/**
 * Painel que desliza da direita sobre a página, para ver o detalhe sem perder a lista.
 * Fecha pelo fundo escurecido ou pelo Esc. `onFechar` precisa ser estável (useCallback):
 * o foco vai para o painel sempre que ele muda.
 */
export default function Gaveta({
  rotulo,
  onFechar,
  rodape,
  larga = false,
  children,
}: {
  /** Nome do diálogo para o leitor de tela. */
  rotulo: string;
  onFechar: () => void;
  rodape?: ReactNode;
  /** Para leitura longa (o detalhe de um processo): 760px em vez de 470px. */
  larga?: boolean;
  children: ReactNode;
}) {
  const painel = useRef<HTMLElement>(null);

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === "Escape") onFechar();
    };
    window.addEventListener("keydown", aoTeclar);
    painel.current?.focus();
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [onFechar]);

  // Portal no body: o <main> do layout cria contexto de empilhamento (z-0), e dentro dele a
  // gaveta ficaria por baixo do topo e da sidebar.
  return createPortal(
    <div className="fixed inset-0 z-40 print:hidden">
      <button
        type="button"
        aria-label="Fechar"
        onClick={onFechar}
        className="absolute inset-0 h-full w-full cursor-default bg-grafite/35 backdrop-blur-[2px] animate-surgir motion-reduce:animate-none"
      />
      <aside
        ref={painel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={rotulo}
        className={`absolute inset-y-0 right-0 flex w-full ${larga ? "max-w-[760px]" : "max-w-[470px]"} flex-col bg-papel shadow-gaveta outline-none animate-deslizar motion-reduce:animate-none`}
      >
        <div className="flex flex-1 flex-col gap-6 overflow-y-auto px-7 py-6">{children}</div>
        {rodape && <div className="flex flex-wrap gap-2.5 border-t border-linha bg-white px-7 py-4">{rodape}</div>}
      </aside>
    </div>,
    document.body,
  );
}
