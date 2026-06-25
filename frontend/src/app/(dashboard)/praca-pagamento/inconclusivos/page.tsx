"use client";

import { useState } from "react";
import { usePaymentPlaceInconclusivos } from "../../../../hooks/usePaymentPlaceCompany";
import { useReopenPaymentPlaceEntry } from "../../../../hooks/usePaymentPlaceCompany";
import MonthDatePicker from "../../../../components/payment-place/MonthDatePicker";

function formatCnpj(cnpj?: string | null) {
  if (!cnpj) return "—";
  const d = cnpj.replace(/\D/g, "").padStart(14, "0");
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export default function InconclusivosPage() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const size = 20;

  const { data, isFetching } = usePaymentPlaceInconclusivos({ from: from || undefined, to: to || undefined, page, size });
  const reopen = useReopenPaymentPlaceEntry();

  const entries = data?.entries ?? [];
  const totalPages = data?.totalPages ?? 0;
  const total = data?.totalElements ?? 0;

  return (
    <div className="space-y-5 p-1">
      <div>
        <h1 className="font-sans text-xl font-bold text-primary">Análises inconclusivas</h1>
        <p className="text-sm text-gray-500">
          Lançamentos marcados como inconclusivos em qualquer lote. Restaure a qualquer momento para reanalisar.
        </p>
      </div>

      {/* Filtros */}
      <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <label className="block">
          <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">De (data da decisão)</span>
          <div className="mt-1 w-[160px]">
            <MonthDatePicker value={from || null} onChange={(v) => { setFrom(v ?? ""); setPage(0); }} />
          </div>
        </label>
        <label className="block">
          <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Até</span>
          <div className="mt-1 w-[160px]">
            <MonthDatePicker value={to || null} onChange={(v) => { setTo(v ?? ""); setPage(0); }} />
          </div>
        </label>
        {(from || to) ? (
          <button type="button" onClick={() => { setFrom(""); setTo(""); setPage(0); }}
            className="h-9 rounded-lg border border-border-light px-3 text-xs font-semibold text-gray-600 transition hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
            Limpar
          </button>
        ) : null}
        <span className="ml-auto text-xs text-gray-400">{total} inconclusivo(s)</span>
      </div>

      {/* Tabela */}
      <div className="overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border-light text-left text-[11px] font-bold uppercase tracking-wide text-gray-400 dark:border-border-dark">
              <th className="px-4 py-3">Título</th>
              <th className="px-4 py-3">Cedente</th>
              <th className="px-4 py-3">Sacado</th>
              <th className="px-4 py-3">Analista</th>
              <th className="px-4 py-3">Decisão em</th>
              <th className="px-4 py-3 text-right">Ação</th>
            </tr>
          </thead>
          <tbody>
            {isFetching && entries.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">Carregando…</td></tr>
            ) : entries.length === 0 ? (
              <tr><td colSpan={6} className="px-4 py-8 text-center text-gray-400">Nenhum lançamento inconclusivo no período.</td></tr>
            ) : (
              entries.map((e) => (
                <tr key={e.id} className="border-b border-border-light last:border-0 dark:border-border-dark">
                  <td className="px-4 py-3">
                    <p className="font-semibold text-grafite dark:text-gray-200">{e.titleNumber ?? "—"}</p>
                    <p className="text-xs text-gray-400">{e.section}</p>
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-grafite dark:text-gray-200">{e.clientName ?? <span className="text-gray-400">cód. {e.clientCode ?? "—"}</span>}</p>
                    {e.clientDocument ? <p className="text-xs text-gray-400">{formatCnpj(e.clientDocument)}</p> : null}
                  </td>
                  <td className="px-4 py-3">
                    <p className="text-grafite dark:text-gray-200">{e.payerName ?? "—"}</p>
                    {e.payerDocument ? <p className="text-xs text-gray-400">{formatCnpj(e.payerDocument)}</p> : null}
                  </td>
                  <td className="px-4 py-3 text-gray-600 dark:text-gray-300">{e.decidedByName ?? "—"}</td>
                  <td className="px-4 py-3 text-gray-600 dark:text-gray-300">{formatDateTime(e.decidedAt)}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      type="button"
                      onClick={() => reopen.mutate(e.id)}
                      disabled={reopen.isPending}
                      className="inline-flex items-center gap-1 rounded-md border border-border-light px-2.5 py-1 text-xs font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    >
                      <span className="material-icons-outlined text-[15px]">restore</span>
                      Restaurar
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Paginação */}
      {totalPages > 1 ? (
        <div className="flex items-center justify-center gap-3">
          <button type="button" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md border border-border-light px-3 py-1 text-xs font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
            Anterior
          </button>
          <span className="text-xs text-gray-500">Página {page + 1} de {totalPages}</span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            className="rounded-md border border-border-light px-3 py-1 text-xs font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
            Próxima
          </button>
        </div>
      ) : null}
    </div>
  );
}
