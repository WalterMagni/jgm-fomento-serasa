"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { usePaymentPlaceHistory } from "../../../../hooks/usePaymentPlaceCompany";
import { PaymentPlaceEntry } from "../../../../types/payment-place";

function formatCnpj(cnpj?: string | null) {
  if (!cnpj) return "—";
  const d = cnpj.replace(/\D/g, "");
  if (d.length !== 14) return cnpj;
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return "—";
  return d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

function formatCurrencyBr(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return "—";
  const s = String(value);
  if (s.includes("R$")) return s;
  return `R$ ${s}`;
}

function toYmd(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function DecisionBadge({ decision }: { decision?: string | null }) {
  if (!decision) {
    return <span className="rounded-md bg-gray-100 px-2 py-0.5 text-[11px] font-bold text-gray-500 dark:bg-white/10 dark:text-gray-400">Pendente</span>;
  }
  const map: Record<string, string> = {
    SACADO: "bg-blue-100 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300",
    CEDENTE: "bg-slate-200 text-slate-700 dark:bg-slate-600/40 dark:text-slate-200",
    INCONCLUSIVO: "bg-amber-100 text-amber-800 dark:bg-amber-500/15 dark:text-amber-300",
  };
  const label = decision === "SACADO" ? "Sacado" : decision === "CEDENTE" ? "Cedente" : "Inconclusivo";
  return <span className={`rounded-md px-2 py-0.5 text-[11px] font-bold ${map[decision] ?? "bg-gray-100 text-gray-600"}`}>{label}</span>;
}

export default function HistoricoPage() {
  const [searchInput, setSearchInput] = useState("");
  const [q, setQ] = useState("");
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [page, setPage] = useState(0);
  const size = 20;

  // Debounce da busca por texto.
  useEffect(() => {
    const t = setTimeout(() => {
      setQ(searchInput.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(t);
  }, [searchInput]);

  const { data, isFetching } = usePaymentPlaceHistory({
    q: q || undefined,
    from: selectedDay || undefined,
    to: selectedDay || undefined,
    page,
    size,
  });

  const entries: PaymentPlaceEntry[] = data?.entries ?? [];
  const totalPages = data?.totalPages ?? 0;
  const total = data?.totalElements ?? 0;

  const calendarCells = useMemo(() => {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const firstWeekday = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const cells: (number | null)[] = [];
    for (let i = 0; i < firstWeekday; i++) cells.push(null);
    for (let d = 1; d <= daysInMonth; d++) cells.push(d);
    return { year, month, cells };
  }, [calendarMonth]);

  const todayYmd = toYmd(new Date());

  return (
    <div className="space-y-5 p-1">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <nav className="mb-1 flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <Link href="/praca-pagamento" className="transition-colors hover:text-primary">Praça de Pagamento</Link>
            <span>/</span>
            <span className="font-medium text-grafite dark:text-white">Histórico</span>
          </nav>
          <h1 className="font-sans text-xl font-bold text-primary dark:text-white">Histórico de títulos</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Biblioteca de todos os lançamentos já importados. Busque por sacado, cedente, banco/agência ou nº do título — ou navegue por dia no calendário.
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[300px_1fr]">
        {/* Coluna esquerda: busca + calendário */}
        <div className="space-y-4">
          <div className="relative">
            <span className="material-icons-outlined pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[20px] text-gray-400">search</span>
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Sacado, cedente, banco, nº título..."
              className="h-11 w-full rounded-xl border border-border-light bg-white pl-10 pr-9 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
            />
            {searchInput ? (
              <button
                type="button"
                onClick={() => setSearchInput("")}
                className="absolute right-2 top-1/2 -translate-y-1/2 flex h-7 w-7 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10"
                aria-label="Limpar busca"
              >
                <span className="material-icons-outlined text-[18px]">close</span>
              </button>
            ) : null}
          </div>

          <div className="rounded-xl border border-border-light bg-surface-light p-3 shadow-sm dark:border-border-dark dark:bg-surface-dark">
            <div className="mb-2 flex items-center justify-between gap-1">
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => setCalendarMonth((d) => new Date(d.getFullYear() - 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Ano anterior"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_left</span></button>
                <button type="button" onClick={() => setCalendarMonth((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Mês anterior"><span className="material-icons-outlined text-[16px]">chevron_left</span></button>
              </div>
              <p className="text-sm font-bold capitalize text-grafite dark:text-white">{`${calendarMonth.toLocaleDateString("pt-BR", { month: "long" })}/${String(calendarMonth.getFullYear()).slice(-2)}`}</p>
              <div className="flex items-center gap-1">
                <button type="button" onClick={() => setCalendarMonth((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo mês"><span className="material-icons-outlined text-[16px]">chevron_right</span></button>
                <button type="button" onClick={() => setCalendarMonth((d) => new Date(d.getFullYear() + 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo ano"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_right</span></button>
              </div>
            </div>
            <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-bold uppercase text-gray-400">
              {["D", "S", "T", "Q", "Q", "S", "S"].map((d, i) => <span key={i}>{d}</span>)}
            </div>
            <div className="mt-1 grid grid-cols-7 gap-1">
              {calendarCells.cells.map((day, idx) => {
                if (day === null) return <div key={`b-${idx}`} />;
                const ymd = `${calendarCells.year}-${String(calendarCells.month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
                const selected = ymd === selectedDay;
                const isFuture = ymd > todayYmd;
                return (
                  <button
                    key={ymd}
                    type="button"
                    disabled={isFuture}
                    onClick={() => { setSelectedDay(selected ? null : ymd); setPage(0); }}
                    className={`flex h-8 items-center justify-center rounded-lg text-sm transition-colors ${
                      selected
                        ? "bg-primary font-bold text-white dark:bg-secondary"
                        : isFuture
                          ? "cursor-not-allowed text-gray-300 dark:text-gray-600"
                          : "text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/5"
                    } ${ymd === todayYmd && !selected ? "ring-1 ring-primary/50 dark:ring-secondary/50" : ""}`}
                  >
                    {day}
                  </button>
                );
              })}
            </div>
            {selectedDay ? (
              <div className="mt-3 flex items-center justify-between gap-2 border-t border-border-light pt-2 dark:border-border-dark">
                <span className="text-xs font-bold text-grafite dark:text-white">{new Date(`${selectedDay}T12:00:00`).toLocaleDateString("pt-BR")}</span>
                <button type="button" onClick={() => { setSelectedDay(null); setPage(0); }} className="text-xs font-semibold text-primary hover:underline dark:text-secondary">Limpar dia</button>
              </div>
            ) : (
              <p className="mt-3 border-t border-border-light pt-2 text-[11px] text-gray-400 dark:border-border-dark">Selecione um dia para filtrar pela data de importação.</p>
            )}
          </div>
        </div>

        {/* Coluna direita: resultados */}
        <div className="space-y-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {isFetching ? "Buscando..." : <><strong className="text-grafite dark:text-white">{total}</strong> resultado(s){q ? <> para “{q}”</> : null}{selectedDay ? <> em {new Date(`${selectedDay}T12:00:00`).toLocaleDateString("pt-BR")}</> : null}</>}
            </p>
          </div>

          {entries.length === 0 && !isFetching ? (
            <div className="rounded-xl border border-dashed border-border-light p-10 text-center text-sm text-gray-500 dark:border-border-dark dark:text-gray-400">
              <span className="material-icons-outlined mb-2 block text-[32px] text-gray-300">history</span>
              Nenhum lançamento encontrado.
            </div>
          ) : (
            <div className="space-y-2">
              {entries.map((e) => (
                <div key={e.id} className="rounded-xl border border-border-light bg-surface-light p-3 shadow-sm transition-colors hover:bg-gray-50/60 dark:border-border-dark dark:bg-surface-dark dark:hover:bg-white/[0.03]">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="truncate font-bold text-grafite dark:text-white">{e.titleNumber ?? "—"}</p>
                        <DecisionBadge decision={e.analystDecision} />
                      </div>
                      <p className="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
                        <span className="font-bold text-blue-600/70 dark:text-blue-300/70">Sacado:</span> {e.payerName ?? "—"}
                        {e.payerDocument ? <span className="text-gray-400"> · {formatCnpj(e.payerDocument)}</span> : null}
                      </p>
                      <p className="truncate text-xs text-gray-500 dark:text-gray-400">
                        <span className="font-bold text-primary/70 dark:text-secondary/70">Cedente:</span> {e.clientName ?? <span className="text-gray-400">cód. {e.clientCode ?? "—"}</span>}
                      </p>
                    </div>
                    <div className="text-right">
                      <p className="whitespace-nowrap text-sm font-bold text-grafite dark:text-white">{formatCurrencyBr(e.paidValue)}</p>
                      <p className="text-[11px] text-gray-500 dark:text-gray-400">{e.bankName ?? "—"} · {e.bankAgency ?? "—"}</p>
                    </div>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-border-light pt-2 text-[11px] text-gray-400 dark:border-border-dark">
                    <span className="inline-flex items-center gap-1"><span className="material-icons-outlined text-[13px]">description</span>{e.batchFileName ?? "—"}</span>
                    <span className="inline-flex items-center gap-1"><span className="material-icons-outlined text-[13px]">schedule</span>Importado em {formatDateTime(e.batchImportedAt)}</span>
                    {e.dueDate ? <span className="inline-flex items-center gap-1"><span className="material-icons-outlined text-[13px]">event</span>Venc. {e.dueDate}</span> : null}
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Paginação */}
          {totalPages > 1 ? (
            <div className="flex items-center justify-between gap-3 pt-1">
              <button
                type="button"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="inline-flex h-9 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
              >
                <span className="material-icons-outlined text-[16px]">chevron_left</span>Anterior
              </button>
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400">Página {page + 1} de {totalPages}</span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                className="inline-flex h-9 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
              >
                Próxima<span className="material-icons-outlined text-[16px]">chevron_right</span>
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
