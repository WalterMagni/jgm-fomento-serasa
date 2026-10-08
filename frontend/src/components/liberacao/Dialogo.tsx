"use client";

import { useEffect, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import Icon from "@/components/ui/Icon";

type Props = {
  titulo: ReactNode;
  subtitulo?: ReactNode;
  onFechar: () => void;
  children: ReactNode;
  rodape?: ReactNode;
  largura?: "sm" | "md" | "lg" | "xl";
  /** Rótulo acessível quando o título não é texto simples. */
  rotulo?: string;
};

const LARGURA = { sm: "max-w-md", md: "max-w-xl", lg: "max-w-3xl", xl: "max-w-6xl" };

/**
 * Casca dos diálogos da esteira: fundo, Esc, foco.
 *
 * <p>Vai por portal para o body: o {@code <main>} do layout cria contexto de empilhamento
 * ({@code relative z-0}), e um diálogo renderizado dentro dele ficaria atrás do menu lateral
 * por mais alto que fosse o z-index.</p>
 *
 * <p>O foco entra no diálogo ao abrir e volta para quem o abriu ao fechar — sem isso, quem usa
 * teclado fecharia o card e cairia no topo da página.</p>
 */
export default function Dialogo({ titulo, subtitulo, onFechar, children, rodape, largura = "md", rotulo }: Props) {
  const painel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const anterior = document.activeElement as HTMLElement | null;
    const primeiro =
      painel.current?.querySelector<HTMLElement>("[data-autofocus]") ??
      painel.current?.querySelector<HTMLElement>("input, textarea, select, button");
    primeiro?.focus();
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.stopPropagation();
        onFechar();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => {
      window.removeEventListener("keydown", onKey);
      anterior?.focus?.();
    };
  }, [onFechar]);

  return createPortal(
    <div
      // z-50 é o topo da escala do projeto (10 fundo de menu, 20 menu suspenso, 50 diálogo).
      className="esteira-fade-in fixed inset-0 z-50 flex items-end justify-center bg-slate-950/45 backdrop-blur-[2px] sm:items-center sm:p-4"
      onMouseDown={event => {
        if (event.target === event.currentTarget) onFechar();
      }}
    >
      <div
        ref={painel}
        role="dialog"
        aria-modal="true"
        aria-label={rotulo ?? (typeof titulo === "string" ? titulo : undefined)}
        className={`esteira-modal-in flex max-h-[92vh] w-full ${LARGURA[largura]} flex-col overflow-hidden rounded-t-2xl bg-white
          shadow-2xl sm:rounded-2xl dark:bg-slate-900`}
      >
        <header className="flex items-start justify-between gap-3 border-b border-slate-100 px-5 py-4 dark:border-slate-800">
          <div className="min-w-0">
            <h2 className="font-display text-lg font-semibold text-slate-900 dark:text-white">{titulo}</h2>
            {subtitulo && <div className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{subtitulo}</div>}
          </div>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar"
            className="inline-flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-lg text-slate-400 transition-colors
              hover:bg-slate-100 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:hover:bg-slate-800 dark:hover:text-slate-200"
          >
            <Icon name="close" size={18} />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
        {rodape && (
          <footer className="flex flex-wrap items-center justify-end gap-2 border-t border-slate-100 bg-slate-50/70 px-5 py-3
            dark:border-slate-800 dark:bg-slate-900">
            {rodape}
          </footer>
        )}
      </div>
    </div>,
    document.body,
  );
}

/** Botões padrão dos diálogos. */
export const BOTAO_PRIMARIO =
  "inline-flex min-h-10 cursor-pointer items-center justify-center gap-1.5 rounded-lg bg-[#612035] px-4 text-sm font-medium text-white shadow-sm " +
  "transition-colors hover:bg-[#4e1a2a] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] focus-visible:ring-offset-2 " +
  "disabled:cursor-not-allowed disabled:opacity-50 dark:focus-visible:ring-offset-slate-900";

export const BOTAO_SECUNDARIO =
  "inline-flex min-h-10 cursor-pointer items-center justify-center gap-1.5 rounded-lg border border-slate-200 bg-white px-4 text-sm font-medium " +
  "text-slate-700 transition-colors hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] " +
  "disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700";

export const CAMPO =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 transition-colors " +
  "focus:border-[#612035] focus:outline-none focus:ring-2 focus:ring-[#612035]/20 disabled:bg-slate-50 disabled:text-slate-500 " +
  "dark:border-slate-700 dark:bg-slate-800 dark:text-white dark:focus:border-[#e8a3b6] dark:focus:ring-[#e8a3b6]/20";

export const ROTULO = "mb-1 block text-xs font-medium text-slate-600 dark:text-slate-300";
