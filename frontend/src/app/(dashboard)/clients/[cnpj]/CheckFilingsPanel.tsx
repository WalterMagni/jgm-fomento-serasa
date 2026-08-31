"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import type { CheckFilingsHistorical, CheckFilingRecord } from "../../../../types/company-detail";

function formatMoney(value: number | undefined) {
  if (!value) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

function formatDateTime(value: string | undefined) {
  if (!value) return "—";
  // Serasa manda "yyyy-MM-dd HH:mm:ss"; troca espaço por T pro Date entender.
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function checkRange(record: CheckFilingRecord) {
  const { initialCheckNumber: from, finalCheckNumber: to } = record;
  if (from == null && to == null) return "—";
  if (from === to || to == null) return String(from);
  return `${from}–${to}`;
}

export function CheckFilingsPanel({ data }: { data?: CheckFilingsHistorical }) {
  const [expanded, setExpanded] = useState(false);
  const records: CheckFilingRecord[] = data?.checkFilingsHistoricalResponse ?? [];

  if (records.length === 0) return null;

  const totalAmount = records.reduce((sum, r) => sum + (r.checkAmount ?? 0), 0);
  const totalChecks = records.reduce((sum, r) => sum + (r.quantityCheck ?? 0), 0);
  const banks = new Set(records.map((r) => r.bankName).filter(Boolean));

  return (
    <div className="mb-6">
      <h2 className="mb-3 flex items-center gap-2 font-sans text-base font-bold text-grafite dark:text-white">
        <Icon name="warning_amber" className="text-lg text-primary" />
        Histórico de Cheques
      </h2>

      <div className="overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="flex w-full items-center gap-3 p-4 text-left transition-colors hover:bg-primary/5 dark:hover:bg-primary/10"
        >
          <Icon name="description" className="flex-shrink-0 text-lg text-primary" />
          <div className="min-w-0 flex-1">
            <p className="font-sans text-sm font-bold text-grafite dark:text-white">
              {records.length} {records.length === 1 ? "ocorrência" : "ocorrências"} de cheque
            </p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-[10px] font-sans font-bold text-red-700 dark:bg-red-900/30 dark:text-red-300">
                {totalChecks} {totalChecks === 1 ? "cheque" : "cheques"}
              </span>
              <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-sans font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                {formatMoney(totalAmount)}
              </span>
              <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-[10px] font-sans font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                {banks.size} {banks.size === 1 ? "banco" : "bancos"}
              </span>
            </div>
          </div>
          <Icon
            name="expand_more"
            className={`flex-shrink-0 text-gray-400 transition-transform duration-200 ${expanded ? "rotate-180" : ""}`}
          />
        </button>

        <div className={`border-t border-border-light dark:border-border-dark ${expanded ? "block" : "hidden"}`}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-left text-sm">
              <thead className="bg-background-light dark:bg-background-dark">
                <tr className="border-b border-border-light dark:border-border-dark">
                  {["Inclusão", "Banco / Agência", "Conta", "Cheque(s)", "Qtd.", "Valor", "Motivo", "Titular"].map((h) => (
                    <th key={h} className="px-3 py-2 text-xs font-sans font-bold uppercase tracking-wide text-gray-400">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border-light dark:divide-border-dark">
                {records.map((r, i) => (
                  <tr key={i}>
                    <td className="whitespace-nowrap px-3 py-2 font-serif text-grafite dark:text-gray-200">
                      {formatDateTime(r.dateTimeInclusion)}
                    </td>
                    <td className="px-3 py-2 font-sans font-bold text-grafite dark:text-white">
                      {r.bankName || "—"}
                      {r.agencyNumber ? <span className="ml-1 font-serif font-normal text-gray-500">ag. {r.agencyNumber}</span> : null}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 font-serif text-grafite dark:text-gray-200">
                      {r.accountNumberCheck ?? "—"}
                    </td>
                    <td className="whitespace-nowrap px-3 py-2 font-serif text-grafite dark:text-gray-200">{checkRange(r)}</td>
                    <td className="px-3 py-2 font-serif text-grafite dark:text-gray-200">{r.quantityCheck ?? "—"}</td>
                    <td className="whitespace-nowrap px-3 py-2 font-serif text-grafite dark:text-gray-200">{formatMoney(r.checkAmount)}</td>
                    <td className="px-3 py-2 font-serif text-red-600 dark:text-red-400">{r.briefDescriptionReason || "—"}</td>
                    <td className="px-3 py-2 font-serif text-gray-500">{r.holderNameAccount || "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
