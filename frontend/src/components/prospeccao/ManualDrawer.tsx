"use client";

import { useEffect } from "react";
import { createPortal } from "react-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import Icon from "@/components/ui/Icon";
import { MANUAL_ESTEIRA } from "@/content/manual-esteira";

/**
 * Manual de uso, num painel lateral sobre a própria esteira.
 *
 * <p>Fica dentro da tela de propósito: a dúvida aparece com o quadro na frente, e mandar a pessoa
 * procurar um documento em outro lugar é onde a ajuda costuma morrer. O texto é o mesmo arquivo
 * versionado com o código, então ele acompanha a funcionalidade no deploy.</p>
 *
 * <p>O painel não tem estilo de tipografia pronto no projeto, então cada elemento do markdown é
 * mapeado à mão — é também o que garante contraste no claro e no escuro.</p>
 */
export default function ManualDrawer({
  onFechar,
  conteudo = MANUAL_ESTEIRA,
  rotulo = "Manual de uso da esteira",
}: {
  onFechar: () => void;
  /** Markdown do manual. A esteira de liberação usa o mesmo painel com o texto dela. */
  conteudo?: string;
  rotulo?: string;
}) {
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") onFechar();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onFechar]);

  // Portal para o body: dentro do <main> (relative z-0) o painel ficaria atrás da barra do topo,
  // escondendo o próprio botão de fechar.
  return createPortal(
    <div
      className="esteira-fade-in fixed inset-0 z-50 flex justify-end bg-slate-900/40 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label={rotulo}
      onClick={event => {
        if (event.target === event.currentTarget) onFechar();
      }}
    >
      <aside
        className="esteira-modal-in flex h-full w-full max-w-2xl flex-col border-l border-slate-200
          bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-800"
      >
        <header className="flex items-center justify-between gap-2 border-b border-slate-200 px-5 py-3
          dark:border-slate-700">
          <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-slate-800 dark:text-slate-100">
            <Icon name="help" className="text-[18px] text-[#612035] dark:text-[#D1732C]" />
            Manual de uso
          </h2>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar manual"
            className="cursor-pointer rounded p-1 text-slate-400 transition-colors hover:bg-slate-100
              hover:text-slate-600 focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
              dark:hover:bg-slate-700"
          >
            <Icon name="close" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          <ReactMarkdown
            // Sem o gfm as tabelas do manual sairiam como texto com barras verticais — e o manual
            // usa tabela justamente onde ela é mais clara: o que cada cor e cada status querem dizer.
            remarkPlugins={[remarkGfm]}
            components={{
              h1: ({ children }) => (
                <h1 className="mb-1 text-lg font-semibold text-slate-800 dark:text-slate-100">{children}</h1>
              ),
              h2: ({ children }) => (
                <h2 className="mb-2 mt-6 border-b border-slate-200 pb-1 text-sm font-semibold uppercase
                  tracking-wide text-[#612035] dark:border-slate-700 dark:text-[#D1732C]">
                  {children}
                </h2>
              ),
              p: ({ children }) => (
                <p className="mb-3 text-[13px] leading-relaxed text-slate-600 dark:text-slate-300">{children}</p>
              ),
              ul: ({ children }) => (
                <ul className="mb-3 list-disc space-y-1 pl-5 text-[13px] leading-relaxed text-slate-600
                  dark:text-slate-300">
                  {children}
                </ul>
              ),
              strong: ({ children }) => (
                <strong className="font-semibold text-slate-800 dark:text-slate-100">{children}</strong>
              ),
              code: ({ children }) => (
                <code className="rounded bg-slate-100 px-1 py-0.5 font-mono text-[11px] text-slate-700
                  dark:bg-slate-900 dark:text-slate-200">
                  {children}
                </code>
              ),
              a: ({ children, href }) => (
                <a href={href} className="text-[#2956E0] underline-offset-2 hover:underline dark:text-sky-400">
                  {children}
                </a>
              ),
              hr: () => <hr className="my-5 border-slate-200 dark:border-slate-700" />,
              table: ({ children }) => (
                <div className="mb-4 overflow-x-auto">
                  <table className="w-full border-collapse text-[12px]">{children}</table>
                </div>
              ),
              th: ({ children }) => (
                <th className="border-b border-slate-300 px-2 py-1.5 text-left font-semibold text-slate-700
                  dark:border-slate-600 dark:text-slate-200">
                  {children}
                </th>
              ),
              td: ({ children }) => (
                <td className="border-b border-slate-100 px-2 py-1.5 align-top text-slate-600
                  dark:border-slate-700 dark:text-slate-300">
                  {children}
                </td>
              ),
            }}
          >
            {conteudo}
          </ReactMarkdown>
        </div>
      </aside>
    </div>,
    document.body,
  );
}
