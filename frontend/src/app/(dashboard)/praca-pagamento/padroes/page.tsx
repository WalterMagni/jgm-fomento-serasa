"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { usePaymentPlacePatterns, useTogglePatternLock, useRecomputePatterns } from "../../../../hooks/usePaymentPlace";
import { PaymentPlacePattern } from "../../../../types/payment-place";

// Só vira "padrão" a partir da Nª decisão do mesmo contexto (espelha PaymentPlaceScorer.MIN_PATTERN_DECISIONS).
const MIN_PATTERN = 4;

function formatCnpj(cnpj?: string | null) {
  if (!cnpj) return "—";
  const d = cnpj.replace(/\D/g, "");
  if (d.length !== 14) return cnpj;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
}

function decisionLabel(d?: string | null) {
  if (d === "CEDENTE") return "Cedente";
  if (d === "SACADO") return "Sacado";
  return "—";
}

function DecisionBadge({ decision }: { decision?: string | null }) {
  if (!decision) return <span className="text-xs text-gray-400">Sem dominância</span>;
  const isCedente = decision === "CEDENTE";
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold ring-1 ${
        isCedente
          ? "bg-[#6120350f] text-primary ring-primary/20"
          : "bg-[#2956E00f] text-[#2956E0] ring-[#2956E0]/20"
      }`}
    >
      {decisionLabel(decision)}
    </span>
  );
}

export default function PadroesPage() {
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const size = 20;

  const { data, isFetching } = usePaymentPlacePatterns({ q: query || undefined, page, size });
  const toggleLock = useTogglePatternLock();
  const recompute = useRecomputePatterns();

  const patterns = data?.patterns ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="space-y-5 p-1">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-sans text-xl font-bold text-primary">Padrões aprendidos</h1>
          <p className="text-sm text-gray-500">
            Cada par cedente × sacado acumula as decisões já tomadas. O sistema usa esse histórico para
            reforçar a sugestão nas próximas importações — e aprende a cada nova decisão.
          </p>
        </div>
        <button
          onClick={() => recompute.mutate()}
          disabled={recompute.isPending}
          title="Recompila os padrões a partir das decisões atuais"
          className="inline-flex items-center gap-1.5 rounded-lg border border-border-light px-3 py-2 text-sm font-semibold text-gray-600 hover:bg-black/5 disabled:opacity-50 dark:border-border-dark dark:text-gray-300"
        >
          <Icon name="refresh" size={16} /> Recalcular
        </button>
      </div>

      {/* Resumo */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <StatCard label="Pares aprendidos" value={data?.totalPatterns ?? 0} icon="psychology" />
        <StatCard label="Pares travados" value={data?.lockedPatterns ?? 0} icon="lock" />
        <StatCard label="Nesta página" value={patterns.length} icon="format_list_bulleted" />
      </div>

      {/* Busca */}
      <div className="flex flex-wrap items-end gap-3 rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <label className="block flex-1 min-w-[220px]">
          <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Buscar por CNPJ (cedente ou sacado)</span>
          <input
            value={query}
            onChange={(e) => { setQuery(e.target.value); setPage(0); }}
            placeholder="Dígitos do CNPJ…"
            className="mt-1 w-full rounded-lg border border-border-light bg-white px-3 py-2 text-sm outline-none focus:border-primary dark:border-border-dark dark:bg-background-dark"
          />
        </label>
        {isFetching ? <span className="pb-2 text-xs text-gray-400">Carregando…</span> : null}
      </div>

      {/* Tabela */}
      <div className="overflow-x-auto rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <table className="w-full min-w-[760px] text-sm">
          <thead>
            <tr className="border-b border-border-light text-left text-[11px] font-bold uppercase tracking-wide text-gray-400 dark:border-border-dark">
              <th className="px-4 py-3">Cedente</th>
              <th className="px-4 py-3">Sacado</th>
              <th className="px-4 py-3">Banco / Agência</th>
              <th className="px-4 py-3 text-center">Padrão</th>
              <th className="px-4 py-3 text-center">Consistência</th>
              <th className="px-4 py-3 text-center">Decisões</th>
              <th className="px-4 py-3">Última</th>
              <th className="px-4 py-3 text-right">Travar</th>
            </tr>
          </thead>
          <tbody>
            {patterns.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-10 text-center text-sm text-gray-400">
                  Nenhum padrão ainda. Eles aparecem conforme você decide títulos entre as mesmas empresas.
                </td>
              </tr>
            ) : (
              patterns.map((p) => <PatternRow key={p.id} pattern={p} onToggle={toggleLock.mutate} busy={toggleLock.isPending} />)
            )}
          </tbody>
        </table>
      </div>

      {/* Paginação */}
      {totalPages > 1 ? (
        <div className="flex items-center justify-center gap-2">
          <button
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg border border-border-light px-3 py-1.5 text-sm disabled:opacity-40 dark:border-border-dark"
          >
            Anterior
          </button>
          <span className="text-sm text-gray-500">{page + 1} / {totalPages}</span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            className="rounded-lg border border-border-light px-3 py-1.5 text-sm disabled:opacity-40 dark:border-border-dark"
          >
            Próxima
          </button>
        </div>
      ) : null}
    </div>
  );
}

function StatCard({ label, value, icon }: { label: string; value: number; icon: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
      <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300">
        <Icon name={icon} size={20} />
      </span>
      <div>
        <p className="text-2xl font-bold text-grafite dark:text-white">{value}</p>
        <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      </div>
    </div>
  );
}

function PatternRow({
  pattern: p,
  onToggle,
  busy,
}: {
  pattern: PaymentPlacePattern;
  onToggle: (v: { patternId: string; locked: boolean; decision?: "SACADO" | "CEDENTE" }) => void;
  busy: boolean;
}) {
  return (
    <tr className="border-b border-border-light last:border-0 hover:bg-black/[0.015] dark:border-border-dark dark:hover:bg-white/[0.02]">
      <td className="px-4 py-3">
        <p className="font-semibold text-grafite dark:text-white">{p.clientName ?? "—"}</p>
        <p className="text-xs text-gray-400">{formatCnpj(p.clientDocument)}</p>
      </td>
      <td className="px-4 py-3">
        <p className="font-semibold text-grafite dark:text-white">{p.payerName ?? "—"}</p>
        <p className="text-xs text-gray-400">{formatCnpj(p.payerDocument)}</p>
      </td>
      <td className="px-4 py-3">
        <p className="font-semibold text-grafite dark:text-white">{p.bankName ?? p.bankCode ?? "—"}</p>
        <p className="text-xs text-gray-400">
          {p.bankCode ? `banco ${p.bankCode}` : "sem banco"}{p.agencyCode ? ` · ag ${p.agencyCode}` : ""}
        </p>
      </td>
      <td className="px-4 py-3 text-center">
        {p.locked ? (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-bold text-amber-700 ring-1 ring-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:ring-amber-500/30">
            <Icon name="lock" size={13} /> {decisionLabel(p.lockedDecision)}
          </span>
        ) : p.totalCount >= MIN_PATTERN ? (
          <DecisionBadge decision={p.dominantDecision} />
        ) : (
          <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-semibold text-gray-500 dark:bg-white/10 dark:text-gray-300">
            <Icon name="hourglass_empty" size={13} /> Aprendendo
          </span>
        )}
      </td>
      <td className="px-4 py-3 text-center">
        {p.totalCount >= MIN_PATTERN ? (
          <>
            {p.consistencyPct != null ? (
              <span className="text-sm font-bold text-grafite dark:text-white">{p.consistencyPct}%</span>
            ) : (
              <span className="text-xs text-gray-400">—</span>
            )}
            <p className="text-[11px] text-gray-400">{p.dominantCount}/{p.totalCount}</p>
          </>
        ) : (
          <p className="text-[11px] text-gray-400">{p.totalCount}/{MIN_PATTERN} p/ consolidar</p>
        )}
      </td>
      <td className="px-4 py-3 text-center text-xs text-gray-500 dark:text-gray-400">
        <span title="Cedente">C {p.cedenteCount}</span> · <span title="Sacado">S {p.sacadoCount}</span>
        {p.inconclusivoCount ? <> · <span title="Inconclusivo">I {p.inconclusivoCount}</span></> : null}
      </td>
      <td className="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">
        {decisionLabel(p.lastDecision)}<br />{formatDate(p.lastDecidedAt)}
      </td>
      <td className="px-4 py-3 text-right">
        {p.locked ? (
          <button
            disabled={busy}
            onClick={() => onToggle({ patternId: p.id, locked: false })}
            className="rounded-lg border border-border-light px-2.5 py-1 text-xs font-semibold text-gray-600 hover:bg-black/5 disabled:opacity-40 dark:border-border-dark dark:text-gray-300"
          >
            Destravar
          </button>
        ) : (
          <button
            disabled={busy || !p.dominantDecision || p.totalCount < MIN_PATTERN}
            title={p.totalCount < MIN_PATTERN ? `Ainda aprendendo (${p.totalCount}/${MIN_PATTERN}) — sem padrão para travar` : p.dominantDecision ? `Travar este contexto como ${decisionLabel(p.dominantDecision)}` : "Sem decisão dominante para travar"}
            onClick={() => p.dominantDecision && p.totalCount >= MIN_PATTERN && onToggle({ patternId: p.id, locked: true, decision: p.dominantDecision })}
            className="inline-flex items-center gap-1 rounded-lg border border-amber-200 px-2.5 py-1 text-xs font-semibold text-amber-700 hover:bg-amber-50 disabled:opacity-40 dark:border-amber-500/30 dark:text-amber-300"
          >
            <Icon name="lock" size={13} /> Travar
          </button>
        )}
      </td>
    </tr>
  );
}
