"use client";

import { useEffect, useMemo, useRef, useState } from "react";

function ymdOf(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function formatBr(ymd?: string | null) {
  if (!ymd) return "";
  const [y, m, d] = ymd.split("-");
  return `${d}/${m}/${y}`;
}

type Props = {
  value?: string | null; // YYYY-MM-DD
  onChange: (value: string | null) => void;
  placeholder?: string;
  /** Bloqueia dias depois desta data (YYYY-MM-DD). */
  max?: string;
  className?: string;
};

// Date-picker em português (popover custom) — o calendário nativo segue o idioma do navegador.
export default function MonthDatePicker({ value, onChange, placeholder = "dd/mm/aaaa", max, className = "" }: Props) {
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(() => {
    const base = value ? new Date(`${value}T12:00:00`) : new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [open]);

  const cells = useMemo(() => {
    const year = month.getFullYear();
    const m = month.getMonth();
    const firstWeekday = new Date(year, m, 1).getDay();
    const daysInMonth = new Date(year, m + 1, 0).getDate();
    const arr: (number | null)[] = [];
    for (let i = 0; i < firstWeekday; i++) arr.push(null);
    for (let d = 1; d <= daysInMonth; d++) arr.push(d);
    return { year, m, arr };
  }, [month]);

  const todayYmd = ymdOf(new Date());

  return (
    <div ref={ref} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex h-9 w-full items-center justify-between gap-2 rounded-lg border border-border-light bg-white px-2 text-sm outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark"
      >
        <span className={value ? "text-grafite dark:text-white" : "text-gray-400"}>{value ? formatBr(value) : placeholder}</span>
        <span className="material-icons-outlined text-[18px] text-gray-400">calendar_today</span>
      </button>
      {open ? (
        <div className="absolute left-0 top-full z-30 mt-1 w-[260px] rounded-xl border border-border-light bg-surface-light p-3 shadow-lg dark:border-border-dark dark:bg-surface-dark">
          <div className="mb-2 flex items-center justify-between gap-1">
            <div className="flex items-center gap-1">
              <button type="button" onClick={() => setMonth((d) => new Date(d.getFullYear() - 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Ano anterior"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_left</span></button>
              <button type="button" onClick={() => setMonth((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Mês anterior"><span className="material-icons-outlined text-[16px]">chevron_left</span></button>
            </div>
            <p className="text-sm font-bold capitalize text-grafite dark:text-white">{`${month.toLocaleDateString("pt-BR", { month: "long" })}/${String(month.getFullYear()).slice(-2)}`}</p>
            <div className="flex items-center gap-1">
              <button type="button" onClick={() => setMonth((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo mês"><span className="material-icons-outlined text-[16px]">chevron_right</span></button>
              <button type="button" onClick={() => setMonth((d) => new Date(d.getFullYear() + 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo ano"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_right</span></button>
            </div>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-bold uppercase text-gray-400">
            {["D", "S", "T", "Q", "Q", "S", "S"].map((d, i) => <span key={i}>{d}</span>)}
          </div>
          <div className="mt-1 grid grid-cols-7 gap-1">
            {cells.arr.map((day, idx) => {
              if (day === null) return <div key={`b-${idx}`} />;
              const ymd = `${cells.year}-${String(cells.m + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
              const selected = ymd === value;
              const isFuture = max ? ymd > max : false;
              return (
                <button
                  key={ymd}
                  type="button"
                  disabled={isFuture}
                  onClick={() => { onChange(ymd); setOpen(false); }}
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
          <div className="mt-2 flex items-center justify-between border-t border-border-light pt-2 dark:border-border-dark">
            <button type="button" onClick={() => { onChange(null); setOpen(false); }} className="text-xs font-semibold text-gray-500 hover:underline dark:text-gray-400">Limpar</button>
            <button
              type="button"
              onClick={() => { const t = ymdOf(new Date()); onChange(t); setMonth(new Date()); setOpen(false); }}
              className="text-xs font-semibold text-primary hover:underline dark:text-secondary"
            >
              Hoje
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
