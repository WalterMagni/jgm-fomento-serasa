"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useBackfillVisaoCedente, usePreviaBackfill } from "@/hooks/useProspeccao";

const LIMITES = [10, 25, 50, 100];

/**
 * Confirmação do "puxar visão cedente".
 *
 * <p>A ação é de mão única: cada análise com visão cedente SIM vira um card em triagem, e não há
 * desfazer em massa. Em produção são centenas, então a tela mostra quantas entrariam antes de
 * agir, deixa recortar por data e limita o tamanho do lote.</p>
 */
export default function BackfillDialog({ onFechar }: { onFechar: () => void }) {
  const [desde, setDesde] = useState("");
  const [limite, setLimite] = useState(25);
  const { data: previa, isLoading, error } = usePreviaBackfill(desde || undefined, true);
  const backfill = useBackfillVisaoCedente();

  const entrarao = previa ? Math.min(previa.seriamCriados, limite) : 0;

  return (
    <div
      className="esteira-fade-in fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4
        backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Puxar visão cedente"
      onClick={event => {
        if (event.target === event.currentTarget) onFechar();
      }}
    >
      <div className="esteira-modal-in w-full max-w-md rounded-xl border border-slate-200 bg-white p-5
        shadow-2xl dark:border-slate-700 dark:bg-slate-800">
        <div className="mb-3 flex items-start justify-between gap-2">
          <div>
            <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">Puxar visão cedente</h2>
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              Traz para a triagem empresas cuja análise indicou perfil de cedente.
            </p>
          </div>
          <button
            type="button"
            onClick={onFechar}
            aria-label="Fechar"
            className="shrink-0 cursor-pointer rounded p-1 text-slate-400 transition-colors
              hover:bg-slate-100 hover:text-slate-600 focus:outline-none focus-visible:ring-2
              focus-visible:ring-[#612035] dark:hover:bg-slate-700"
          >
            <Icon name="close" />
          </button>
        </div>

        {isLoading && <p className="py-4 text-center text-xs text-slate-500">Contando…</p>}
        {error && <p className="py-4 text-center text-xs text-red-600">{error.message}</p>}

        {previa && (
          <>
            <dl className="mb-3 grid grid-cols-3 gap-2 rounded-lg bg-slate-50 p-3 text-center dark:bg-slate-900">
              <div>
                <dt className="text-[10px] uppercase tracking-wide text-slate-400">com visão cedente</dt>
                <dd className="text-lg font-semibold text-slate-700 dark:text-slate-200">
                  {previa.comVisaoCedente}
                </dd>
              </div>
              <div>
                <dt className="text-[10px] uppercase tracking-wide text-slate-400">já na esteira</dt>
                <dd className="text-lg font-semibold text-slate-500">{previa.jaNaEsteira}</dd>
              </div>
              <div>
                <dt className="text-[10px] uppercase tracking-wide text-slate-400">entrariam</dt>
                <dd className="text-lg font-semibold text-[#612035] dark:text-[#D1732C]">
                  {previa.seriamCriados}
                </dd>
              </div>
            </dl>

            {previa.amostra.length > 0 && (
              <p className="mb-3 text-[11px] text-slate-500 dark:text-slate-400">
                Por exemplo: {previa.amostra.slice(0, 3).join(", ")}
                {previa.seriamCriados > 3 && ` e mais ${previa.seriamCriados - 3}`}
              </p>
            )}

            <label className="block text-xs font-medium text-slate-600 dark:text-slate-300">
              Só análises a partir de
              <input
                type="date"
                value={desde}
                onChange={event => setDesde(event.target.value)}
                className="mt-1 w-full cursor-pointer rounded border border-slate-300 px-2 py-1.5 text-sm
                  focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
                  dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
              />
            </label>
            <p className="mt-1 text-[10px] text-slate-400">Em branco traz o histórico inteiro.</p>

            <fieldset className="mt-3">
              <legend className="text-xs font-medium text-slate-600 dark:text-slate-300">
                No máximo, por vez
              </legend>
              <div className="mt-1 flex gap-1">
                {LIMITES.map(opcao => (
                  <button
                    key={opcao}
                    type="button"
                    onClick={() => setLimite(opcao)}
                    className={`cursor-pointer rounded px-2.5 py-1 text-xs transition-colors
                      focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                        limite === opcao
                          ? "bg-[#612035] text-white"
                          : "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-700 dark:text-slate-300"
                      }`}
                  >
                    {opcao}
                  </button>
                ))}
              </div>
            </fieldset>

            <div className="mt-4 flex items-center justify-between gap-2">
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                {entrarao === 0
                  ? "Nada novo para trazer."
                  : `Vai criar ${entrarao} card${entrarao > 1 ? "s" : ""} em triagem.`}
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={onFechar}
                  className="cursor-pointer rounded px-3 py-1.5 text-xs text-slate-600 transition-colors
                    hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700"
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  disabled={entrarao === 0 || backfill.isPending}
                  onClick={() =>
                    backfill.mutate({ desde: desde || undefined, limite }, { onSuccess: onFechar })
                  }
                  className="cursor-pointer rounded bg-[#612035] px-3 py-1.5 text-xs text-white
                    transition-colors hover:bg-[#4d1a2a] focus:outline-none focus-visible:ring-2
                    focus-visible:ring-[#612035] focus-visible:ring-offset-1
                    disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {backfill.isPending ? "trazendo…" : "Trazer"}
                </button>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
