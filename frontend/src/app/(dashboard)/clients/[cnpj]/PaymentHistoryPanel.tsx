"use client";

import React, { useState } from "react";
import Icon from "@/components/ui/Icon";
import type {
  AdvancedCommercialPaymentHistory,
  SegmentPaymentHistory,
  MonthDetailItem,
  MonthDetailSummary,
  TitleQuantityEntry,
  EvolutionCommitmentsEntry,
  BusinessReferenceEntry,
  AverageDelayPeriodItem,
  RelationshipSuppliersPeriods,
} from "../../../../types/company-detail";

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtMonth(raw: string | undefined): string {
  if (!raw) return "—";
  if (/^\d{4}-\d{2}$/.test(raw)) {
    const [y, m] = raw.split("-");
    return `${m}/${y}`;
  }
  return raw;
}

// ── SummaryChip ───────────────────────────────────────────────────────────────

type ChipColor = "green" | "red" | "blue" | "orange" | "gray";

function SummaryChip({ label, value, color = "gray" }: { label: string; value: string; color?: ChipColor }) {
  const cls: Record<ChipColor, string> = {
    green:  "bg-green-100  text-green-700  dark:bg-green-900/30  dark:text-green-300",
    red:    "bg-red-100    text-red-700    dark:bg-red-900/30    dark:text-red-300",
    blue:   "bg-blue-100   text-blue-700   dark:bg-blue-900/30   dark:text-blue-300",
    orange: "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300",
    gray:   "bg-gray-100   text-gray-600   dark:bg-gray-800      dark:text-gray-300",
  };
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-sans font-bold ${cls[color]}`}>
      <span className="font-normal opacity-70">{label}</span> {value}
    </span>
  );
}

// ── ExpandSection ─────────────────────────────────────────────────────────────

function ExpandSection({
  id, title, icon, summary, children, expanded, onToggle,
}: {
  id: string;
  title: string;
  icon: string;
  summary: React.ReactNode;
  children: React.ReactNode;
  expanded: boolean;
  onToggle: (id: string) => void;
}) {
  return (
    <div className="border border-border-light dark:border-border-dark rounded-xl overflow-hidden">
      <button
        onClick={() => onToggle(id)}
        className="w-full flex items-center gap-3 p-4 bg-surface-light dark:bg-surface-dark hover:bg-primary/5 dark:hover:bg-primary/10 transition-colors text-left"
      >
        <Icon name={icon} className="text-lg text-primary flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-sans font-bold text-grafite dark:text-white">{title}</p>
          <div className="flex flex-wrap gap-1.5 mt-1">{summary}</div>
        </div>
        <Icon name="expand_more" className={`text-gray-400 flex-shrink-0 transition-transform duration-200 ${expanded ? "rotate-180" : ""}`} />
      </button>

      {expanded && (
        <div className="border-t border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark p-4 space-y-5">
          {children}
        </div>
      )}
    </div>
  );
}

// ── FaixasGrid ────────────────────────────────────────────────────────────────

function FaixasGrid({ entries }: { entries: TitleQuantityEntry[] }) {
  const hasData = entries.some(e => e.range && e.range !== "-");
  if (!hasData) return <p className="text-sm text-gray-400 font-serif">Sem dados de faixas.</p>;
  return (
    <div className="grid grid-cols-3 sm:grid-cols-4 lg:grid-cols-7 gap-2">
      {entries.map((entry, i) => {
        const isPontual = entry.name === "PONTUAL";
        return (
          <div key={i} className={`rounded-lg p-3 text-center border ${
            isPontual
              ? "bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-700"
              : "bg-surface-light dark:bg-surface-dark border-border-light dark:border-border-dark"
          }`}>
            <p className={`text-[10px] font-sans font-bold uppercase tracking-wider mb-1 ${isPontual ? "text-green-700 dark:text-green-400" : "text-gray-400"}`}>
              {entry.name}
            </p>
            <p className={`text-xs font-sans font-bold ${isPontual ? "text-green-700 dark:text-green-300" : "text-grafite dark:text-gray-200"}`}>
              {entry.range && entry.range !== "-" ? entry.range : "—"}
            </p>
            {entry.percentage && entry.percentage !== "0.0% e 0.0%" && (
              <p className="text-[9px] text-gray-400 font-serif mt-0.5">{entry.percentage}</p>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ── MonthlyTable ──────────────────────────────────────────────────────────────

function MonthlyTable({ months }: { months: MonthDetailItem[] }) {
  if (!months.length) return <p className="text-sm text-gray-400 font-serif">Sem dados mensais.</p>;
  const get = (m: MonthDetailItem, name: string) =>
    m.periodList?.find(p => p.name === name);

  const renderCell = (m: MonthDetailItem, name: string, colorClass?: string) => {
    const period = get(m, name);
    const range = period?.range && period.range !== "-" ? period.range : null;
    const percentage = period?.percentage && period.percentage !== "0.0% e 0.0%" ? period.percentage.replace(" e ", " – ") : null;

    if (!range && !percentage) {
      return <span className="text-gray-400">—</span>;
    }

    return (
      <div className="flex flex-col items-center leading-tight">
        <span className={colorClass}>{range ?? "—"}</span>
        {percentage && (
          <span className="mt-0.5 text-[10px] text-gray-400 whitespace-nowrap">{percentage}</span>
        )}
      </div>
    );
  };

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b border-border-light dark:border-border-dark">
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4 whitespace-nowrap">Mês</th>
            <th className="text-center text-[11px] font-sans font-bold text-green-600 uppercase pb-2 px-2">Pontual</th>
            <th className="text-center text-[11px] font-sans font-bold text-yellow-600 uppercase pb-2 px-2">8-15d</th>
            <th className="text-center text-[11px] font-sans font-bold text-orange-500 uppercase pb-2 px-2">16-30d</th>
            <th className="text-center text-[11px] font-sans font-bold text-red-500 uppercase pb-2 px-2">31-60d</th>
            <th className="text-center text-[11px] font-sans font-bold text-red-700 uppercase pb-2 px-2">+60d</th>
            <th className="text-center text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 px-2">Total</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border-light dark:divide-border-dark">
          {months.map((m, i) => (
            <tr key={i}>
              <td className="py-2 pr-4 font-sans font-bold text-grafite dark:text-gray-200 whitespace-nowrap">{fmtMonth(m.month)}</td>
              <td className="py-2 px-2 text-center font-serif text-green-700 dark:text-green-400">{renderCell(m, "PONTUAL", "text-green-700 dark:text-green-400")}</td>
              <td className="py-2 px-2 text-center font-serif text-grafite dark:text-gray-300">{renderCell(m, "8-15")}</td>
              <td className="py-2 px-2 text-center font-serif text-grafite dark:text-gray-300">{renderCell(m, "16-30")}</td>
              <td className="py-2 px-2 text-center font-serif text-grafite dark:text-gray-300">{renderCell(m, "31-60")}</td>
              <td className="py-2 px-2 text-center font-serif text-grafite dark:text-gray-300">{renderCell(m, "+60")}</td>
              <td className="py-2 px-2 text-center font-serif text-gray-500">{renderCell(m, "TOTAL MES")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── DelayChips ────────────────────────────────────────────────────────────────

function DelayChips({ periods }: { periods: AverageDelayPeriodItem[] }) {
  if (!periods.length) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {periods.map((d, i) => {
        const hasDelay = (d.averageDelayDaysFrom ?? 0) > 0;
        return (
          <div key={i} className={`rounded-lg px-3 py-2 text-center border min-w-[70px] ${
            hasDelay
              ? "bg-amber-50 dark:bg-amber-900/20 border-amber-200 dark:border-amber-700"
              : "bg-surface-light dark:bg-surface-dark border-border-light dark:border-border-dark"
          }`}>
            <p className="text-[10px] font-sans font-bold uppercase text-gray-400">{d.period}</p>
            <p className={`text-xs font-sans font-bold mt-0.5 ${hasDelay ? "text-amber-600 dark:text-amber-400" : "text-grafite dark:text-gray-200"}`}>
              {`${d.averageDelayDaysFrom ?? 0}–${d.averageDelayDaysTo ?? 0}`}d
            </p>
          </div>
        );
      })}
    </div>
  );
}

// ── MonthDetailSummaryTable ───────────────────────────────────────────────────

const SUMMARY_ROWS: { key: keyof MonthDetailSummary; label: string; color: string }[] = [
  { key: "punctual",   label: "Pontual",  color: "text-green-700 dark:text-green-400" },
  { key: "period8To15",  label: "8–15 dias",  color: "text-yellow-600 dark:text-yellow-400" },
  { key: "period16To30", label: "16–30 dias", color: "text-orange-500 dark:text-orange-400" },
  { key: "period31To60", label: "31–60 dias", color: "text-red-500 dark:text-red-400" },
  { key: "periodGT60",   label: "+60 dias",   color: "text-red-700 dark:text-red-300" },
  { key: "spotPayment",  label: "À Vista",    color: "text-blue-600 dark:text-blue-400" },
  { key: "total",        label: "Total",      color: "text-grafite dark:text-gray-200" },
];

function MonthDetailSummaryTable({ summary }: { summary: MonthDetailSummary }) {
  const hasAny = SUMMARY_ROWS.some(r => {
    const e = summary[r.key];
    return e?.totalValueRangeDescription && e.totalValueRangeDescription !== "-";
  });
  if (!hasAny) return null;

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b border-border-light dark:border-border-dark">
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4 whitespace-nowrap">Faixa</th>
            <th className="text-right text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 px-3 whitespace-nowrap">% do Total</th>
            <th className="text-right text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 px-3 whitespace-nowrap">Volume Total</th>
            <th className="text-right text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pl-3 whitespace-nowrap">Média por Título</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border-light dark:divide-border-dark">
          {SUMMARY_ROWS.map(({ key, label, color }) => {
            const e = summary[key];
            if (!e) return null;
            const hasData = e.totalValueRangeDescription && e.totalValueRangeDescription !== "-";
            const isTotal = key === "total";
            return (
              <tr key={key} className={isTotal ? "bg-gray-50 dark:bg-gray-800/40 font-bold" : ""}>
                <td className={`py-2 pr-4 font-sans font-bold text-sm ${color}`}>{label}</td>
                <td className="py-2 px-3 text-right font-serif text-grafite dark:text-gray-300 whitespace-nowrap">
                  {(e.percentageValueFrom != null && e.percentageValueTo != null && (e.percentageValueFrom > 0 || e.percentageValueTo > 0))
                    ? `${e.percentageValueFrom}% – ${e.percentageValueTo}%`
                    : "—"}
                </td>
                <td className="py-2 px-3 text-right font-serif text-grafite dark:text-gray-300 whitespace-nowrap">
                  {hasData ? e.totalValueRangeDescription : "—"}
                </td>
                <td className="py-2 pl-3 text-right font-serif text-grafite dark:text-gray-300 whitespace-nowrap">
                  {e.averageValueRangeDescription && e.averageValueRangeDescription !== "-"
                    ? e.averageValueRangeDescription : "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ── EvoTable ──────────────────────────────────────────────────────────────────

function EvoTable({ entries }: { entries: EvolutionCommitmentsEntry[] }) {
  if (!entries.length) return <p className="text-sm text-gray-400 font-serif">Sem dados de evolução.</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border-collapse">
        <thead>
          <tr className="border-b border-border-light dark:border-border-dark">
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4">Mês/Ano</th>
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4">A Vencer</th>
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4">Vencido</th>
            <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2">Total do Mês</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border-light dark:divide-border-dark">
          {entries.map((e, i) => {
            const hasOverdue = e.expiredTrackDescription && e.expiredTrackDescription !== "-";
            return (
              <tr key={i} className={hasOverdue ? "bg-red-50/40 dark:bg-red-900/10" : ""}>
                <td className="py-2 pr-4 font-sans font-bold text-grafite dark:text-gray-200 whitespace-nowrap">
                  {e.descriptionMonthCommitment}/{e.yearCommitment}
                </td>
                <td className="py-2 pr-4 font-serif text-grafite dark:text-gray-300">
                  {e.trackDescriptionToExpire && e.trackDescriptionToExpire !== "-" ? e.trackDescriptionToExpire : "—"}
                </td>
                <td className={`py-2 pr-4 font-serif ${hasOverdue ? "text-red-600 dark:text-red-400 font-bold" : "text-gray-400"}`}>
                  {hasOverdue ? e.expiredTrackDescription : "—"}
                </td>
                <td className="py-2 font-serif text-grafite dark:text-gray-300">
                  {e.totalMonthRangeDescription ?? "—"}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ── RefCards ──────────────────────────────────────────────────────────────────

function RefCards({ refs }: { refs: BusinessReferenceEntry[] }) {
  if (!refs.length) return <p className="text-sm text-gray-400 font-serif">Sem referenciais registrados.</p>;
  const ICON_MAP: Record<string, string> = {
    "ULTIMA COMPRA": "shopping_cart",
    "MAIOR FATURA":  "receipt",
    "MAIOR ACUMULO": "account_balance",
  };
  const COLOR_MAP: Record<string, string> = {
    "ULTIMA COMPRA": "text-secondary",
    "MAIOR FATURA":  "text-primary",
    "MAIOR ACUMULO": "text-blue-600 dark:text-blue-400",
  };
  return (
    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
      {refs.map((ref, i) => {
        const icon  = ICON_MAP[ref.businessDescription]  ?? "info";
        const color = COLOR_MAP[ref.businessDescription] ?? "text-gray-500";
        const month = ref.monthPotentialDate ? String(ref.monthPotentialDate).padStart(2, "0") : null;
        const dateLabel = month && ref.yearPotentialDate ? `${month}/${ref.yearPotentialDate}` : null;
        return (
          <div key={i} className="rounded-xl p-4 border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <Icon name={icon} className={`text-xl ${color}`} />
              <p className="text-xs font-sans font-bold text-gray-400 uppercase tracking-wider">{ref.businessDescription}</p>
            </div>
            <p className="text-xl font-sans font-bold text-grafite dark:text-white">
              {ref.potentialValueRangeDescription ?? "—"}
            </p>
            {dateLabel && <p className="text-xs text-gray-400 font-serif">Referência: {dateLabel}</p>}
          </div>
        );
      })}
    </div>
  );
}

function normalizeRelationshipLabel(raw?: string) {
  return (raw ?? "")
    .replace(/:/g, "")
    .replace(/MES/g, "M")
    .replace(/ANOS/g, "A")
    .replace(/ANO/g, "A")
    .replace(/\s+/g, " ")
    .trim();
}

function RelationshipSuppliersCard({ data }: { data: RelationshipSuppliersPeriods }) {
  const periods = data.relationshipSuppliersPeriodList ?? [];
  const summary = data.summary;

  if (!periods.length && !summary) {
    return <p className="text-sm text-gray-400 font-serif">Sem dados de relacionamento.</p>;
  }

  return (
    <div className="space-y-4">
      {periods.length > 0 && (
        <div className="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-7 gap-2">
          {periods.map((item, index) => (
            <div
              key={`${item.relationshipPeriodDescription ?? "period"}-${index}`}
              className="rounded-lg border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark px-3 py-3 text-center"
            >
              <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">
                {normalizeRelationshipLabel(item.relationshipPeriodDescription)}
              </p>
              <p className="mt-1 text-lg font-sans font-bold text-grafite dark:text-white">
                {item.relationshipSourceQuantity ?? 0}
              </p>
              <p className="text-[10px] font-sans text-gray-500">fontes</p>
            </div>
          ))}
        </div>
      )}

      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-2">
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Fontes consultadas</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.sourcesTotal ?? 0}</p>
          </div>
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Hist. pagamentos</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.paymentHistorySources ?? 0}</p>
          </div>
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Valores</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.paymentHistoryValuesSources ?? 0}</p>
          </div>
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Compromissos</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.evolutionCommitmentsSources ?? 0}</p>
          </div>
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">Referências</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.businessReferencesSources ?? 0}</p>
          </div>
          <div className="rounded-lg border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-3 py-2.5">
            <p className="text-[10px] font-sans font-bold uppercase tracking-wide text-gray-400">À vista</p>
            <p className="mt-1 text-base font-sans font-bold text-grafite dark:text-white">{summary.spotPaymentBusinessReferencesSources ?? 0}</p>
          </div>
        </div>
      )}
    </div>
  );
}

// ── VisaoTab ──────────────────────────────────────────────────────────────────

type VisaoData = Pick<AdvancedCommercialPaymentHistory, "paymentHistory" | "evolutionCommitmentsSuppliers" | "businessReferences"> | SegmentPaymentHistory | undefined;

function hasRelationshipSuppliersPeriods(data: VisaoData): data is SegmentPaymentHistory {
  return !!data && "relationshipSuppliersPeriods" in data;
}

function VisaoTab({
  data, tabId, expandedSections, onToggle,
}: {
  data: VisaoData;
  tabId: string;
  expandedSections: Set<string>;
  onToggle: (id: string) => void;
}) {
  const months      = data?.paymentHistory?.monthDetail?.months ?? [];
  const faixas      = data?.paymentHistory?.titlesQuantity ?? [];
  const delays      = data?.paymentHistory?.averageDelayPeriod?.periodList ?? [];
  const delaySummary = data?.paymentHistory?.averageDelayPeriod?.summary;
  const monthSummary = data?.paymentHistory?.monthDetail?.summary;
  const evoList     = data?.evolutionCommitmentsSuppliers?.evolutionCommitmentsSuppliersList ?? [];
  const refList     = data?.businessReferences?.businessReferencesList ?? [];
  const relationship = hasRelationshipSuppliersPeriods(data) ? data.relationshipSuppliersPeriods : undefined;
  const relationshipPeriods = relationship?.relationshipSuppliersPeriodList ?? [];
  const relationshipSummary = relationship?.summary;

  const pontual   = faixas.find(f => f.name === "PONTUAL");
  const lastMonth = months[months.length - 1];
  const lastEvo   = evoList[evoList.length - 1];
  const evoDate   = data?.evolutionCommitmentsSuppliers?.lastUpdateDate;
  const refDate   = data?.businessReferences?.lastUpdateDate;

  const hasDelayData = delays.length > 0 || (delaySummary?.averageDelayDaysFrom != null);
  const hasMonthSummary = !!monthSummary && Object.values(monthSummary).some(
    (e) => e && typeof e === 'object' && (e as { totalValueRangeDescription?: string }).totalValueRangeDescription !== "-"
  );

  const noData = months.length === 0 && faixas.length === 0 && evoList.length === 0 && refList.length === 0 && !hasDelayData && !hasMonthSummary && relationshipPeriods.length === 0 && !relationshipSummary;

  if (noData) {
    return (
      <div className="py-10 text-center">
        <Icon name="inbox" className="text-4xl text-gray-300 dark:text-gray-600" />
        <p className="text-sm text-gray-400 font-serif mt-2">Sem dados para esta visão.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">

      {(relationshipPeriods.length > 0 || relationshipSummary) && (
        <ExpandSection
          id={`${tabId}-relationship`}
          title="Relacionamento com Fontes"
          icon="handshake"
          expanded={expandedSections.has(`${tabId}-relationship`)}
          onToggle={onToggle}
          summary={
            <>
              {relationshipSummary?.sourcesTotal != null && (
                <SummaryChip label="fontes" value={String(relationshipSummary.sourcesTotal)} color="blue" />
              )}
              {relationshipPeriods.length > 0 && (
                <SummaryChip label="faixas" value={String(relationshipPeriods.length)} color="gray" />
              )}
              {relationship?.lastUpdateDate && (
                <SummaryChip label="atualizado" value={relationship.lastUpdateDate.slice(0, 7)} color="gray" />
              )}
            </>
          }
        >
          {relationship && <RelationshipSuppliersCard data={relationship} />}
        </ExpandSection>
      )}

      {/* 1 — Histórico de Pagamentos */}
      {(months.length > 0 || faixas.length > 0 || hasDelayData || hasMonthSummary) && (
        <ExpandSection
          id={`${tabId}-hist`}
          title="Histórico de Pagamentos"
          icon="receipt_long"
          expanded={expandedSections.has(`${tabId}-hist`)}
          onToggle={onToggle}
          summary={
            <>
              {months.length > 0 && <SummaryChip label="meses" value={String(months.length)} color="blue" />}
              {pontual?.range && <SummaryChip label="pontual" value={pontual.range} color="green" />}
              {delaySummary?.averageDelayDaysFrom != null && (
                <SummaryChip
                  label="atraso médio"
                  value={`${delaySummary.averageDelayDaysFrom}–${delaySummary.averageDelayDaysTo ?? delaySummary.averageDelayDaysFrom}d`}
                  color={(delaySummary.averageDelayDaysFrom ?? 0) > 0 ? "orange" : "green"}
                />
              )}
              {lastMonth && <SummaryChip label="último" value={fmtMonth(lastMonth.month)} color="gray" />}
            </>
          }
        >
          {/* Prazo médio geral de atraso */}
          {delaySummary?.averageDelayDaysFrom != null && (
            <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border ${
              (delaySummary.averageDelayDaysFrom ?? 0) === 0
                ? "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800"
                : "bg-amber-50 dark:bg-amber-900/10 border-amber-200 dark:border-amber-700"
            }`}>
              <Icon name="schedule" className={`text-xl ${ (delaySummary.averageDelayDaysFrom ?? 0) === 0 ? "text-green-600" : "text-amber-600" }`} />
              <div>
                <p className="text-[10px] font-sans font-bold text-gray-400 uppercase tracking-wide">Prazo Médio de Atraso (período geral)</p>
                <p className={`text-base font-sans font-bold mt-0.5 ${
                  (delaySummary.averageDelayDaysFrom ?? 0) === 0
                    ? "text-green-700 dark:text-green-400"
                    : "text-amber-700 dark:text-amber-300"
                }`}>
                  {(delaySummary.averageDelayDaysFrom ?? 0) === 0 && (delaySummary.averageDelayDaysTo ?? 0) === 0
                    ? "Sem atraso registrado"
                    : `${delaySummary.averageDelayDaysFrom} a ${delaySummary.averageDelayDaysTo} dias`}
                </p>
              </div>
            </div>
          )}

          {/* Faixas de Pagamento */}
          {faixas.length > 0 && (
            <div>
              <p className="text-[10px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-2">Faixas de Pagamento</p>
              <FaixasGrid entries={faixas} />
            </div>
          )}

          {/* Média dos Pagamentos (monthDetail.summary) */}
          {hasMonthSummary && monthSummary && (
            <div>
              <p className="text-[10px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-2">Média dos Pagamentos — Histórico por Faixa</p>
              <MonthDetailSummaryTable summary={monthSummary} />
            </div>
          )}

          {/* Prazo médio mês a mês */}
          {delays.length > 0 && (
            <div>
              <p className="text-[10px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-2">Atraso por Período (meses)</p>
              <DelayChips periods={delays} />
            </div>
          )}

          {/* Detalhe mensal */}
          {months.length > 0 && (
            <div>
              <p className="text-[10px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-2">Detalhe Mensal</p>
              <MonthlyTable months={months} />
            </div>
          )}
        </ExpandSection>
      )}

      {/* 2 — Evolução de Compromissos */}
      {evoList.length > 0 && (
        <ExpandSection
          id={`${tabId}-evo`}
          title="Evolução de Compromissos"
          icon="trending_up"
          expanded={expandedSections.has(`${tabId}-evo`)}
          onToggle={onToggle}
          summary={
            <>
              <SummaryChip label="períodos" value={String(evoList.length)} color="blue" />
              {lastEvo?.totalMonthRangeDescription && (
                <SummaryChip label="último total" value={lastEvo.totalMonthRangeDescription} color="orange" />
              )}
              {lastEvo && (
                <SummaryChip label="ref." value={`${lastEvo.descriptionMonthCommitment}/${lastEvo.yearCommitment}`} color="gray" />
              )}
              {evoDate && <SummaryChip label="atualizado" value={evoDate.slice(0, 7)} color="gray" />}
            </>
          }
        >
          <EvoTable entries={evoList} />
        </ExpandSection>
      )}

      {/* 3 — Referenciais de Negócios */}
      {refList.length > 0 && (
        <ExpandSection
          id={`${tabId}-ref`}
          title="Referenciais de Negócios"
          icon="store"
          expanded={expandedSections.has(`${tabId}-ref`)}
          onToggle={onToggle}
          summary={
            <>
              {refList.map((r, i) => (
                <SummaryChip key={i} label={r.businessDescription} value={r.potentialValueRangeDescription ?? "—"} color="gray" />
              ))}
              {refDate && <SummaryChip label="atualizado" value={refDate.slice(0, 7)} color="gray" />}
            </>
          }
        >
          <RefCards refs={refList} />
        </ExpandSection>
      )}

    </div>
  );
}

// ── PaymentHistoryPanel (exported) ────────────────────────────────────────────

type Tab = "mercado" | "sacado" | "cedente";

interface Props {
  ph: AdvancedCommercialPaymentHistory;
}

const TABS: { id: Tab; label: string; icon: string }[] = [
  { id: "mercado", label: "Mercado",            icon: "public" },
  { id: "sacado",  label: "Factorings Sacado",  icon: "swap_horiz" },
  { id: "cedente", label: "Visão Cedente",       icon: "account_balance_wallet" },
];

export function PaymentHistoryPanel({ ph }: Props) {
  const [activeTab, setActiveTab]           = useState<Tab>("mercado");
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setExpandedSections(prev => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const tabData: Record<Tab, VisaoData> = {
    mercado: ph,
    sacado:  ph.segmentData?.drawee,
    cedente: ph.segmentData?.assignor,
  };

  // badge: conta quantas seções têm dados
  const countSections = (data: VisaoData) => {
    let n = 0;
    if (data && "relationshipSuppliersPeriods" in data && (data.relationshipSuppliersPeriods?.relationshipSuppliersPeriodList?.length || data.relationshipSuppliersPeriods?.summary)) n++;
    if ((data?.paymentHistory?.monthDetail?.months ?? []).length > 0 || (data?.paymentHistory?.titlesQuantity ?? []).length > 0) n++;
    if ((data?.evolutionCommitmentsSuppliers?.evolutionCommitmentsSuppliersList ?? []).length > 0) n++;
    if ((data?.businessReferences?.businessReferencesList ?? []).length > 0) n++;
    return n;
  };

  return (
    <div className="mb-6">
      <h2 className="font-sans font-bold text-base text-grafite dark:text-white mb-3 flex items-center gap-2">
        <Icon name="receipt_long" className="text-lg text-primary" />
        Histórico de Pagamentos
      </h2>

      <div className="bg-surface-light dark:bg-surface-dark rounded-xl shadow-sm border border-border-light dark:border-border-dark overflow-hidden">

        {/* ── Tab bar ─────────────────────────────────────────────────────── */}
        <div className="flex border-b border-border-light dark:border-border-dark overflow-x-auto">
          {TABS.map(tab => {
            const count = countSections(tabData[tab.id]);
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2 px-4 py-3 text-sm font-sans font-semibold whitespace-nowrap transition-colors border-b-2 ${
                  activeTab === tab.id
                    ? "border-primary text-primary bg-primary/5 dark:bg-primary/10"
                    : "border-transparent text-gray-500 hover:text-grafite dark:hover:text-white hover:bg-gray-50 dark:hover:bg-gray-800/50"
                }`}
              >
                <Icon name={tab.icon} className="text-base" />
                {tab.label}
                {count > 0 && (
                  <span className={`ml-1 px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                    activeTab === tab.id
                      ? "bg-primary text-white"
                      : "bg-gray-200 dark:bg-gray-700 text-gray-500 dark:text-gray-400"
                  }`}>
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* ── Tab content ─────────────────────────────────────────────────── */}
        <div className="p-4">
          <VisaoTab
            data={tabData[activeTab]}
            tabId={activeTab}
            expandedSections={expandedSections}
            onToggle={toggle}
          />
        </div>

      </div>
    </div>
  );
}
