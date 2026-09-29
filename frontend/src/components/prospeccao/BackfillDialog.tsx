"use client";

import { useMemo, useState } from "react";
import Icon from "@/components/ui/Icon";
import {
  useBackfillVisaoCedente,
  useCandidatasBackfill,
  usePreviaBackfill,
  useTrazerEscolhidas,
} from "@/hooks/useProspeccao";
import { formatCnpj, formatDate } from "./formatters";

const LIMITES = [10, 25, 50, 100];

/**
 * "Puxar visão cedente": escolher empresas, ou trazer um lote.
 *
 * <p>A ação é de mão única — cada empresa vira um card em triagem e não há desfazer em massa. Em
 * produção são centenas de análises com visão cedente, então o caminho normal é buscar a empresa
 * pelo nome ou CNPJ e marcar o que se quer. O lote continua disponível para a carga inicial, com
 * teto e recorte por data.</p>
 */
export default function BackfillDialog({ onFechar }: { onFechar: () => void }) {
  const [aba, setAba] = useState<"escolher" | "lote">("escolher");
  const [busca, setBusca] = useState("");
  const [desde, setDesde] = useState("");
  const [limite, setLimite] = useState(25);
  const [marcados, setMarcados] = useState<Set<string>>(new Set());

  const { data: previa } = usePreviaBackfill(desde || undefined, true);
  const { data: candidatas, isLoading, error } = useCandidatasBackfill(busca, desde || undefined, true);
  const trazer = useTrazerEscolhidas();
  const backfill = useBackfillVisaoCedente();

  const itens = useMemo(() => candidatas?.itens ?? [], [candidatas]);
  const todosMarcados = itens.length > 0 && itens.every(item => marcados.has(item.cnpj));

  function alternar(cnpj: string) {
    setMarcados(atual => {
      const proximo = new Set(atual);
      if (proximo.has(cnpj)) {
        proximo.delete(cnpj);
      } else {
        proximo.add(cnpj);
      }
      return proximo;
    });
  }

  function alternarTodos() {
    setMarcados(atual => {
      if (todosMarcados) {
        const proximo = new Set(atual);
        itens.forEach(item => proximo.delete(item.cnpj));
        return proximo;
      }
      return new Set([...atual, ...itens.map(item => item.cnpj)]);
    });
  }

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
      <div className="esteira-modal-in flex max-h-[85vh] w-full max-w-lg flex-col rounded-xl border
        border-slate-200 bg-white shadow-2xl dark:border-slate-700 dark:bg-slate-800">
        <header className="flex items-start justify-between gap-2 border-b border-slate-200 p-4
          dark:border-slate-700">
          <div>
            <h2 className="text-base font-semibold text-slate-800 dark:text-slate-100">Puxar visão cedente</h2>
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              Empresas cuja análise indicou perfil de cedente. Cada uma vira um card em triagem.
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
        </header>

        <nav className="flex gap-4 border-b border-slate-200 px-4 dark:border-slate-700">
          {(
            [
              ["escolher", "Escolher empresas"],
              ["lote", "Trazer um lote"],
            ] as const
          ).map(([valor, rotulo]) => (
            <button
              key={valor}
              type="button"
              onClick={() => setAba(valor)}
              className={`-mb-px cursor-pointer border-b-2 px-1 py-2 text-xs font-medium transition-colors
                focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035] ${
                  aba === valor
                    ? "border-[#612035] text-[#612035] dark:border-[#D1732C] dark:text-[#D1732C]"
                    : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400"
                }`}
            >
              {rotulo}
            </button>
          ))}
        </nav>

        {aba === "escolher" ? (
          <>
            <div className="border-b border-slate-200 p-3 dark:border-slate-700">
              <div className="relative">
                <Icon
                  name="search"
                  className="pointer-events-none absolute left-2 top-1/2 -translate-y-1/2 text-[16px] text-slate-400"
                />
                <input
                  autoFocus
                  type="search"
                  value={busca}
                  onChange={event => setBusca(event.target.value)}
                  placeholder="Buscar por razão social ou CNPJ…"
                  aria-label="Buscar empresa por razão social ou CNPJ"
                  className="w-full rounded-md border border-slate-300 py-1.5 pl-8 pr-2 text-xs
                    focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]
                    dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
                />
              </div>
              {candidatas && (
                <p className="mt-1.5 flex items-center justify-between text-[11px] text-slate-400">
                  <span>
                    {candidatas.total === 0
                      ? "nenhuma empresa"
                      : `${candidatas.total} empresa${candidatas.total > 1 ? "s" : ""} fora da esteira`}
                    {candidatas.total > candidatas.exibidas && ` · mostrando ${candidatas.exibidas}`}
                  </span>
                  {itens.length > 0 && (
                    <button
                      type="button"
                      onClick={alternarTodos}
                      className="cursor-pointer rounded px-1 text-slate-500 underline-offset-2 hover:underline
                        focus:outline-none focus-visible:ring-2 focus-visible:ring-[#612035]"
                    >
                      {todosMarcados ? "desmarcar" : "marcar as visíveis"}
                    </button>
                  )}
                </p>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {isLoading && <p className="py-6 text-center text-xs text-slate-500">Buscando…</p>}
              {error && <p className="py-6 text-center text-xs text-red-600">{error.message}</p>}
              {candidatas && itens.length === 0 && (
                <div className="flex flex-col items-center gap-1 py-8">
                  <Icon name="search_off" className="text-[24px] text-slate-300" />
                  <p className="text-xs text-slate-500">
                    {busca.trim() ? "Nenhuma empresa com esse termo." : "Nada novo para trazer."}
                  </p>
                </div>
              )}
              <ul className="space-y-0.5">
                {itens.map(item => (
                  <li key={item.cnpj}>
                    <label
                      className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 transition-colors
                        hover:bg-slate-50 dark:hover:bg-slate-700"
                    >
                      <input
                        type="checkbox"
                        checked={marcados.has(item.cnpj)}
                        onChange={() => alternar(item.cnpj)}
                        className="cursor-pointer accent-[#612035]"
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-xs text-slate-700 dark:text-slate-200">
                          {item.nome}
                        </span>
                        <span className="block font-mono text-[10px] text-slate-400">
                          {formatCnpj(item.cnpj)}
                          {item.consultaEm && ` · analisada em ${formatDate(item.consultaEm)}`}
                        </span>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </div>

            <footer className="flex items-center justify-between gap-2 border-t border-slate-200 p-3
              dark:border-slate-700">
              <p className="text-[11px] text-slate-500 dark:text-slate-400">
                {marcados.size === 0
                  ? "Marque as empresas que quer trazer."
                  : `${marcados.size} marcada${marcados.size > 1 ? "s" : ""}.`}
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
                  disabled={marcados.size === 0 || trazer.isPending}
                  onClick={() =>
                    trazer.mutate([...marcados], {
                      onSuccess: () => {
                        setMarcados(new Set());
                        onFechar();
                      },
                    })
                  }
                  className="cursor-pointer rounded bg-[#612035] px-3 py-1.5 text-xs text-white
                    transition-colors hover:bg-[#4d1a2a] focus:outline-none focus-visible:ring-2
                    focus-visible:ring-[#612035] focus-visible:ring-offset-1
                    disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {trazer.isPending ? "trazendo…" : "Trazer marcadas"}
                </button>
              </div>
            </footer>
          </>
        ) : (
          <div className="p-4">
            {previa && (
              <dl className="mb-3 grid grid-cols-3 gap-2 rounded-lg bg-slate-50 p-3 text-center
                dark:bg-slate-900">
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
            <p className="mt-1 text-[10px] text-slate-400">Em branco considera o histórico inteiro.</p>

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
                  {backfill.isPending ? "trazendo…" : "Trazer lote"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
