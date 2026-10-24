"use client";

import Icon from "@/components/ui/Icon";
import type { Notificacao } from "@/types/notificacao";
import { VISUAL } from "./visual";

const LINHAS = 3;

/**
 * Uma linha por destino: o que aponta para o mesmo card (ou processo) junta, na ordem da mais nova.
 * O título não separa a empresa, então a linha mostra o mais novo e conta o resto.
 */
function porDestino(itens: Notificacao[]) {
  const grupos = new Map<string, Notificacao[]>();
  for (const item of itens) grupos.set(item.link, [...(grupos.get(item.link) ?? []), item]);
  return [...grupos.values()];
}

/**
 * Aviso de rajada: várias notificações num toast só, as mais novas em cima, agrupadas por card.
 * Cada linha leva ao card; "Ver todas" abre o sino com a lista inteira.
 */
export default function ToastResumo({
  itens,
  onAbrir,
  onVerTodas,
  onFechar,
}: {
  itens: Notificacao[];
  onAbrir: (notificacao: Notificacao) => void;
  onVerTodas: () => void;
  onFechar: () => void;
}) {
  const grupos = porDestino(itens);
  const resto = grupos.slice(LINHAS).reduce((total, grupo) => total + grupo.length, 0);
  return (
    <div
      role="status"
      className="w-[min(380px,calc(100vw-32px))] overflow-hidden rounded-xl border border-[#612035]/25 bg-white shadow-[0_18px_40px_-18px_rgba(97,32,53,0.55)] dark:border-[#612035]/60 dark:bg-[#1a1c23]"
    >
      <div className="h-1 bg-[#612035]" aria-hidden />
      <div className="p-4">
        <div className="flex items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#612035]/10 text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]">
            <Icon name="notifications" size={18} />
          </span>
          <p className="text-sm font-semibold text-grafite dark:text-white">{itens.length} novas notificações</p>
        </div>
        <ul className="mt-2.5 space-y-0.5">
          {grupos.slice(0, LINHAS).map(grupo => {
            const [notificacao] = grupo;
            const visual = VISUAL[notificacao.tipo] ?? VISUAL.DECISAO;
            return (
              <li key={notificacao.link}>
                <button
                  type="button"
                  onClick={() => onAbrir(notificacao)}
                  title={grupo.map(item => item.titulo).join("\n")}
                  className="flex w-full cursor-pointer items-center gap-2.5 rounded-lg px-1.5 py-1.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-800/70"
                >
                  <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${visual.cor}`}>
                    <Icon name={visual.icone} size={13} />
                  </span>
                  <span className="min-w-0 flex-1 truncate text-[13px] text-slate-700 dark:text-slate-200">{notificacao.titulo}</span>
                  {grupo.length > 1 && (
                    <span
                      className="shrink-0 rounded-full bg-[#612035]/10 px-1.5 text-[11px] font-bold leading-[18px] text-[#612035] dark:bg-[#612035]/40 dark:text-[#f2c4d1]"
                      aria-label={`e mais ${grupo.length - 1} neste card`}
                    >
                      +{grupo.length - 1}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </ul>
        {resto > 0 && (
          <p className="mt-0.5 pl-[42px] text-xs text-slate-400">
            e mais {resto} {resto === 1 ? "outra" : "outras"}
          </p>
        )}
        <div className="mt-3 flex items-center gap-2">
          <button
            type="button"
            onClick={onVerTodas}
            className="inline-flex h-8 cursor-pointer items-center gap-1.5 rounded-lg bg-[#612035] px-3 text-xs font-semibold text-white transition-colors hover:bg-[#4e1a2b]"
          >
            <Icon name="notifications" size={13} /> Ver todas
          </button>
          <button
            type="button"
            onClick={onFechar}
            className="h-8 cursor-pointer rounded-lg px-2 text-xs font-semibold text-gray-500 hover:text-grafite dark:hover:text-white"
          >
            Fechar
          </button>
        </div>
      </div>
    </div>
  );
}
