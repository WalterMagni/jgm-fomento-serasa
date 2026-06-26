"use client";

import React, { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useClientProfile } from "../../../../hooks/useClientProfile";
import { usePaymentPlaceCompany, useReopenPaymentPlaceEntry } from "../../../../hooks/usePaymentPlaceCompany";
import PaymentPlaceEntryReadOnlyModal from "../../../../components/payment-place/PaymentPlaceEntryReadOnlyModal";
import { AttachmentBadge, AttachmentViewerModal } from "../../../../components/payment-place/EntryAttachments";
import type { PaymentPlaceEntry } from "../../../../types/payment-place";
import {
  riskLabel,
  totalDebtFromAnalysis,
  totalPendingFromAnalysis,
  type CompanyPredecessor,
  type CompanyMember,
  type CreditAnalysisHistoryItem,
  type QSAPartner,
  type QSADirector,
  type TitleQuantityEntry,
  type EvolutionCommitmentsEntry,
  type BusinessReferenceEntry,
  type MonthDetailItem,
  type AverageDelayPeriodItem,
  type NotaryRecord,
  type PefinRecord,
  type CollectionRecord,
  type CheckRecord,
  type JudgementFilingRecord,
  type BankruptRecord,
} from "../../../../types/company-detail";
import { SerasaReportPrint } from "./SerasaReportPrint";
import { PaymentHistoryPanel } from "./PaymentHistoryPanel";
import { AiAnalysisCard } from "./AiAnalysisCard";
import { CompanyNotesPanel } from "./CompanyNotesPanel";
import { CommercialInformationPanel } from "./CommercialInformationPanel";
import { CompanyDocumentsPanel } from "./CompanyDocumentsPanel";
import { CompanyBranchesPanel } from "./CompanyBranchesPanel";
import { useEnrichPersonSerasa } from "../../../../hooks/usePersonProfile";
import type { PersonAnalysisSummary } from "../../../../types/person-analysis";

// ─── Brand colours ────────────────────────────────────────────────────────────
const CNPJA_COLOR  = "#2956E0";
const SERASA_COLOR = "#E4006F";

// ─── Risk helpers ─────────────────────────────────────────────────────────────
const RISK_COLOR: Record<string, string> = {
  "1": "#DC2626", "2": "#EA580C", "3": "#D97706", "4": "#16A34A", "5": "#2563EB",
};
const RISK_BG: Record<string, string> = {
  "1": "#FEF2F2", "2": "#FFF7ED", "3": "#FFFBEB", "4": "#F0FDF4", "5": "#EFF6FF",
};
const RISK_DECISION: Record<string, string> = {
  "1": "Negar Crédito",
  "2": "Análise Criteriosa",
  "3": "Crédito Condicionado",
  "4": "Crédito Aprovado",
  "5": "Crédito Liberado",
};
function riskColorFor(rc: string | undefined) { return RISK_COLOR[rc ?? ""] ?? "#6B7280"; }
function riskDecisionFor(rc: string | undefined) { return RISK_DECISION[rc ?? ""] ?? "Análise Pendente"; }

// ─── General helpers ──────────────────────────────────────────────────────────
function formatCNPJ(raw: string) {
  return raw.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}
function formatCurrency(value: number) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}
function formatCount(value: string | number | undefined) {
  if (value == null || value === "") return "—";
  const numeric = typeof value === "number" ? value : Number(String(value).replace(/\D/g, ""));
  return Number.isFinite(numeric)
    ? new Intl.NumberFormat("pt-BR").format(numeric)
    : String(value);
}
function formatDate(raw: string | undefined) {
  if (!raw) return "-";
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [y, m, d] = raw.split("-");
    return `${d}/${m}/${y}`;
  }
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(raw)) {
    return raw;
  }
  const d = new Date(raw);
  return isNaN(d.getTime()) ? raw : d.toLocaleDateString("pt-BR");
}
function formatDateTime(raw: string | undefined, options?: { dateOnly?: boolean }) {
  if (!raw) return "—";

  const normalized = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(raw) ? `${raw}-03:00` : raw;
  const date = new Date(normalized);
  if (isNaN(date.getTime())) return raw;

  return new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo",
    ...(options?.dateOnly
      ? { day: "2-digit", month: "2-digit", year: "numeric" }
      : { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit" }),
  }).format(date);
}
function navigateToIndividualProfile(target: string) {
  if (typeof window === "undefined") return;
  window.location.assign(`/individuals/${target}`);
}
function buildContextQuery(fromPath: string, fromLabel?: string) {
  const params = new URLSearchParams({ from: fromPath });
  if (fromLabel) params.set("fromLabel", fromLabel);
  return params.toString();
}
function formatMonthYear(raw: string | undefined) {
  if (!raw) return "—";
  if (/^\d{4}-\d{2}$/.test(raw)) {
    const [year, month] = raw.split("-");
    const shortMonth = new Date(Number(year), Number(month) - 1, 1).toLocaleDateString("pt-BR", {
      month: "short",
    }).replace(".", "");
    return `${shortMonth}/${year.slice(-2)}`;
  }
  return raw;
}
function pendingIcon(msg: string | undefined) {
  if (!msg) return "⚪";
  const u = msg.toUpperCase();
  if (u.includes("NADA CONSTA")) return "✅";
  if (u.includes("NAO INFORMAD")) return "⚠️";
  return "ℹ️";
}

function buildReportFileName(name: string | undefined, fallback: string) {
  const safeName = (name || fallback).replace(/[/\\?%*:|"<>]/g, "").trim();
  return `${safeName || fallback}.pdf`;
}

async function extractErrorMessage(res: Response, fallback: string) {
  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    const data = await res.json().catch(() => ({}));
    return data.message || data.error || fallback;
  }

  const text = await res.text().catch(() => "");
  return text || fallback;
}

// ─── Sub-components ───────────────────────────────────────────────────────────

function PartnerDetailModal({ partner, director, onClose, pfSummary, companyCnpj, companyLabel }: {
  partner?: QSAPartner | null;
  director?: QSADirector | null;
  onClose: () => void;
  pfSummary?: PersonAnalysisSummary;
  companyCnpj?: string;
  companyLabel?: string;
}) {
  const pathname = usePathname();
  const item = partner ?? director;
  if (!item) return null;

  const partnerCpf = (item.documentId ?? "").replace(/\D/g, "");
  const isCpf = partnerCpf.length === 11;

  const rows: { label: string; value: string | number | boolean | undefined }[] = [
    { label: "Nome", value: item.name },
    { label: "Tipo Documento", value: item.documentType },
    { label: "Documento", value: item.documentId },
    { label: "Situação", value: item.status },
    { label: "Desde", value: item.sinceDate ? formatDate(item.sinceDate) : undefined },
    { label: "Nacionalidade", value: item.nationality },
    { label: "Restrição", value: item.restrictionSign ? "Sim ⚠" : "Não" },
    ...(partner ? [
      { label: "Capital Total (%)", value: partner.capitalTotalValue != null ? `${partner.capitalTotalValue}%` : undefined },
      { label: "Capital Votante (%)", value: partner.capitalVoterValue != null ? `${partner.capitalVoterValue}%` : undefined },
    ] : []),
    ...(director ? [
      { label: "Cargo", value: director.role },
      { label: "Estado Civil", value: director.maritalStatus },
      { label: "Consistência Doc.", value: director.documentConsistency != null ? (director.documentConsistency ? "Consistente" : "Inconsistente") : undefined },
      { label: "Atualização", value: director.informationUpdateDate ? formatDate(director.informationUpdateDate) : undefined },
    ] : []),
  ].filter(r => r.value != null && r.value !== "");

  const initials = item.name.substring(0, 2).toUpperCase();
  const isPartner = !!partner;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" />
      <div
        className="relative bg-surface-light dark:bg-surface-dark rounded-2xl shadow-2xl border border-border-light dark:border-border-dark w-full max-w-md max-h-[80vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start gap-4 p-6 border-b border-border-light dark:border-border-dark">
          <div className={`w-14 h-14 rounded-full flex items-center justify-center font-bold text-lg font-sans flex-shrink-0 ${
            item.restrictionSign
              ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
              : isPartner
              ? "bg-primary/10 text-primary"
              : "bg-secondary/20 text-secondary"
          }`}>
            {initials}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="font-sans font-bold text-grafite dark:text-white text-base leading-tight">{item.name}</h3>
            <p className="text-xs text-gray-500 font-sans mt-0.5">
              {isPartner ? "Sócio" : (director as QSADirector)?.role ?? "Administrador"}
            </p>
            {item.restrictionSign && (
              <span className="inline-flex items-center gap-1 mt-1.5 text-[10px] font-sans font-bold text-red-600 bg-red-50 dark:bg-red-900/20 px-2 py-0.5 rounded-full border border-red-200 dark:border-red-800">
                ⚠ Com restrição
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center rounded-full hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors flex-shrink-0"
          >
            <Icon name="close" className="text-base" />
          </button>
        </div>

        {/* Details */}
        <div className="p-6 space-y-3">
          {rows.map((row, i) => (
            <div key={i} className="flex items-start justify-between gap-4 py-2 border-b border-border-light/50 dark:border-border-dark/50 last:border-0">
              <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide flex-shrink-0">{row.label}</dt>
              <dd className="text-sm font-serif text-grafite dark:text-gray-200 text-right break-all">{String(row.value)}</dd>
            </div>
          ))}
        </div>

        {/* Consulta PF — somente sócios com CPF */}
        {isCpf && (
          <div className="px-6 pb-6 pt-0 border-t border-border-light dark:border-border-dark mt-0">
            <p className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-3 mt-4">
              Análise Pessoa Física
            </p>
            {pfSummary ? (
              <div className="flex items-center justify-between gap-3 p-3 rounded-xl bg-green-50 dark:bg-green-900/10 border border-green-200 dark:border-green-800">
                <div>
                  <p className="text-xs font-sans font-bold text-green-700 dark:text-green-400">
                    Consultado em {formatDateTime(pfSummary.consultaEm, { dateOnly: true })}
                  </p>
                  {pfSummary.hasNegative ? (
                    <p className="text-[10px] text-red-600 font-sans mt-0.5">
                      ⚠ {pfSummary.negativeTotalCount} restrição{pfSummary.negativeTotalCount !== 1 ? "ões" : ""} encontrada{pfSummary.negativeTotalCount !== 1 ? "s" : ""}
                    </p>
                  ) : (
                    <p className="text-[10px] text-green-600 font-sans mt-0.5">Sem restrições</p>
                  )}
                </div>
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    navigateToIndividualProfile(`${partnerCpf}?${buildContextQuery(pathname, companyLabel)}`);
                  }}
                  className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-sans font-bold text-white bg-primary hover:bg-primary/90 transition-colors flex-shrink-0"
                >
                  <Icon name="open_in_new" className="text-sm" />
                  Ver análise
                </button>
              </div>
            ) : (
              <div className="flex items-center justify-between gap-3 p-3 rounded-xl bg-gray-50 dark:bg-gray-800/50 border border-gray-200 dark:border-gray-700">
                <div>
                  <p className="text-xs font-sans font-bold text-gray-600 dark:text-gray-300">Nenhuma consulta realizada</p>
                  <p className="text-[10px] text-gray-400 font-sans mt-0.5">Custo: R$ 15,52 por consulta</p>
                </div>
                {companyCnpj && (
                  <PartnerPFBadge cpf={partnerCpf} pfSummary={undefined} companyCnpj={companyCnpj} companyLabel={companyLabel} inModal />
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Badge exibido em cada card de sócio indicando o status da análise PF.
 * - Se já consultado: link verde para /individuals/{cpf}
 * - Se não: botão para consultar Serasa PF
 */
function PartnerPFBadge({ cpf, pfSummary, companyCnpj, companyLabel, inModal = false }: {
  cpf: string;
  pfSummary: PersonAnalysisSummary | undefined;
  companyCnpj: string;
  companyLabel?: string;
  inModal?: boolean;
}) {
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const { mutate: consultPF, isPending } = useEnrichPersonSerasa(cpf, () => {
    queryClient.invalidateQueries({ queryKey: ["clientProfile", companyCnpj] });
  });

  if (pfSummary) {
    return (
      <button
        type="button"
        onClick={(event) => {
          event.stopPropagation();
          navigateToIndividualProfile(`${cpf}?${buildContextQuery(pathname, companyLabel)}`);
        }}
        className="inline-flex items-center gap-1 text-[10px] font-sans font-bold px-2 py-0.5 rounded-full bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400 border border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors"
        title="Ver análise PF"
      >
        <Icon name="person" style={{ fontSize: 10 }} />
        Ver PF
        {pfSummary.hasNegative && <span className="w-1.5 h-1.5 rounded-full bg-red-500 ml-0.5 flex-shrink-0" />}
      </button>
    );
  }

  if (inModal) {
    return (
      <button
        onClick={() => consultPF()}
        disabled={isPending}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-sans font-bold text-white transition-all disabled:opacity-50"
        style={{ background: "#E4006F" }}
        title="Consultar Serasa PF"
      >
        <Icon name={isPending ? "hourglass_empty" : "person_search"} className="text-sm" />
        {isPending ? "Consultando..." : "Consultar PF"}
      </button>
    );
  }

  return (
    <button
      onClick={() => consultPF()}
      disabled={isPending}
      className="inline-flex items-center gap-1 text-[10px] font-sans font-bold px-2 py-0.5 rounded-full bg-gray-50 text-gray-500 dark:bg-gray-800 dark:text-gray-400 border border-gray-200 dark:border-gray-700 hover:bg-primary/10 hover:text-primary hover:border-primary/20 transition-colors disabled:opacity-50"
      title="Consultar Serasa PF"
    >
      <Icon name={isPending ? "hourglass_empty" : "person_search"} style={{ fontSize: 10 }} />
      {isPending ? "..." : "Consultar PF"}
    </button>
  );
}

function DataProgressBar({ hasCnpja, hasSerasa }: { hasCnpja: boolean; hasSerasa: boolean }) {
  const pct = hasSerasa ? 100 : hasCnpja ? 66 : 33;
  const label = hasSerasa ? "100% Completo" : hasCnpja ? "66% — Enriquecimento OK" : "33% — Dados Base";
  const badgeClass = hasSerasa
    ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300 border-green-200 dark:border-green-800"
    : hasCnpja
    ? "bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-300 border-blue-200 dark:border-blue-800"
    : "bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300 border-amber-200 dark:border-amber-800";

  const steps = [
    { key: "base",   icon: "database",            label: "Dados Base",      done: true },
    { key: "cnpja",  icon: "domain_verification", label: "CNPJ Já",         done: hasCnpja },
    { key: "serasa", icon: "analytics",            label: "Serasa Experian", done: hasSerasa },
  ];

  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-4 shadow-sm border border-border-light dark:border-border-dark mb-8 print:hidden">
      <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <h3 className="text-sm font-sans font-bold text-gray-500 uppercase tracking-wide">Status de Coleta de Dados</h3>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-lg font-bold text-grafite dark:text-white font-sans">{label}</span>
            <span className={`text-xs px-2 py-0.5 rounded-full font-sans font-medium border ${badgeClass}`}>
              {hasSerasa ? "Dados Coletados" : hasCnpja ? "Enriquecimento Ok" : "Análise Pendente"}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-6">
          {steps.map((step) => (
            <div key={step.key} className="flex flex-col items-center gap-1">
              <div className={`w-10 h-10 rounded-full flex items-center justify-center border relative transition-colors ${
                step.done
                  ? "bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400 border-green-200 dark:border-green-800"
                  : "bg-gray-100 dark:bg-gray-800 text-gray-400 border-gray-200 dark:border-gray-700"
              }`}>
                <span className="material-symbols-outlined text-xl">{step.icon}</span>
                {step.done && (
                  <span className="absolute -bottom-1 -right-1 bg-green-500 border-2 border-surface-light dark:border-surface-dark w-4 h-4 rounded-full flex items-center justify-center">
                    <Icon name="check" size={10} className="text-white" />
                  </span>
                )}
              </div>
              <span className="text-xs font-sans font-medium text-gray-600 dark:text-gray-300 whitespace-nowrap">{step.label}</span>
            </div>
          ))}
        </div>
      </div>
      <div className="w-full bg-gray-200 dark:bg-gray-700 h-1.5 rounded-full mt-4 overflow-hidden">
        <div className="h-1.5 rounded-full transition-all duration-700 ease-out bg-gradient-to-r from-primary to-secondary" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

function MapEmbed({ mode, searchQuery, lat, lng }: { mode: "streetview" | "map"; searchQuery: string; lat?: number | null; lng?: number | null }) {
  const apiKey = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY;

  let src = "";
  const hasCoords = lat != null && lng != null && lat !== 0 && lng !== 0;

  if (apiKey) {
    if (mode === "streetview") {
      src = hasCoords
        ? `https://www.google.com/maps/embed/v1/streetview?key=${apiKey}&location=${lat},${lng}`
        : `https://www.google.com/maps/embed/v1/streetview?key=${apiKey}&location=${encodeURIComponent(searchQuery)}`;
    } else {
      src = hasCoords
        ? `https://www.google.com/maps/embed/v1/place?key=${apiKey}&q=${lat},${lng}&maptype=roadmap`
        : `https://www.google.com/maps/embed/v1/place?key=${apiKey}&q=${encodeURIComponent(searchQuery)}&maptype=roadmap`;
    }
  } else {
    // Fallback without API key
    if (mode === "streetview") {
      if (hasCoords) {
         src = `https://maps.google.com/maps?layer=c&cbll=${lat},${lng}&cbp=12,0,0,0,0&source=embed&output=svembed`;
      } else {
         src = `https://maps.google.com/maps?layer=c&q=${encodeURIComponent(searchQuery)}&source=embed&output=svembed`;
      }
    } else {
      if (hasCoords) {
         src = `https://maps.google.com/maps?q=${lat},${lng}&t=m&z=17&output=embed`;
      } else {
         src = `https://maps.google.com/maps?q=${encodeURIComponent(searchQuery)}&t=m&z=17&output=embed`;
      }
    }
  }

  return (
    <div className="rounded-lg overflow-hidden border border-border-light dark:border-border-dark h-full min-h-[160px] w-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
      {src ? (
        <iframe title={mode === "streetview" ? "Street View" : "Mapa"} src={src} width="100%" height="100%" style={{ border: 0 }} allowFullScreen loading="lazy" referrerPolicy="no-referrer-when-downgrade" />
      ) : (
        <span className="text-sm text-gray-400">Mapa Indisponível</span>
      )}
    </div>
  );
}

function NegativeCard({
  label, count, balance, fmt, hasDetail, isOpen, onToggle,
}: {
  label: string; count: number; balance: number; fmt: (v: number) => string;
  hasDetail?: boolean; isOpen?: boolean; onToggle?: () => void;
}) {
  const isClean = count === 0;
  return (
    <div
      className={`rounded-xl p-4 border transition-all ${
        isClean
          ? "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800"
          : isOpen
            ? "bg-red-100 dark:bg-red-900/20 border-red-500 dark:border-red-500 ring-2 ring-red-300 dark:ring-red-700"
            : "bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800"
      } ${hasDetail ? "cursor-pointer select-none" : ""}`}
      onClick={hasDetail ? onToggle : undefined}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-sans font-bold uppercase tracking-wide text-gray-500 dark:text-gray-400">{label}</span>
        <div className="flex items-center gap-1">
          <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
            isClean ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-300" : "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300"
          }`}>
            {isClean ? "Nada Consta" : `${count} ocorr.`}
          </span>
          {hasDetail && (
            <Icon name={isOpen ? "expand_less" : "expand_more"} className="text-base text-gray-400 dark:text-gray-500" />
          )}
        </div>
      </div>
      <p className={`text-3xl font-sans font-bold ${isClean ? "text-green-700 dark:text-green-400" : "text-red-700 dark:text-red-400"}`}>{count}</p>
      {balance > 0 && <p className="text-xs font-serif text-gray-500 dark:text-gray-400 mt-0.5">{fmt(balance)}</p>}
    </div>
  );
}

function NegDetailTable({ title, children, shownCount, totalCount }: {
  title: string; children: React.ReactNode;
  shownCount?: number; totalCount?: number;
}) {
  const hasMissing = totalCount != null && shownCount != null && shownCount < totalCount;
  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-5 shadow-sm border border-red-200 dark:border-red-800 mt-3">
      <p className="text-xs font-sans font-bold text-red-600 dark:text-red-400 uppercase tracking-wide mb-3 flex items-center gap-2">
        <Icon name="receipt_long" className="text-sm" />
        {title}
      </p>
      <div className="overflow-x-auto">{children}</div>
      {hasMissing && (
        <div className="mt-3 flex items-start gap-2 p-2.5 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-700">
          <Icon name="info" className="text-sm text-amber-600 dark:text-amber-400 mt-0.5 shrink-0" />
          <p className="text-xs font-sans text-amber-700 dark:text-amber-300">
            Exibindo <strong>{shownCount}</strong> de <strong>{totalCount}</strong> ocorrência(s). A Serasa pode ter registros adicionais não retornados nesta consulta — o valor total exibido no card reflete todos os registros cadastrados.
          </p>
        </div>
      )}
    </div>
  );
}

function PefinRefinTable({ records, fmt }: { records: PefinRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Credor</th>
          <th className="text-left pb-2 pr-3">Contrato</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200">{r.creditorName ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{r.contractId ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{r.legalNature ?? r.legalNatureId ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function NotaryDetailTable({ records, fmt }: { records: NotaryRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-right pb-2 pr-3">Valor</th>
          <th className="text-left pb-2 pr-3">Cartório</th>
          <th className="text-left pb-2">Cidade / UF</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500">{r.officeNumber ? `Nº ${r.officeNumber}` : "—"}</td>
            <td className="py-1.5 font-serif text-grafite dark:text-gray-200">{[r.city, r.federalUnit].filter(Boolean).join(" / ") || "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function CollectionDetailTable({ records, fmt }: { records: CollectionRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Credor</th>
          <th className="text-left pb-2 pr-3">Contrato</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200">{r.creditorName ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{r.contractId ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function CheckDetailTable({ records, fmt }: { records: CheckRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Banco</th>
          <th className="text-left pb-2 pr-3">Agência</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500">{r.bankCode ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500">{r.agency ?? "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function JudgementFilingDetailTable({ records, fmt }: { records: JudgementFilingRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">Dist / Vara</th>
          <th className="text-left pb-2 pr-3">Cidade / UF</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500">{r.legalNature ?? r.legalNatureId ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{[r.distributor, r.civilCourt].filter(Boolean).join(" / ") || "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200">{[r.city, r.state].filter(Boolean).join(" / ") || "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function BankruptDetailTable({ records, fmt }: { records: BankruptRecord[]; fmt: (v: number) => string }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
          <th className="text-left pb-2 pr-3">Ocorrência</th>
          <th className="text-left pb-2 pr-3">Inclusão</th>
          <th className="text-left pb-2 pr-3">Natureza</th>
          <th className="text-left pb-2 pr-3">Cidade / UF</th>
          <th className="text-right pb-2">Valor</th>
        </tr>
      </thead>
      <tbody className="divide-y divide-border-light dark:divide-border-dark">
        {records.map((r, i) => (
          <tr key={i}>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500 whitespace-nowrap">{formatDate(r.inclusionDate)}</td>
            <td className="py-1.5 pr-3 font-serif text-gray-500">{r.legalNature ?? r.legalNatureId ?? "—"}</td>
            <td className="py-1.5 pr-3 font-serif text-grafite dark:text-gray-200">{[r.city, r.state].filter(Boolean).join(" / ") || "—"}</td>
            <td className="py-1.5 text-right font-sans font-bold text-red-600 dark:text-red-400 whitespace-nowrap">{r.amount != null ? fmt(r.amount) : "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function RiskBadge({ riskClass }: { riskClass: string }) {
  const color = riskColorFor(riskClass);
  const bg = RISK_BG[riskClass] ?? "#F9FAFB";
  return (
    <span className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-sans font-bold border"
      style={{ color, backgroundColor: bg, borderColor: color + "55" }}>
      <span style={{ width: 8, height: 8, borderRadius: "50%", background: color, display: "inline-block" }} />
      {riskLabel(riskClass)}
    </span>
  );
}

function PendingRow({ label, message, date }: { label: string; message?: string; date?: string }) {
  const icon = pendingIcon(message);
  const isClean = message?.toUpperCase().includes("NADA CONSTA");
  return (
    <div className={`rounded-lg p-3 border ${
      isClean
        ? "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800"
        : "bg-amber-50 dark:bg-amber-900/10 border-amber-200 dark:border-amber-800"
    }`}>
      <div className="flex items-start gap-2">
        <span className="text-base mt-0.5 flex-shrink-0">{icon}</span>
        <div className="min-w-0">
          <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-wide">{label}</p>
          <p className="text-sm font-serif text-grafite dark:text-gray-200 mt-0.5 break-words">{message ?? "Não informado"}</p>
          {date && <p className="text-[10px] text-gray-400 mt-0.5">Atualizado: {date}</p>}
        </div>
      </div>
    </div>
  );
}

// ─── Balance/DRE helper ───────────────────────────────────────────────────────
function fmtMil(val: number | undefined): string {
  if (val == null) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 }).format(val * 1000);
}

// ─── SerasaConclusions ────────────────────────────────────────────────────────
function SerasaConclusions({ items }: { items: { conclusionLine: string }[] }) {
  if (!items.length) return null;
  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
      <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
        <Icon name="psychology" className="text-lg" />
        Conclusões Econômico-Financeiras
      </h2>
      <ol className="space-y-2">
        {items.map((item, i) => (
          <li key={i} className="flex gap-3 text-sm font-serif text-grafite dark:text-gray-200 leading-relaxed">
            <span className="font-bold text-primary/60 flex-shrink-0 tabular-nums">{i + 1}.</span>
            <span>{item.conclusionLine.trim()}</span>
          </li>
        ))}
      </ol>
    </div>
  );
}

// ─── FinancialIndices ─────────────────────────────────────────────────────────
function FinancialIndices({ items, dates }: { items: Record<string, unknown>[]; dates?: Record<string, string | undefined> }) {
  if (!items.length) return null;

  // Group by category
  const categories: { title: string; rows: Record<string, unknown>[] }[] = [];
  for (const item of items) {
    if (item.descriptionCategoryIndex) {
      categories.push({ title: item.descriptionCategoryIndex as string, rows: [] });
    }
    categories[categories.length - 1]?.rows.push(item);
  }

  const d1 = dates?.earliestDate     ? new Date(dates.earliestDate).getFullYear()     : "T-2";
  const d2 = dates?.intermediateDate ? new Date(dates.intermediateDate).getFullYear() : "T-1";
  const d3 = dates?.mostRecentDate   ? new Date(dates.mostRecentDate).getFullYear()   : "T0";

  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
      <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
        <Icon name="bar_chart" className="text-lg" />
        Índices Financeiros
      </h2>
      <div className="space-y-5">
        {categories.map((cat, ci) => (
          <div key={ci}>
            <p className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-widest mb-2 pb-1 border-b border-border-light dark:border-border-dark">
              {cat.title}
            </p>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-[10px] font-sans font-bold text-gray-400 uppercase">
                    <th className="text-left pb-1 pr-4">Índice</th>
                    <th className="text-right pb-1 px-2">{d1}</th>
                    <th className="text-right pb-1 px-2">Setor</th>
                    <th className="text-right pb-1 px-2">{d2}</th>
                    <th className="text-right pb-1 px-2">Setor</th>
                    <th className="text-right pb-1 px-2">{d3}</th>
                    <th className="text-right pb-1 pl-2">Setor</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-light dark:divide-border-dark">
                  {cat.rows.map((row: Record<string, unknown>, ri) => (
                    <tr key={ri}>
                      <td className="py-1.5 pr-4 font-serif text-grafite dark:text-gray-200 text-xs">{row.indexDescription as string}</td>
                      <td className="py-1.5 px-2 text-right font-sans font-bold text-grafite dark:text-gray-200 text-xs">{(row.oldBalanceIndex as string) ?? "—"}</td>
                      <td className="py-1.5 px-2 text-right font-serif text-gray-400 text-xs">{(row.oldPatternIndex as string) ?? "—"}</td>
                      <td className="py-1.5 px-2 text-right font-sans font-bold text-grafite dark:text-gray-200 text-xs">{(row.intermediaryBalanceIndex as string) ?? "—"}</td>
                      <td className="py-1.5 px-2 text-right font-serif text-gray-400 text-xs">{(row.intermediaryPatternIndex as string) ?? "—"}</td>
                      <td className="py-1.5 px-2 text-right font-sans font-bold text-grafite dark:text-gray-200 text-xs">{(row.recentBalanceIndex as string) ?? "—"}</td>
                      <td className="py-1.5 pl-2 text-right font-serif text-gray-400 text-xs">{(row.recentPatternIndex as string) ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── AccountingSummary (DRE highlights) ───────────────────────────────────────
const DRE_HIGHLIGHTS = new Set([
  "FATURAMENTO BRUTO", "FATURAM LIQUIDO", "RESULTADO BRUTO",
  "GIR DA ATIVIDADE (EBITDA)", "RESULT EXERCICIO", "RESULTADO LIQUIDO",
]);

function AccountingSummary({ items, statements, dates }: {
  items: Record<string, unknown>[];
  statements?: { lastStatement?: string; valuesIn?: string };
  dates?: Record<string, string | undefined>;
}) {
  const highlighted = items.filter(it => DRE_HIGHLIGHTS.has((it.accountName as string) ?? ""));
  if (!highlighted.length) return null;

  const d1 = dates?.earliestDate     ? new Date(dates.earliestDate).getFullYear()     : "T-2";
  const d2 = dates?.intermediateDate ? new Date(dates.intermediateDate).getFullYear() : "T-1";
  const d3 = dates?.mostRecentDate   ? new Date(dates.mostRecentDate).getFullYear()   : "T0";
  const unit = statements?.valuesIn ? `(em ${statements.valuesIn.toLowerCase()})` : "";

  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
      <h2 className="font-sans font-bold text-base text-primary mb-1 flex items-center gap-2">
        <Icon name="table_chart" className="text-lg" />
        DRE — Demonstrativo de Resultados
      </h2>
      {unit && <p className="text-[10px] text-gray-400 font-serif mb-4">{unit} · {statements?.lastStatement}</p>}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
              <th className="text-left pb-2 pr-4">Conta</th>
              <th className="text-right pb-2 px-3">{d1}</th>
              <th className="text-right pb-2 px-3">{d2}</th>
              <th className="text-right pb-2 pl-3">{d3}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-light dark:divide-border-dark">
            {highlighted.map((item: Record<string, unknown>, i) => {
              const isEbitda = (item.accountName as string)?.includes("EBITDA");
              const isResult = item.accountName === "RESULTADO LIQUIDO" || item.accountName === "RESULT EXERCICIO";
              return (
                <tr key={i} className={isEbitda || isResult ? "bg-primary/5 dark:bg-primary/10" : ""}>
                  <td className={`py-2 pr-4 font-sans text-xs ${isEbitda || isResult ? "font-bold text-primary" : "font-medium text-grafite dark:text-gray-200"}`}>
                    {item.accountName as string}
                  </td>
                  <td className="py-2 px-3 text-right font-serif text-xs text-gray-500">{fmtMil(item.oldValue as number | undefined)}</td>
                  <td className="py-2 px-3 text-right font-serif text-xs text-gray-500">{fmtMil(item.intermediateValue as number | undefined)}</td>
                  <td className={`py-2 pl-3 text-right text-xs font-sans font-bold ${
                    ((item.recentValue as number) ?? 0) >= 0 ? "text-green-700 dark:text-green-400" : "text-red-600 dark:text-red-400"
                  }`}>{fmtMil(item.recentValue as number | undefined)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ─── BankReferencesCard ───────────────────────────────────────────────────────
function BankReferencesCard({ items, message, updateDate }: {
  items?: { institution?: string; compensationCode?: string; agency?: string }[];
  message?: string;
  updateDate?: string;
}) {
  if (!items?.length) return null;
  return (
    <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
      <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
        <Icon name="account_balance" className="text-lg" />
        Referências Bancárias
        {updateDate && <span className="ml-auto text-xs font-sans text-gray-400">Atualizado: {updateDate}</span>}
      </h2>
      {message && (
        <p className="text-xs font-serif text-gray-500 mb-3">{message}</p>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border-light dark:border-border-dark text-[10px] font-sans font-bold text-gray-400 uppercase">
              <th className="text-left pb-2 pr-4">Banco</th>
              <th className="text-left pb-2 pr-4">Código</th>
              <th className="text-left pb-2">Agência</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border-light dark:divide-border-dark">
            {items.map((ref, i) => (
              <tr key={i}>
                <td className="py-2 pr-4 font-serif text-grafite dark:text-gray-200">{ref.institution ?? "—"}</td>
                <td className="py-2 pr-4 font-serif text-gray-500">{ref.compensationCode ?? "—"}</td>
                <td className="py-2 font-serif text-gray-500">{ref.agency ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function ClientDashboardPage() {
  const params = useParams();
  const router = useRouter();
  const searchParams = useSearchParams();
  const cnpj = params.cnpj as string;
  const cleanCnpj = cnpj ? cnpj.replace(/\D/g, "") : "";

  const [selectedAnalysisId, setSelectedAnalysisId] = useState<number | null>(null);
  const { profile, isLoading, isError, refreshCnpja, isRefreshingCnpja, refreshSerasa, isRefreshingSerasa } = useClientProfile(cnpj, selectedAnalysisId);
  const [pracaFrom, setPracaFrom] = useState("");
  const [pracaTo, setPracaTo] = useState("");
  const [pracaDecisao, setPracaDecisao] = useState("");
  const [pracaPage, setPracaPage] = useState(0);
  const [pracaSize, setPracaSize] = useState(10);
  const [pracaSelected, setPracaSelected] = useState<PaymentPlaceEntry | null>(null);
  const [pracaViewer, setPracaViewer] = useState<PaymentPlaceEntry | null>(null);
  const reopenEntryMutation = useReopenPaymentPlaceEntry();
  const { data: pracaSummary } = usePaymentPlaceCompany(cnpj, {
    from: pracaFrom || undefined,
    to: pracaTo || undefined,
    decisao: pracaDecisao || undefined,
    page: pracaPage,
    size: pracaSize,
  });

  const [selectedPartner, setSelectedPartner] = useState<QSAPartner | null>(null);
  const [selectedDirector, setSelectedDirector] = useState<QSADirector | null>(null);
  const [openNegCards, setOpenNegCards] = useState<Record<string, boolean>>({});
  const [showInactivePartners, setShowInactivePartners] = useState(false);
  const [showPredecessors, setShowPredecessors] = useState(false);
  const [qsaCollapsed, setQsaCollapsed] = useState(false);
  const [showAllAdmins, setShowAllAdmins] = useState(false);
  const [showAllSocios, setShowAllSocios] = useState(false);
  const [isSendingEmail, setIsSendingEmail] = useState(false);
  const [emailNotificacaoCedente] = useState<boolean>(() => {
    if (typeof window !== "undefined") {
      const stored = localStorage.getItem("serasa_email_notificacao");
      return stored === null ? true : stored === "true";
    }
    return true;
  });

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <p className="text-gray-500 font-sans flex items-center gap-2">
          <Icon name="sync" className="animate-spin" /> Carregando dados...
        </p>
      </div>
    );
  }

  if (isError || !profile) {
    return (
      <div className="flex h-64 items-center justify-center">
        <p className="text-red-500 font-sans">Falha ao carregar dados do cliente.</p>
      </div>
    );
  }

  const { companyDetail, creditAnalysis } = profile;
  const backTarget = searchParams.get("from");
  const backTargetLabel = searchParams.get("fromLabel");
  const ca  = creditAnalysis;
  const cr  = ca?.creditRatingDetails;
  const companyLabel = cr?.companyName || companyDetail?.companyName || profile?.client?.name || "Empresa";
  const qsa = ca?.partnerDetails;
  const neg = ca?.negativeSummary;
  const inq = ca?.inquiryHistory;
  const ph   = ca?.paymentHistory;

  const hasCnpjaData  = !!(companyDetail?.latitude != null || companyDetail?.longitude != null);
  const hasSerasaData = !!ca;

  // Endereço — prioriza dados Serasa (identificationReport.address), fallback CNPJ Já
  const serasaAddr   = cr?.address?.addressLine;
  const serasaCity   = cr?.address?.city;
  const serasaState  = cr?.address?.state;
  const serasaDistr  = cr?.address?.district;
  const serasaZip    = cr?.address?.zipCode;

  let streetPart = serasaAddr || companyDetail?.street || "";
  const numberPart = companyDetail?.number || "";
  if (!serasaAddr && streetPart && numberPart && !new RegExp(`\\b${numberPart}\\s*$`, "i").test(streetPart)) {
    streetPart = `${streetPart}, ${numberPart}`;
  }

  const city     = serasaCity  || companyDetail?.city  || "";
  const state    = serasaState || companyDetail?.state || "";
  const district = serasaDistr || companyDetail?.district || "";
  const zip      = serasaZip   || companyDetail?.zip || companyDetail?.zipCode || "";

  const fullAddressParts = [
    streetPart,
    district,
    city && state ? `${city} — ${state}` : city || state,
    zip ? `CEP: ${zip}` : "",
  ].filter(Boolean);

  const mapsSearchQuery = [streetPart, district, city, state].filter(Boolean).join(", ");

  const partners:  QSAPartner[]  = qsa?.partnerCompleteReport?.partnersList  ?? [];
  const directors: QSADirector[] = qsa?.directorCompleteReport?.directorsList ?? [];
  const activePartners = partners.filter((partner) => !partner.status?.toUpperCase().includes("BAIX"));
  const inactivePartners = partners.filter((partner) => partner.status?.toUpperCase().includes("BAIX"));
  const visiblePartners = showInactivePartners ? [...activePartners, ...inactivePartners] : activePartners;
  // QSA: esquerda = Administradores, direita = Sócios. Serasa (partners/directors) tem prioridade;
  // senão usa companyDetail.members, separando por papel.
  const isAdminRole = (role?: string | null) => (role ?? "").toLowerCase().includes("admin");
  const qsaMembers: CompanyMember[] = companyDetail?.members ?? [];
  const memberRoleText = (m: CompanyMember) => (typeof m.role === "string" ? m.role : m.role?.text) ?? "";
  const memberAdmins = qsaMembers.filter((m) => isAdminRole(memberRoleText(m)));
  const memberSocios = qsaMembers.filter((m) => !isAdminRole(memberRoleText(m)));
  const adminCount = directors.length > 0 ? directors.length : memberAdmins.length;
  const socioCount = partners.length > 0 ? visiblePartners.length : memberSocios.length;
  const hasAdmins = adminCount > 0;
  const hasSocios = socioCount > 0;
  const visibleDirectors = showAllAdmins ? directors : directors.slice(0, 2);
  const visibleMemberAdmins = showAllAdmins ? memberAdmins : memberAdmins.slice(0, 2);
  const visibleSocioPartners = showAllSocios ? visiblePartners : visiblePartners.slice(0, 2);
  const visibleMemberSocios = showAllSocios ? memberSocios : memberSocios.slice(0, 2);
  // Fallback members → estrutura do modal (sócio/admin), para ficarem clicáveis com consulta PF.
  const memberDoc = (m: CompanyMember) => (m.documentNumber ?? "").replace(/\D/g, "");
  const memberDocType = (doc: string) => (doc.length === 11 ? "CPF" : doc.length === 14 ? "CNPJ" : undefined);
  const memberToPartner = (m: CompanyMember): QSAPartner => {
    const doc = memberDoc(m);
    return { name: m.person?.name || m.name || "Sócio", documentId: doc || undefined, documentType: memberDocType(doc) };
  };
  const memberToDirector = (m: CompanyMember): QSADirector => {
    const doc = memberDoc(m);
    return { name: m.person?.name || m.name || "Administrador", documentId: doc || undefined, documentType: memberDocType(doc), role: memberRoleText(m) };
  };
  const inquiries  = inq?.inquiryCompanyResponse?.results ?? [];
  const inquiryHistory = inq?.inquiryCompanyResponse?.quantity?.historical ?? [];
  const maxInquiryOccurrences = inquiryHistory.reduce((max, item) => Math.max(max, item.occurrences ?? 0), 0);
  const analysisHistory: CreditAnalysisHistoryItem[] = profile.analysisHistory ?? [];
  const predecessors: CompanyPredecessor[] = cr?.predecessorList ?? [];
  const visiblePredecessors = showPredecessors ? predecessors : predecessors.slice(0, 5);
  const hiddenPredecessorCount = Math.max(predecessors.length - 5, 0);
  const latestAnalysisId = analysisHistory[0]?.id ?? null;
  const fmt = formatCurrency;
  const handleGoPortfolio = () => {
    router.push("/");
  };
  const handleGoBack = () => {
    if (backTarget?.startsWith("/")) {
      router.push(backTarget);
      return;
    }
    if (typeof window !== "undefined" && window.history.length > 1) {
      router.back();
      return;
    }
    router.push("/");
  };
  const contextualBackLabel = backTarget?.startsWith("/individuals/")
    ? `Voltar para ${backTargetLabel || "Pessoa Física"}`
    : backTarget?.startsWith("/")
      ? `Voltar para ${backTargetLabel || "origem"}`
      : null;

  const handleDeleteCompany = async () => {
    if (!confirm("Tem certeza que deseja excluir esta empresa? Esta ação não pode ser desfeita.")) return;

    try {
      const token = localStorage.getItem("serasa_token");
      const headers: HeadersInit = {
        "Content-Type": "application/json",
      };
      if (token) headers["Authorization"] = `Bearer ${token}`;

      const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
      const res = await fetch(`${apiUrl}/company/${cnpj}`, {
        method: "DELETE",
        headers,
      });

      if (res.ok) router.push("/");
      else alert("Erro ao excluir. Tente novamente.");
    } catch {
      alert("Erro de conexão ao excluir.");
    }
  };

  const handleGeneratePdf = async () => {
    try {
      const token = localStorage.getItem("serasa_token");
      const fileName = buildReportFileName(
        cr?.companyName || companyDetail?.companyName || profile?.client?.name,
        cnpj
      );
      const res = await fetch(`/api/company/${cnpj}/report-pdf`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao gerar PDF."));
      }

      const pdfBlob = await res.blob();
      const url = URL.createObjectURL(pdfBlob);
      const link = document.createElement("a");
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error(err);
      alert(err instanceof Error ? err.message : "Erro ao gerar PDF.");
    }
  };

  const handleSendEmailAnalysis = async () => {
    if (!confirm("Enviar e-mail de análise para a equipe comercial usando o PDF oficial do sistema?")) return;
    setIsSendingEmail(true);
    try {
      const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
      const token = localStorage.getItem("serasa_token");
      const pdfResponse = await fetch(`/api/company/${cnpj}/report-pdf`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (!pdfResponse.ok) {
        throw new Error(await extractErrorMessage(pdfResponse, "Erro ao gerar PDF."));
      }

      const pdfBlob = await pdfResponse.blob();
      const fileName = buildReportFileName(
        cr?.companyName || companyDetail?.companyName || profile?.client?.name,
        cnpj
      );
      const formData = new FormData();
      formData.append("pdf", new File([pdfBlob], fileName, { type: "application/pdf" }));

      const emailHeaders: HeadersInit = {};
      if (token) emailHeaders["Authorization"] = `Bearer ${token}`;

      const emailResponse = await fetch(`${apiUrl}/company/${cnpj}/email-cedente`, {
        method: "POST",
        headers: emailHeaders,
        body: formData,
      });

      if (emailResponse.ok) {
        alert("E-mail de análise enviado com sucesso!");
      } else {
        alert(await extractErrorMessage(emailResponse, "Erro ao enviar e-mail."));
      }
    } catch (err) {
      console.error(err);
      alert("Erro ao gerar PDF ou enviar e-mail.");
    } finally {
      setIsSendingEmail(false);
    }
  };

  return (
    <>
      {/* ── Relatório Serasa completo (apenas no print) ───────────────────── */}
      <SerasaReportPrint profile={profile} />

      {/* ── Page Header (screen only) ─────────────────────────────────────── */}
      <div className="flex flex-col xl:flex-row justify-between items-start xl:items-start mb-6 gap-4 print:hidden">
        <div className="min-w-0">
          <div className="mb-3 flex flex-col items-start gap-2">
            <button onClick={handleGoPortfolio} className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-primary dark:text-gray-400 dark:hover:text-white transition-colors">
              <Icon name="arrow_back" className="text-base" />
              Voltar à Carteira
            </button>
            {contextualBackLabel && (
              <button onClick={handleGoBack} className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-primary dark:text-gray-400 dark:hover:text-white transition-colors">
                <Icon name="arrow_back" className="text-base" />
                {contextualBackLabel}
              </button>
            )}
          </div>
          {hasSerasaData && ca?.consultaEm && (
            <div className="mb-2">
                <span className="text-gray-400 text-xs font-sans">
                Consulta Serasa: {formatDateTime(ca.consultaEm)}
              </span>
            </div>
          )}
          <h1 className="text-3xl font-sans font-bold text-grafite dark:text-white">
            {cr?.companyName || companyDetail?.companyName || profile?.client?.name || "Empresa"}
          </h1>
          {(cr?.companyAlias || companyDetail?.alias) && (
            <p className="text-sm text-gray-500 font-serif mt-0.5">
              {cr?.companyAlias || companyDetail?.alias}
            </p>
          )}
        </div>

        <div className="w-full xl:w-auto xl:max-w-[440px]">
          <div className="rounded-2xl border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark p-3 shadow-sm">
            <div className="flex flex-col sm:flex-row sm:flex-wrap gap-2">
              <button
                onClick={() => refreshCnpja()}
                disabled={isRefreshingCnpja}
                style={{ backgroundColor: CNPJA_COLOR }}
                className="inline-flex items-center justify-center gap-2 h-10 px-3.5 rounded-xl text-white font-sans font-semibold text-sm shadow-[0_1px_2px_rgba(0,0,0,0.08),_0_2px_6px_rgba(41,86,224,0.22)] hover:-translate-y-[1px] hover:brightness-110 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none disabled:brightness-100"
              >
                <Icon name="domain" size={18} className={`${isRefreshingCnpja ? "animate-spin" : ""}`} />
                {isRefreshingCnpja ? "Consultando..." : "CNPJ Já"}
              </button>
              <button
                onClick={() => refreshSerasa()}
                disabled={isRefreshingSerasa}
                style={{ backgroundColor: SERASA_COLOR }}
                className="inline-flex items-center justify-center gap-2 h-10 px-3.5 rounded-xl text-white font-sans font-semibold text-sm shadow-[0_1px_2px_rgba(0,0,0,0.08),_0_2px_6px_rgba(228,0,111,0.22)] hover:-translate-y-[1px] hover:brightness-110 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none disabled:brightness-100"
              >
                <Icon name="security" size={18} className={`${isRefreshingSerasa ? "animate-spin" : ""}`} />
                {isRefreshingSerasa ? "Consultando..." : "Serasa"}
              </button>
              <details className="relative sm:ml-auto">
                <summary className="list-none inline-flex items-center justify-center gap-2 h-10 px-3.5 rounded-xl border border-gray-300 bg-white text-grafite dark:border-gray-600 dark:bg-gray-800 dark:text-white font-sans font-semibold text-sm shadow-sm hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors cursor-pointer">
                  <Icon name="more_horiz" size={18} />
                  Mais ações
                </summary>
                <div className="absolute right-0 top-12 z-20 w-56 rounded-2xl border border-border-light dark:border-border-dark bg-white dark:bg-gray-900 shadow-xl p-2">
                  <button
                    onClick={handleGeneratePdf}
                    className="w-full text-left inline-flex items-center gap-2 px-3 py-2.5 rounded-xl text-sm font-sans font-medium text-grafite dark:text-white hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                  >
                    <Icon name="picture_as_pdf" size={18} />
                    Gerar PDF
                  </button>
                  {ca?.visaoCedente === "SIM" && emailNotificacaoCedente && (
                    <button
                      onClick={handleSendEmailAnalysis}
                      disabled={isSendingEmail}
                      className="w-full text-left inline-flex items-center gap-2 px-3 py-2.5 rounded-xl text-sm font-sans font-medium text-grafite dark:text-white hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors disabled:opacity-50"
                    >
                      <Icon name={isSendingEmail ? "sync" : "mail"} size={18} className={`${isSendingEmail ? "animate-spin" : ""}`} />
                      {isSendingEmail ? "Enviando e-mail..." : "Enviar análise por e-mail"}
                    </button>
                  )}
                </div>
              </details>
            </div>
          </div>
        </div>
      </div>

      {analysisHistory.length > 0 && (
        <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-5 shadow-sm border border-border-light dark:border-border-dark print:hidden mb-6">
          <div className="flex items-center justify-between gap-4 mb-4">
            <div>
              <h2 className="font-sans font-bold text-base text-primary flex items-center gap-2">
                <Icon name="history" className="text-lg" />
                Histórico de Consultas
              </h2>
              <p className="text-xs text-gray-500 font-sans mt-1">
                As consultas ficam salvas no banco. Clique em uma versão para carregá-la na tela.
              </p>
            </div>
            <span className="text-xs text-gray-400 font-sans">
              {analysisHistory.length} consulta{analysisHistory.length !== 1 ? "s" : ""}
            </span>
          </div>

          <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
            {analysisHistory.map((item, index) => {
              const isLatest = item.id === latestAnalysisId;
              const isSelected = selectedAnalysisId == null ? isLatest : selectedAnalysisId === item.id;

              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setSelectedAnalysisId(isLatest ? null : item.id)}
                  className={`w-full text-left rounded-lg border px-4 py-3 transition-all ${
                    isSelected
                      ? "border-primary bg-primary/5 dark:bg-primary/10"
                      : "border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark hover:border-primary/40"
                  }`}
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-sans font-bold text-grafite dark:text-white">
                          Consulta {analysisHistory.length - index}
                        </p>
                        {isLatest && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-sans font-bold bg-green-100 text-green-700 dark:bg-green-900/20 dark:text-green-400">
                            Mais recente
                          </span>
                        )}
                        {isSelected && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-sans font-bold bg-primary/10 text-primary">
                            Em tela
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-500 font-sans mt-1">
                        {item.consultaEm ? formatDateTime(item.consultaEm) : "Data não disponível"}
                      </p>
                      <div className="flex flex-wrap items-center gap-2 mt-2">
                        {item.riskClass && (
                          <span className="inline-flex items-center px-2 py-1 rounded-md text-[11px] font-sans font-bold bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">
                            Risco {riskLabel(item.riskClass)}
                          </span>
                        )}
                        {item.visaoCedente && (
                          <span className={`inline-flex items-center px-2 py-1 rounded-md text-[11px] font-sans font-bold ${
                            item.visaoCedente === "SIM"
                              ? "bg-green-100 text-green-700 dark:bg-green-900/20 dark:text-green-400"
                              : item.visaoCedente === "NAO"
                                ? "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300"
                                : "bg-amber-100 text-amber-700 dark:bg-amber-900/20 dark:text-amber-400"
                          }`}>
                            Visão Cedente: {item.visaoCedente}
                          </span>
                        )}
                        {item.status && (
                          <span className="inline-flex items-center px-2 py-1 rounded-md text-[11px] font-sans font-bold bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">
                            {item.status}
                          </span>
                        )}
                      </div>
                    </div>
                    <Icon name="chevron_right" className={`text-sm flex-shrink-0 mt-1 ${ isSelected ? "text-primary" : "text-gray-300" }`} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* ── Conteúdo visível apenas na tela (oculto no PDF) ─────────────────── */}
      <div className="print:hidden">

      {/* ── Progress Bar ──────────────────────────────────────────────────── */}
      <DataProgressBar hasCnpja={hasCnpjaData} hasSerasa={hasSerasaData} />

      {/* ── Praça de Pagamento: resumo sacado/cedente ─────────────────────── */}
      {pracaSummary && pracaSummary.totalCount > 0 ? (
        <div className="mb-6 rounded-xl border border-border-light bg-surface-light p-5 shadow-sm dark:border-border-dark dark:bg-surface-dark print:hidden">
          <div className="mb-3 flex items-center gap-2">
            <Icon name="swap_horiz" className="text-primary" />
            <h2 className="font-sans text-base font-bold text-primary">Praça de Pagamento — visão acumulada</h2>
            <span className="ml-auto text-xs text-gray-400">{pracaSummary.totalCount} títulos decididos</span>
          </div>
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-600/30 dark:bg-slate-700/20">
            <p className="text-[11px] font-bold uppercase tracking-wide text-slate-700 dark:text-slate-300">Visão Cedente</p>
            <p className="mt-1 text-2xl font-bold text-slate-700 dark:text-slate-200">{formatCurrency(pracaSummary.cedenteValue)}</p>
            <p className="text-xs text-gray-500">{pracaSummary.cedenteCount} título(s) pagos pelo cedente</p>
          </div>
        </div>
      ) : null}

      {/* ── Row 1: identificação + endereço ───────────────────────────────── */}
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mb-6">

        {/* Col 1 — Identificação */}
        <div className="flex flex-col">
          <div className="flex flex-1 flex-col bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-sans font-bold text-base text-primary flex items-center gap-2">
                <Icon name="business" className="text-lg" /> Identificação
              </h2>
              <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-sans font-bold border shadow-sm ${
                companyDetail?.statusText === "Ativa"
                  ? "bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-300 border-green-300 dark:border-green-700"
                  : "bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-300 border-red-300 dark:border-red-700"
              }`}>
                <span className={`w-2 h-2 rounded-full flex-shrink-0 ${companyDetail?.statusText === "Ativa" ? "bg-green-500 animate-pulse" : "bg-red-500"}`} />
                {companyDetail?.statusText ?? "Não informada"}
              </span>
            </div>
            <dl className="space-y-3">
              <div>
                <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">CNPJ</dt>
                <dd className="text-sm font-serif text-grafite dark:text-gray-200 font-medium">
                  {formatCNPJ(ca?.cnpj || companyDetail?.documentNumber || "")}
                </dd>
              </div>
              <ClientCodeField cnpj={cnpj} value={profile?.client?.clientCode} origin={profile?.client?.origin} />
              <div>
                <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Razão Social</dt>
                <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                  {cr?.companyName || companyDetail?.companyName || profile?.client?.name || "-"}
                </dd>
              </div>
              {(cr?.companyAlias || companyDetail?.alias) && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Nome Fantasia</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                    {cr?.companyAlias || companyDetail?.alias}
                  </dd>
                </div>
              )}
              {hasSerasaData && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Funcionários</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                    {formatCount(cr?.numberEmployees)}
                  </dd>
                </div>
              )}
              {hasSerasaData && (
                <div className="rounded-lg border-l-4 border-secondary bg-secondary/5 px-3 py-2.5 dark:bg-secondary/10">
                  <dt className="text-[10px] font-sans font-bold uppercase tracking-wider text-secondary mb-0.5">Regime Tributário</dt>
                  <dd className="text-base font-sans font-bold text-grafite dark:text-white">
                    {cr?.taxOption || "—"}
                  </dd>
                </div>
              )}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Fundação</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                    {formatDate(cr?.companyFoundation || companyDetail?.founded)}
                  </dd>
                </div>
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Capital Social</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                    {qsa?.companyData?.socialCapitalValue
                      ? fmt(qsa.companyData.socialCapitalValue)
                      : companyDetail?.companyEquity
                      ? fmt(companyDetail.companyEquity)
                      : "—"}
                  </dd>
                </div>
              </div>
              {hasSerasaData && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Filiais</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">
                    {formatCount(cr?.branchOffices)}
                  </dd>
                </div>
              )}
              {cr?.partnership && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Tipo Societário</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">{cr.partnership}</dd>
                </div>
              )}
              {cr?.nireNumber && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">NIRE</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">{cr.nireNumber}</dd>
                </div>
              )}
              {cr?.cnae && (
                <div>
                  <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">CNAE</dt>
                  <dd className="text-sm font-serif text-grafite dark:text-gray-200">{cr.cnae}</dd>
                </div>
              )}
              {(cr?.economicActivity || cr?.serasaActiveCode) && (
                <div className="bg-primary/5 dark:bg-primary/10 rounded-lg px-3 py-2.5 border-l-4 border-primary">
                  <dt className="text-[10px] font-sans font-bold text-primary uppercase tracking-wider mb-0.5">Ramo de Atividade</dt>
                  <dd className="text-sm font-sans font-medium text-grafite dark:text-gray-100 leading-snug">
                    {cr?.economicActivity || cr?.serasaActiveCode}
                  </dd>
                </div>
              )}
              {predecessors.length > 0 && (
                <div className="rounded-lg border border-border-light dark:border-border-dark bg-gray-50/70 dark:bg-gray-900/30 p-3">
                  <button
                    type="button"
                    onClick={() => setShowPredecessors((current) => !current)}
                    className="flex w-full items-center justify-between gap-3 text-left"
                  >
                    <div>
                      <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Nomes Anteriores</dt>
                      <dd className="text-xs font-sans text-gray-500 mt-0.5">
                        {predecessors.length} altera{predecessors.length === 1 ? "ção" : "ções"} de nome identificada{predecessors.length === 1 ? "" : "s"}
                      </dd>
                    </div>
                    <Icon name={showPredecessors ? "expand_less" : "expand_more"} className="text-gray-400" />
                  </button>

                  <div className="mt-3 border-t border-border-light dark:border-border-dark pt-3">
                      <div className="relative pl-5">
                        <div className="absolute left-[7px] top-1 bottom-1 w-px bg-gradient-to-b from-primary/50 via-primary/20 to-transparent" />
                      {visiblePredecessors.map((item, index) => (
                        <div
                          key={`${item.predecessorName ?? "predecessor"}-${item.predecessorDate ?? index}`}
                          className="relative mb-3 last:mb-0"
                        >
                          <span className="absolute -left-5 top-1.5 h-3.5 w-3.5 rounded-full border-2 border-primary/30 bg-white dark:bg-surface-dark" />
                          <div className="rounded-lg border border-border-light/70 dark:border-border-dark bg-white/80 dark:bg-gray-950/30 px-3 py-2.5 shadow-sm">
                            <p className="text-[11px] font-sans font-bold uppercase tracking-wide text-primary">
                              {formatDate(item.predecessorDate)}
                            </p>
                            <p className="mt-1 text-sm font-serif text-grafite dark:text-gray-200">
                              {item.predecessorName || "Nome anterior não informado"}
                            </p>
                            <p className="mt-1 text-[11px] font-sans text-gray-400">
                              Nome utilizado antes da razão social atual
                            </p>
                          </div>
                        </div>
                      ))}
                      </div>
                    {hiddenPredecessorCount > 0 && !showPredecessors && (
                      <button
                        type="button"
                        onClick={() => setShowPredecessors(true)}
                        className="mt-3 inline-flex items-center gap-1 text-xs font-sans font-bold text-primary hover:text-primary/80 transition-colors"
                      >
                        Ver mais {hiddenPredecessorCount} altera{hiddenPredecessorCount === 1 ? "ção" : "ções"}
                        <Icon name="expand_more" className="text-sm" />
                      </button>
                    )}
                    {predecessors.length > 5 && showPredecessors && (
                      <button
                        type="button"
                        onClick={() => setShowPredecessors(false)}
                        className="mt-3 inline-flex items-center gap-1 text-xs font-sans font-bold text-gray-500 hover:text-primary transition-colors"
                      >
                        Mostrar apenas 5
                        <Icon name="expand_less" className="text-sm" />
                      </button>
                    )}
                  </div>
                </div>
              )}
            </dl>
            {hasSerasaData && (
              <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-border-light pt-5 text-[11px] font-sans text-gray-500 dark:border-border-dark dark:text-gray-400">
                <span>
                  Fonte dos dados: <strong className="font-bold text-primary">Serasa</strong>
                </span>
                <span className="hidden text-gray-300 dark:text-gray-600 sm:inline">|</span>
                <span>
                  Consulta: <strong className="font-bold text-grafite dark:text-white">{formatDateTime(ca?.consultaEm)}</strong>
                </span>
                <span className="hidden text-gray-300 dark:text-gray-600 sm:inline">|</span>
                <span>
                  Atualização: <strong className="font-bold text-grafite dark:text-white">{formatDate(cr?.updateDate)}</strong>
                </span>
              </div>
            )}
          </div>

        </div>

        {/* Col 2 — Endereço + Mapa */}
        <div className="flex flex-col">
          <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark flex-1 flex flex-col print:border-gray-400 print:shadow-none">
            <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
              <Icon name="streetview" className="text-lg" /> Endereço da Sede
            </h2>
            <div className="mb-4">
              <p className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide mb-1">Localização</p>
              {fullAddressParts.length > 0 ? (
                <>
                  <p className="text-sm font-serif text-grafite dark:text-gray-200 font-medium">{fullAddressParts[0]}</p>
                  <p className="text-xs font-serif text-gray-500 dark:text-gray-400 mt-0.5">{fullAddressParts.slice(1).join(" · ")}</p>
                </>
              ) : (
                <p className="text-sm text-gray-400">Endereço não disponível</p>
              )}
            </div>

            {(cr?.phone || cr?.companyUrl) && (
              <div className="mb-4 space-y-1.5">
                {cr?.phone && (cr.phone.areaCode || cr.phone.phoneNumber) && (
                  <div className="flex items-center gap-2 text-sm font-serif text-grafite dark:text-gray-200">
                    <Icon name="phone" className="text-base text-gray-400" />
                    {`(${cr.phone.areaCode || ""}) ${cr.phone.phoneNumber || ""}`}
                  </div>
                )}
                {cr?.companyUrl && (
                  <div className="flex items-center gap-2 text-sm font-serif text-grafite dark:text-gray-200">
                    <Icon name="language" className="text-base text-gray-400" />
                    <a href={cr.companyUrl.startsWith("http") ? cr.companyUrl : `https://${cr.companyUrl}`}
                       target="_blank" rel="noopener noreferrer" className="hover:text-primary transition-colors truncate">
                      {cr.companyUrl}
                    </a>
                  </div>
                )}
              </div>
            )}

            {mapsSearchQuery && (
              <div className="flex-1 min-h-[520px] grid grid-rows-2 gap-3 print:hidden">
                <div className="relative rounded-lg overflow-hidden border border-border-light dark:border-border-dark">
                  <MapEmbed mode="streetview" searchQuery={mapsSearchQuery} lat={companyDetail?.latitude} lng={companyDetail?.longitude} />
                  <div className="absolute top-2 left-2 bg-black/60 backdrop-blur-md px-2 py-1 rounded-full text-white text-[11px] font-sans font-bold z-10 flex items-center gap-1">
                    <Icon name="directions_walk" size={13} /> Street View
                  </div>
                </div>
                <div className="relative rounded-lg overflow-hidden border border-border-light dark:border-border-dark">
                  <MapEmbed mode="map" searchQuery={mapsSearchQuery} lat={companyDetail?.latitude} lng={companyDetail?.longitude} />
                  <div className="absolute top-2 left-2 bg-black/60 backdrop-blur-md px-2 py-1 rounded-full text-white text-[11px] font-sans font-bold z-10 flex items-center gap-1">
                    <Icon name="map" size={13} /> Mapa
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      <CompanyBranchesPanel cnpj={cleanCnpj} />

      {/* ── Serasa: chamada ou pendências ────────────────────────────────── */}
      {(!hasSerasaData || totalPendingFromAnalysis(ca) > 0) && (
        <div className="mb-6">
          {!hasSerasaData ? (
          <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-8 shadow-sm border border-border-light dark:border-border-dark flex flex-col items-center justify-center text-center">
            <div className="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center text-primary mb-4">
              <Icon name="security_update_warning" className="text-3xl" />
            </div>
            <h3 className="text-xl font-bold font-sans text-grafite dark:text-white mb-2">Relatório Serasa não consultado</h3>
            <p className="text-gray-500 mb-6 text-sm max-w-xs">Clique em &ldquo;Consultar Serasa&rdquo; para obter o relatório RELATORIO_AVANCADO_PJ_ANALITICO.</p>
            <button
              onClick={() => refreshSerasa()}
              disabled={isRefreshingSerasa}
              style={{ backgroundColor: SERASA_COLOR }}
              className="inline-flex items-center gap-2 px-6 py-3 rounded-lg text-white font-sans font-bold shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(228,0,111,0.3)] hover:-translate-y-[1px] hover:brightness-110 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none disabled:brightness-100"
            >
              <Icon name="security" className={`${isRefreshingSerasa ? "animate-spin" : ""}`} />
              {isRefreshingSerasa ? "Consultando..." : "Consultar Serasa Agora"}
            </button>
          </div>
        ) : (
          <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-red-200 dark:border-red-900/60 print:border-gray-400 print:shadow-none">
            {(() => {
              const rows = [
                { label: "PEFIN",           count: neg?.pefin?.summary?.count ?? 0,               balance: neg?.pefin?.summary?.balance ?? 0 },
                { label: "REFIN",           count: neg?.refin?.summary?.count ?? 0,               balance: neg?.refin?.summary?.balance ?? 0 },
                { label: "Protestos",       count: neg?.notary?.summary?.count ?? 0,              balance: neg?.notary?.summary?.balance ?? 0 },
                { label: "Cheques",         count: neg?.check?.summary?.count ?? 0,               balance: neg?.check?.summary?.balance ?? 0 },
                { label: "Dívida Vencida",  count: neg?.collectionRecords?.summary?.count ?? 0,   balance: neg?.collectionRecords?.summary?.balance ?? 0 },
                { label: "Ações Judiciais", count: inq?.judgementFilings?.summary?.count ?? 0,    balance: inq?.judgementFilings?.summary?.balance ?? 0 },
                { label: "Falências",       count: inq?.bankrupts?.summary?.count ?? 0,           balance: inq?.bankrupts?.summary?.balance ?? 0 },
              ].filter(r => r.count > 0);

              return (
                <div className="bg-red-50 dark:bg-red-900/10 rounded-lg border border-red-200 dark:border-red-800 overflow-hidden">
                  <div className="px-3 py-2 border-b border-red-200 dark:border-red-800">
                    <p className="text-xs font-sans font-bold text-red-700 dark:text-red-400 flex items-center gap-1.5">
                      <Icon name="warning_amber" className="text-sm" />
                      {totalPendingFromAnalysis(ca)} pendência(s) · {fmt(totalDebtFromAnalysis(ca))}
                    </p>
                  </div>
                  <div className="divide-y divide-red-100 dark:divide-red-900/30">
                    {rows.map(r => (
                      <div key={r.label} className="flex items-center justify-between px-3 py-1.5">
                        <span className="text-xs font-sans font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">{r.label}</span>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-sans text-red-600 dark:text-red-400">{r.count}×</span>
                          <span className="text-xs font-sans font-bold text-red-700 dark:text-red-300">{fmt(r.balance)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })()}
          </div>
        )}
        </div>
      )}

      {/* ── IA — Análise de Crédito com Gemini ───────────────────────────── */}
      <AiAnalysisCard
        cnpj={cnpj}
        hasSerasaData={hasSerasaData}
        initialData={(() => {
          if (!ca?.aiAnalysis) return null;
          try { return JSON.parse(ca.aiAnalysis); } catch { return null; }
        })()}
        aiAnalysisDate={ca?.aiAnalysisDate ?? null}
      />

      {/* ── QSA — Quadro Societário (minimizável; Administradores à esquerda, Sócios à direita) ── */}
      {(hasAdmins || hasSocios) && (
        <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none mb-6">
          <button
            type="button"
            onClick={() => setQsaCollapsed((v) => !v)}
            className="w-full flex items-center justify-between gap-2 group"
          >
            <h2 className="font-sans font-bold text-base text-primary flex items-center gap-2">
              <Icon name="people" className="text-lg" /> QSA — Quadro Societário
            </h2>
            <span className="flex items-center gap-2 text-xs text-gray-400 font-sans">
              {adminCount + socioCount} no total
              <Icon name={qsaCollapsed ? "expand_more" : "expand_less"} className="text-gray-400 group-hover:text-primary transition-colors" />
            </span>
          </button>

          {!qsaCollapsed && (
          <div className={`mt-5 grid gap-6 ${hasAdmins && hasSocios ? "md:grid-cols-2 md:divide-x md:divide-border-light dark:md:divide-border-dark" : "grid-cols-1"}`}>
            {/* ── Administradores (esquerda) ── */}
            {hasAdmins && (
              <div className={hasSocios ? "md:pr-6" : ""}>
                <div className="flex items-center gap-2 mb-3">
                  <span className="w-1 h-4 rounded-full bg-secondary inline-block" />
                  <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-widest">Administradores</p>
                  <span className="ml-auto text-xs text-gray-400 font-sans">{adminCount}</span>
                </div>
                <div className="space-y-2">
                  {directors.length > 0 ? visibleDirectors.map((d, i) => (
                    <button
                      key={i}
                      onClick={() => setSelectedDirector(d)}
                      className="w-full text-left flex items-start gap-3 p-3 bg-background-light dark:bg-background-dark rounded-lg hover:bg-secondary/5 dark:hover:bg-secondary/10 transition-colors cursor-pointer group"
                    >
                      <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-xs font-sans flex-shrink-0 ${
                        d.restrictionSign ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400" : "bg-secondary/20 text-secondary"
                      }`}>
                        {d.name.substring(0, 2).toUpperCase()}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-sans font-bold text-grafite dark:text-gray-200 truncate">{d.name}</p>
                        <p className="text-xs font-serif text-gray-500">{d.role} · {d.status}</p>
                        {d.sinceDate && <p className="text-[10px] text-gray-400">Desde: {formatDate(d.sinceDate)}</p>}
                      </div>
                      <Icon name="chevron_right" className="text-sm text-gray-300 group-hover:text-secondary transition-colors self-center flex-shrink-0" />
                    </button>
                  )) : visibleMemberAdmins.map((m: CompanyMember, i: number) => {
                    const name = m.person?.name || m.name || "Administrador";
                    return (
                      <button
                        key={i}
                        onClick={() => setSelectedDirector(memberToDirector(m))}
                        className="w-full text-left flex items-start gap-3 p-3 bg-background-light dark:bg-background-dark rounded-lg hover:bg-secondary/5 dark:hover:bg-secondary/10 transition-colors cursor-pointer group"
                      >
                        <div className="w-9 h-9 rounded-full bg-secondary/20 text-secondary flex items-center justify-center font-bold text-xs font-sans flex-shrink-0">
                          {name.substring(0, 2).toUpperCase()}
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-sans font-bold text-grafite dark:text-gray-200 truncate">{name}</p>
                          <p className="text-xs font-serif text-gray-500">{memberRoleText(m)}</p>
                        </div>
                        <Icon name="chevron_right" className="text-sm text-gray-300 group-hover:text-secondary transition-colors self-center flex-shrink-0" />
                      </button>
                    );
                  })}
                </div>
                {adminCount > 2 && (
                  <button
                    type="button"
                    onClick={() => setShowAllAdmins((v) => !v)}
                    className="mt-3 inline-flex items-center gap-1 text-xs font-sans font-bold text-primary hover:text-primary/80 transition-colors"
                  >
                    {showAllAdmins ? "Mostrar menos" : `Ver mais ${adminCount - 2}`}
                    <Icon name={showAllAdmins ? "expand_less" : "expand_more"} className="text-sm" />
                  </button>
                )}
              </div>
            )}

            {/* ── Sócios (direita) ── */}
            {hasSocios && (
              <div className={hasAdmins ? "md:pl-6" : ""}>
                <div className="flex items-center gap-2 mb-3">
                  <span className="w-1 h-4 rounded-full bg-primary inline-block" />
                  <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-widest">Sócios</p>
                  <span className="ml-auto text-xs text-gray-400 font-sans">{socioCount}</span>
                </div>
                {hasAdmins && (
                  <div className="md:hidden border-t border-border-light dark:border-border-dark mb-3 -mx-3" />
                )}
                {partners.length > 0 && inactivePartners.length > 0 && (
                  <div className="flex items-center justify-between gap-4 mb-3 rounded-lg border border-border-light dark:border-border-dark px-3 py-2 bg-background-light dark:bg-background-dark">
                    <p className="text-xs font-sans text-gray-500 dark:text-gray-400">
                      Exibindo <strong>{activePartners.length}</strong> sócio(s) ativo(s). Há{" "}
                      <strong>{inactivePartners.length}</strong> baixado(s).
                    </p>
                    <button
                      type="button"
                      onClick={() => setShowInactivePartners((prev) => !prev)}
                      className="text-xs text-gray-400 hover:text-primary transition-colors underline underline-offset-2 whitespace-nowrap"
                    >
                      {showInactivePartners ? "ocultar baixados" : "ver baixados"}
                    </button>
                  </div>
                )}
                <div className="space-y-2">
                  {partners.length > 0 ? visibleSocioPartners.map((p, i) => {
                    const partnerCpf = (p.documentId ?? "").replace(/\D/g, "");
                    const isCpf = partnerCpf.length === 11;
                    const pfSummary = isCpf ? profile?.partnerPfAnalyses?.[partnerCpf] : undefined;
                    const isInactive = p.status?.toUpperCase().includes("BAIX");
                    return (
                      <div
                        key={`${p.documentId ?? p.name}-${i}`}
                        className="flex items-center bg-background-light dark:bg-background-dark rounded-lg overflow-hidden"
                      >
                        <div
                          role="button"
                          tabIndex={0}
                          onClick={() => setSelectedPartner(p)}
                          onKeyDown={(e) => e.key === "Enter" && setSelectedPartner(p)}
                          className="flex items-start gap-3 p-3 flex-1 min-w-0 hover:bg-primary/5 dark:hover:bg-primary/10 transition-colors cursor-pointer group"
                        >
                          <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-xs font-sans flex-shrink-0 ${
                            p.restrictionSign ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400" : "bg-primary/10 text-primary"
                          }`}>
                            {p.name.substring(0, 2).toUpperCase()}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-sans font-bold text-grafite dark:text-gray-200 truncate">{p.name}</p>
                            <p className="text-xs font-serif text-gray-500">
                              {p.documentType}: {p.documentId || "-"} · {p.status}
                              {p.capitalTotalValue != null ? ` · ${p.capitalTotalValue}%` : ""}
                            </p>
                            {p.sinceDate && <p className="text-[10px] text-gray-400">Desde: {formatDate(p.sinceDate)}</p>}
                            {p.restrictionSign && <span className="text-[10px] text-red-600 font-bold">⚠ Com restrição</span>}
                            {isInactive && (
                              <p className="text-[10px] text-amber-600 dark:text-amber-400 font-bold mt-1">
                                Sócio baixado exibido por expansão
                              </p>
                            )}
                          </div>
                          <Icon name="chevron_right" className="text-sm text-gray-300 group-hover:text-primary transition-colors self-center flex-shrink-0" />
                        </div>
                        {isCpf && pfSummary && (
                          <div className="pr-3 flex-shrink-0">
                            <PartnerPFBadge cpf={partnerCpf} pfSummary={pfSummary} companyCnpj={cleanCnpj} companyLabel={companyLabel} />
                          </div>
                        )}
                      </div>
                    );
                  }) : visibleMemberSocios.map((m: CompanyMember, i: number) => {
                    const name = m.person?.name || m.name || "Sócio";
                    const doc = memberDoc(m);
                    const isCpf = doc.length === 11;
                    const pfSummary = isCpf ? profile?.partnerPfAnalyses?.[doc] : undefined;
                    return (
                      <div key={i} className="flex items-center bg-background-light dark:bg-background-dark rounded-lg overflow-hidden">
                        <div
                          role="button"
                          tabIndex={0}
                          onClick={() => setSelectedPartner(memberToPartner(m))}
                          onKeyDown={(e) => e.key === "Enter" && setSelectedPartner(memberToPartner(m))}
                          className="flex items-start gap-3 p-3 flex-1 min-w-0 hover:bg-primary/5 dark:hover:bg-primary/10 transition-colors cursor-pointer group"
                        >
                          <div className="w-9 h-9 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-xs font-sans flex-shrink-0">
                            {name.substring(0, 2).toUpperCase()}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="text-sm font-sans font-bold text-grafite dark:text-gray-200 truncate">{name}</p>
                            <p className="text-xs font-serif text-gray-500">{memberRoleText(m)}{doc ? ` · ${memberDocType(doc)}: ${doc}` : ""}</p>
                          </div>
                          <Icon name="chevron_right" className="text-sm text-gray-300 group-hover:text-primary transition-colors self-center flex-shrink-0" />
                        </div>
                        {isCpf && pfSummary && (
                          <div className="pr-3 flex-shrink-0">
                            <PartnerPFBadge cpf={doc} pfSummary={pfSummary} companyCnpj={cleanCnpj} companyLabel={companyLabel} />
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
                {socioCount > 2 && (
                  <button
                    type="button"
                    onClick={() => setShowAllSocios((v) => !v)}
                    className="mt-3 inline-flex items-center gap-1 text-xs font-sans font-bold text-primary hover:text-primary/80 transition-colors"
                  >
                    {showAllSocios ? "Mostrar menos" : `Ver mais ${socioCount - 2}`}
                    <Icon name={showAllSocios ? "expand_less" : "expand_more"} className="text-sm" />
                  </button>
                )}
              </div>
            )}
          </div>
          )}
        </div>
      )}

      {/* ── Modals ───────────────────────────────────────────────────────── */}
      <PartnerDetailModal
        partner={selectedPartner}
        onClose={() => setSelectedPartner(null)}
        pfSummary={(() => {
          const cpf = (selectedPartner?.documentId ?? "").replace(/\D/g, "");
          return cpf.length === 11 ? profile?.partnerPfAnalyses?.[cpf] : undefined;
        })()}
        companyCnpj={cleanCnpj}
        companyLabel={companyLabel}
      />
      <PartnerDetailModal
        director={selectedDirector}
        onClose={() => setSelectedDirector(null)}
        pfSummary={(() => {
          const cpf = (selectedDirector?.documentId ?? "").replace(/\D/g, "");
          return cpf.length === 11 ? profile?.partnerPfAnalyses?.[cpf] : undefined;
        })()}
        companyCnpj={cleanCnpj}
        companyLabel={companyLabel}
      />

      {/* ── Dados Negativos (só com Serasa) ──────────────────────────────── */}
      {hasSerasaData && (() => {
        const negativeSummaryMessage = neg?.message?.trim();
        const isNadaConstaSignal = negativeSummaryMessage?.toUpperCase() === "NADA CONSTA";
        const pefinRecords   = (neg?.pefin   as { pefinResponse?: PefinRecord[]   } | undefined)?.pefinResponse   ?? [];
        const refinRecords   = (neg?.refin   as { refinResponse?: PefinRecord[]   } | undefined)?.refinResponse   ?? [];
        const notaryRecords  = neg?.notary?.notaryResponse ?? [];
        const collectRecords = (neg?.collectionRecords as { collectionRecordsResponse?: CollectionRecord[] } | undefined)?.collectionRecordsResponse ?? [];
        const checkRecords   = (neg?.check   as { checkResponse?: CheckRecord[]   } | undefined)?.checkResponse   ?? [];

        const toggle = (id: string) => setOpenNegCards(prev => ({ ...prev, [id]: !prev[id] }));
        const isOpen = (id: string) => !!openNegCards[id];

        const detailIds: { id: string; hasRecords: boolean }[] = [
          { id: "pefin",    hasRecords: pefinRecords.length > 0 },
          { id: "refin",    hasRecords: refinRecords.length > 0 },
          { id: "check",    hasRecords: checkRecords.length > 0 },
          { id: "collect",  hasRecords: collectRecords.length > 0 },
          { id: "concentre",hasRecords: notaryRecords.length > 0 },
        ];
        const expandableIds = detailIds.filter(d => d.hasRecords).map(d => d.id);
        const allExpanded   = expandableIds.length > 0 && expandableIds.every(id => openNegCards[id]);

        const concentreRaw = (inq as Record<string, unknown>)?.concentre ?? (inq as Record<string, unknown>)?.concentreRecords ?? (neg as Record<string, unknown>)?.concentre;
        const recheckRaw   = (inq as Record<string, unknown>)?.recheckOccurrences ?? (inq as Record<string, unknown>)?.recheckData ?? (inq as Record<string, unknown>)?.checkOccurrences ?? (neg as Record<string, unknown>)?.recheque;
        const concentreMsg = (concentreRaw as { message?: string })?.message;
        const recheckMsg   = (recheckRaw   as { message?: string })?.message;
        const notaryCount  = neg?.notary?.summary?.count ?? 0;
        const recheckCount = (recheckRaw as { summary?: { count: number } })?.summary?.count ?? 0;

        const concentreText = notaryCount > 0
          ? `${notaryCount} protesto(s) em cartório`
          : concentreMsg || "Nada Consta para o CNPJ e Participantes";
        const recheckText = recheckCount > 0
          ? recheckMsg || `${recheckCount} ocorrência(s)`
          : recheckMsg || "Nada Consta para o CNPJ";

        return (
          <div className="mb-6">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-sans font-bold text-base text-grafite dark:text-white flex items-center gap-2">
                <Icon name="warning_amber" className="text-lg text-primary" /> Dados Negativos
              </h2>
              {expandableIds.length > 0 && (
                <button
                  className="text-xs text-gray-400 hover:text-primary dark:hover:text-primary-light transition-colors underline underline-offset-2"
                  onClick={() => setOpenNegCards(
                    allExpanded
                      ? {}
                      : Object.fromEntries(expandableIds.map(id => [id, true]))
                  )}
                >
                  {allExpanded ? "recolher tudo" : "expandir tudo"}
                </button>
              )}
            </div>

            {negativeSummaryMessage && (
              <div className={`mb-3 rounded-xl px-4 py-3 ${
                isNadaConstaSignal
                  ? "border border-red-300/90 bg-red-50 dark:border-red-700 dark:bg-red-950/20"
                  : "border border-amber-300/80 bg-amber-50 dark:border-amber-700 dark:bg-amber-900/10"
              }`}>
                <div className="flex items-start gap-3">
                  <Icon name={isNadaConstaSignal ? "priority_high" : "info"} size={18} className={`mt-0.5 ${ isNadaConstaSignal ? "text-red-700 dark:text-red-400" : "text-amber-700 dark:text-amber-400" }`} />
                  <div>
                    <p className={`text-[11px] font-sans font-bold uppercase tracking-[0.16em] ${
                      isNadaConstaSignal
                        ? "text-red-800 dark:text-red-300"
                        : "text-amber-800 dark:text-amber-300"
                    }`}>
                      {isNadaConstaSignal ? "Sinalização de Atenção" : "Mensagem retornada pela Serasa"}
                    </p>
                    {isNadaConstaSignal ? (
                      <>
                        <p className="mt-1 text-sm font-serif font-semibold text-red-900 dark:text-red-200">
                          NADA CONSTA
                        </p>
                        <p className="mt-1 text-sm font-serif text-red-900/90 dark:text-red-200/90">
                          A Serasa retornou a sinalização &quot;NADA CONSTA&quot;. Esse tipo de resposta pode indicar restrição de exibição de ocorrências e deve ser analisado com atenção pela equipe.
                        </p>
                      </>
                    ) : (
                      <p className="mt-1 text-sm font-serif text-amber-900 dark:text-amber-200">
                        {negativeSummaryMessage}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* Cards principais */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <NegativeCard label="PEFIN"     count={neg?.pefin?.summary?.count ?? 0}             balance={neg?.pefin?.summary?.balance ?? 0}             fmt={fmt} hasDetail={pefinRecords.length > 0}   isOpen={isOpen("pefin")}   onToggle={() => toggle("pefin")} />
              <NegativeCard label="REFIN"     count={neg?.refin?.summary?.count ?? 0}             balance={neg?.refin?.summary?.balance ?? 0}             fmt={fmt} hasDetail={refinRecords.length > 0}   isOpen={isOpen("refin")}   onToggle={() => toggle("refin")} />
              <NegativeCard label="Cheques"   count={neg?.check?.summary?.count ?? 0}             balance={neg?.check?.summary?.balance ?? 0}             fmt={fmt} hasDetail={checkRecords.length > 0}   isOpen={isOpen("check")}   onToggle={() => toggle("check")} />
              <NegativeCard label="Dívida Vencida" count={neg?.collectionRecords?.summary?.count ?? 0} balance={neg?.collectionRecords?.summary?.balance ?? 0} fmt={fmt} hasDetail={collectRecords.length > 0} isOpen={isOpen("collect")} onToggle={() => toggle("collect")} />
            </div>

            {/* Detail panels */}
            {isOpen("pefin") && pefinRecords.length > 0 && (
              <NegDetailTable title="PEFIN — Pendências Financeiras" shownCount={pefinRecords.length} totalCount={neg?.pefin?.summary?.count}>
                <PefinRefinTable records={pefinRecords} fmt={fmt} />
              </NegDetailTable>
            )}
            {isOpen("refin") && refinRecords.length > 0 && (
              <NegDetailTable title="REFIN — Refinanciamentos" shownCount={refinRecords.length} totalCount={neg?.refin?.summary?.count}>
                <PefinRefinTable records={refinRecords} fmt={fmt} />
              </NegDetailTable>
            )}
            {isOpen("collect") && collectRecords.length > 0 && (
              <NegDetailTable title="Cobranças" shownCount={collectRecords.length} totalCount={neg?.collectionRecords?.summary?.count}>
                <CollectionDetailTable records={collectRecords} fmt={fmt} />
              </NegDetailTable>
            )}
            {isOpen("check") && checkRecords.length > 0 && (
              <NegDetailTable title="Cheques" shownCount={checkRecords.length} totalCount={neg?.check?.summary?.count}>
                <CheckDetailTable records={checkRecords} fmt={fmt} />
              </NegDetailTable>
            )}

            {/* Concentre e Recheque */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-3">
              {/* Concentre — clicável quando há protestos */}
              <div
                className={`rounded-xl p-4 border transition-all ${notaryCount === 0
                  ? "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800"
                  : isOpen("concentre")
                    ? "bg-red-100 dark:bg-red-900/20 border-red-500 dark:border-red-500 ring-2 ring-red-300 dark:ring-red-700"
                    : "bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800"
                } ${notaryCount > 0 ? "cursor-pointer select-none" : ""}`}
                onClick={notaryCount > 0 ? () => toggle("concentre") : undefined}
              >
                <div className="flex items-center justify-between mb-1">
                  <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-wide">Concentre — Protestos em Cartório</p>
                  {notaryCount > 0 && (
                    <Icon name={isOpen("concentre") ? "expand_less" : "expand_more"} className="text-base text-gray-400 dark:text-gray-500" />
                  )}
                </div>
                <p className={`text-sm font-serif ${notaryCount === 0 ? "text-green-700 dark:text-green-400" : "text-red-700 dark:text-red-400"}`}>
                  {concentreText}
                </p>
                {isOpen("concentre") && notaryRecords.length > 0 && (
                  <div className="mt-3 pt-3 border-t border-red-200 dark:border-red-700 overflow-x-auto">
                    <NotaryDetailTable records={notaryRecords} fmt={fmt} />
                  </div>
                )}
              </div>

              {/* Recheque — somente resumo */}
              <div className={`rounded-xl p-4 border ${recheckCount === 0
                ? "bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800"
                : "bg-red-50 dark:bg-red-900/10 border-red-200 dark:border-red-800"}`}>
                <p className="text-xs font-sans font-bold text-gray-500 uppercase tracking-wide mb-1">Recheque — Cheques Extraviados/Sustados</p>
                <p className={`text-sm font-serif ${recheckCount === 0 ? "text-green-700 dark:text-green-400" : "text-red-700 dark:text-red-400"}`}>
                  {recheckText}
                </p>
              </div>
            </div>
          </div>
        );
      })()}

      {/* ── Fatos + Consultas Recentes (só com Serasa) ───────────────────── */}
      {hasSerasaData && (() => {
        const judgementRecords = (inq?.judgementFilings as { judgementFilingsResponse?: JudgementFilingRecord[] } | undefined)?.judgementFilingsResponse ?? [];
        const bankruptRecords  = (inq?.bankrupts        as { bankruptsResponse?: BankruptRecord[] }              | undefined)?.bankruptsResponse        ?? [];

        const factItems = [
          { id: "judgement", label: "Ações Judiciais",                  section: inq?.judgementFilings, records: judgementRecords },
          { id: "bankrupt",  label: "Falências / Recuperação Judicial", section: inq?.bankrupts,        records: bankruptRecords  },
        ];

        const openFact = factItems.find(f => openNegCards[f.id] && f.records.length > 0) ?? null;

        return (
          <div className="mb-6">
            <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">

              {/* Fatos Relevantes */}
              <div className="bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
                <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
                  <Icon name="gavel" className="text-lg" /> Fatos Relevantes
                </h2>
                <dl className="space-y-3">
                  {factItems.map(({ id, label, section, records }) => {
                    const count    = section?.summary?.count ?? 0;
                    const balance  = section?.summary?.balance ?? 0;
                    const isClean  = count === 0;
                    const hasDetail = records.length > 0;
                    const open     = !!openNegCards[id];
                    return (
                      <div
                        key={id}
                        className={`flex items-center justify-between p-3 rounded-lg transition-all ${
                          isClean ? "bg-green-50 dark:bg-green-900/10" :
                          open    ? "bg-red-100 dark:bg-red-900/20 ring-1 ring-red-300 dark:ring-red-700" :
                                    "bg-red-50 dark:bg-red-900/10"
                        } ${hasDetail ? "cursor-pointer select-none" : ""}`}
                        onClick={hasDetail ? () => setOpenNegCards(p => ({ ...p, [id]: !p[id] })) : undefined}
                      >
                        <dt className="text-sm font-sans text-grafite dark:text-gray-200">{label}</dt>
                        <dd className={`flex items-center gap-1 text-sm font-bold font-sans ${isClean ? "text-green-700 dark:text-green-400" : "text-red-700 dark:text-red-400"}`}>
                          {count === 0 ? "Nada Consta" : `${count}× · ${fmt(balance)}`}
                          {hasDetail && (
                            <Icon name={open ? "expand_less" : "expand_more"} className="text-base text-gray-400" />
                          )}
                        </dd>
                      </div>
                    );
                  })}
                </dl>
              </div>

              {/* Consultas Recentes */}
              <div className="xl:col-span-2 bg-surface-light dark:bg-surface-dark rounded-xl p-6 shadow-sm border border-border-light dark:border-border-dark print:border-gray-400 print:shadow-none">
                <h2 className="font-sans font-bold text-base text-primary mb-4 flex items-center gap-2">
                  <Icon name="manage_search" className="text-lg" /> Consultas ao CNPJ
                  {inq?.inquiryCompanyResponse?.quantity?.actual != null && (
                    <span className="ml-auto text-xs font-sans text-gray-400">
                      {inq.inquiryCompanyResponse.quantity.actual} nos últimos 30 dias
                    </span>
                  )}
                </h2>
                {inquiries.length === 0 ? (
                  <p className="text-sm text-gray-400 font-serif">Nenhuma consulta registrada.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="border-b border-border-light dark:border-border-dark">
                          <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4">Data</th>
                          <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2 pr-4">Empresa Consultante</th>
                          <th className="text-left text-[11px] font-sans font-bold text-gray-400 uppercase pb-2">CNPJ</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border-light dark:divide-border-dark">
                        {inquiries.slice(0, 8).map((r: import("../../../../types/company-detail").InquiryResult, i: number) => (
                          <tr key={i}>
                            <td className="py-2 pr-4 font-serif text-grafite dark:text-gray-200 whitespace-nowrap">{formatDate(r.occurrenceDate)}</td>
                            <td className="py-2 pr-4 font-serif text-grafite dark:text-gray-200">{r.companyName}</td>
                            <td className="py-2 font-serif text-gray-500 whitespace-nowrap">
                              {r.companyDocumentId ? formatCNPJ(r.companyDocumentId) : "—"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                    {inquiries.length > 8 && (
                      <p className="text-xs text-gray-400 mt-2 font-sans">+{inquiries.length - 8} consulta(s) não exibida(s)</p>
                    )}

                    {inquiryHistory.length > 0 && (
                      <div className="mt-4 border-t border-border-light dark:border-border-dark pt-4">
                        <div className="flex items-center justify-between gap-3 mb-3">
                          <div>
                            <p className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">
                              Histórico mensal de consultas
                            </p>
                            <p className="text-xs text-gray-500 font-sans mt-0.5">
                              Volume consolidado informado pela Serasa por mês.
                            </p>
                          </div>
                        </div>

                        <div className="rounded-xl border border-border-light dark:border-border-dark bg-background-light dark:bg-background-dark px-2.5 py-2.5">
                          <div className="flex items-end gap-1.5 overflow-hidden">
                            {inquiryHistory.map((item, index) => {
                              const height = Math.max(
                                maxInquiryOccurrences > 0
                                  ? ((item.occurrences ?? 0) / maxInquiryOccurrences) * 56
                                  : 0,
                                8,
                              );

                              return (
                                <div
                                  key={`${item.inquiryDate ?? "month"}-${index}`}
                                  className="min-w-0 flex-1 flex flex-col items-center gap-1"
                                >
                                  <div className="h-5 flex items-end justify-center">
                                    <p className="text-[10px] font-sans font-bold text-grafite dark:text-white leading-none">
                                      {item.occurrences ?? 0}
                                    </p>
                                  </div>
                                  <div className="h-14 w-full flex items-end justify-center rounded-md bg-white/70 dark:bg-gray-950/30 px-1 py-1 mt-0.5">
                                    <div
                                      className="w-full max-w-[18px] rounded-t-[4px] bg-gradient-to-t from-primary to-primary/65 shadow-sm"
                                      style={{ height: `${height}px` }}
                                    />
                                  </div>
                                  <p className="text-[9px] text-center font-sans font-bold uppercase tracking-wide text-gray-400 leading-tight min-h-[14px] flex items-start justify-center">
                                    {formatMonthYear(item.inquiryDate)}
                                  </p>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Painel de detalhe em largura total — aparece abaixo do grid */}
            {openFact && (() => {
              const { id, section, records } = openFact;
              const count = section?.summary?.count ?? 0;
              const title = id === "judgement"
                ? "Ações Judiciais — Detalhamento"
                : "Falências / Recuperação Judicial — Detalhamento";
              return (
                <NegDetailTable title={title} shownCount={records.length} totalCount={count}>
                  {id === "judgement"
                    ? <JudgementFilingDetailTable records={records as JudgementFilingRecord[]} fmt={fmt} />
                    : <BankruptDetailTable records={records as BankruptRecord[]} fmt={fmt} />
                  }
                </NegDetailTable>
              );
            })()}
          </div>
        );
      })()}

      {/* ── Histórico de Pagamentos (Mercado / Sacado / Cedente) ────────────── */}
      {ca?.paymentHistory && (
        <PaymentHistoryPanel ph={ca.paymentHistory} />
      )}

      <CommercialInformationPanel cnpj={cleanCnpj} />

      <CompanyNotesPanel cnpj={cleanCnpj} />

      <CompanyDocumentsPanel cnpj={cleanCnpj} />

      {/* ── Praça de Pagamento: lançamentos (último bloco antes da Zona de Perigo) ── */}
      {pracaSummary && pracaSummary.totalCount > 0 ? (
        <div className="mb-6 rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark print:hidden">
          <div className="flex flex-wrap items-center gap-2 border-b border-border-light p-5 dark:border-border-dark">
            <Icon name="fact_check" className="text-primary" />
            <h2 className="font-sans text-base font-bold text-primary">Praça de Pagamento</h2>
            <span className="text-[11px] text-gray-400">· clique na linha para ver detalhes</span>
            <span className="ml-auto text-xs text-gray-400">{pracaSummary.totalFilteredElements} de {pracaSummary.totalCount}</span>
          </div>

          {/* Filtros */}
          <div className="flex flex-wrap items-end gap-3 border-b border-border-light p-4 dark:border-border-dark">
            <label className="block">
              <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">De</span>
              <input type="date" value={pracaFrom} onChange={(ev) => { setPracaFrom(ev.target.value); setPracaPage(0); }}
                className="mt-1 block h-9 rounded-lg border border-border-light bg-white px-2 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white" />
            </label>
            <label className="block">
              <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Até</span>
              <input type="date" value={pracaTo} onChange={(ev) => { setPracaTo(ev.target.value); setPracaPage(0); }}
                className="mt-1 block h-9 rounded-lg border border-border-light bg-white px-2 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white" />
            </label>
            <label className="block">
              <span className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Por página</span>
              <select value={pracaSize} onChange={(ev) => { setPracaSize(Number(ev.target.value)); setPracaPage(0); }}
                className="mt-1 block h-9 rounded-lg border border-border-light bg-white px-2 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white">
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={30}>30</option>
              </select>
            </label>
            {(pracaFrom || pracaTo || pracaDecisao) ? (
              <button type="button" onClick={() => { setPracaFrom(""); setPracaTo(""); setPracaDecisao(""); setPracaPage(0); }}
                className="h-9 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
                Limpar
              </button>
            ) : null}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500 dark:bg-white/5 dark:text-gray-400">
                <tr>
                  <th className="px-5 py-3">Título</th>
                  <th className="px-5 py-3">Sacado</th>
                  <th className="px-5 py-3">Valor pago</th>
                  <th className="px-5 py-3">Praça</th>
                  <th className="px-5 py-3">Decidido em</th>
                  <th className="px-5 py-3">Analista</th>
                  <th className="px-5 py-3 text-right">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border-light dark:divide-border-dark">
                {pracaSummary.entries.length === 0 ? (
                  <tr><td colSpan={7} className="px-5 py-6 text-sm text-gray-500">Nenhum lançamento para os filtros.</td></tr>
                ) : pracaSummary.entries.map((e) => (
                  <tr key={e.id} onClick={() => setPracaSelected(e)} className="cursor-pointer hover:bg-gray-50/70 dark:hover:bg-white/[0.03]">
                    <td className="px-5 py-3 font-bold text-grafite dark:text-white">
                      <span className="inline-flex items-center gap-2">
                        {e.titleNumber ?? "-"}
                        {e.attachmentCount ? <AttachmentBadge count={e.attachmentCount} onClick={() => setPracaViewer(e)} /> : null}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-grafite dark:text-gray-200">{e.payerName ?? "-"}</td>
                    <td className="px-5 py-3 text-grafite dark:text-gray-200">{e.paidValue ?? "-"}</td>
                    <td className="px-5 py-3">
                      <span className={`inline-flex h-6 items-center rounded-full px-2.5 text-xs font-bold ${
                        e.analystDecision === "SACADO"
                          ? "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300"
                          : "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
                      }`}>
                        {e.analystDecision === "SACADO" ? "Praça Sacado" : "Praça Cedente"}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-gray-500">{e.decidedAt ? new Date(e.decidedAt).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) : "-"}</td>
                    <td className="px-5 py-3 text-gray-500">{e.decidedByName ?? "-"}</td>
                    <td className="px-5 py-3 text-right">
                      <button
                        type="button"
                        disabled={reopenEntryMutation.isPending}
                        onClick={(ev) => {
                          ev.stopPropagation();
                          const ok = window.confirm(
                            `Remover a análise do título ${e.titleNumber ?? ""}?\n\nO lançamento volta para PENDENTE na Praça de Pagamento (reaberto e destacado) e, se o lote do dia estiver arquivado, ele será restaurado.`,
                          );
                          if (ok) reopenEntryMutation.mutate(e.id);
                        }}
                        className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-red-100 text-red-600 transition-colors hover:bg-red-50 disabled:opacity-60 dark:border-red-500/20 dark:text-red-300 dark:hover:bg-red-500/10"
                        title="Remover análise (devolve à Praça de Pagamento)"
                        aria-label="Remover análise"
                      >
                        <Icon name="delete" size={18} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Paginação */}
          {pracaSummary.totalPages > 1 ? (
            <div className="flex items-center justify-between gap-2 border-t border-border-light p-4 dark:border-border-dark">
              <span className="text-xs text-gray-500">Página {pracaSummary.page + 1} de {pracaSummary.totalPages}</span>
              <div className="flex items-center gap-2">
                <button type="button" disabled={pracaSummary.page <= 0} onClick={() => setPracaPage((p) => Math.max(0, p - 1))}
                  className="inline-flex h-8 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
                  <Icon name="chevron_left" size={16} /> Anterior
                </button>
                <button type="button" disabled={pracaSummary.page >= pracaSummary.totalPages - 1} onClick={() => setPracaPage((p) => p + 1)}
                  className="inline-flex h-8 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
                  Próxima <Icon name="chevron_right" size={16} />
                </button>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}

      {pracaSelected ? (
        <PaymentPlaceEntryReadOnlyModal entry={pracaSelected} onClose={() => setPracaSelected(null)} />
      ) : null}
      {pracaViewer ? (
        <AttachmentViewerModal entryId={pracaViewer.id} title={pracaViewer.titleNumber ?? undefined} onClose={() => setPracaViewer(null)} />
      ) : null}

      <div className="mt-10 rounded-2xl border border-red-200 dark:border-red-900/60 bg-red-50/60 dark:bg-red-950/20 p-5">
        <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
          <div>
            <h2 className="text-sm font-sans font-bold text-red-700 dark:text-red-400 uppercase tracking-wide">
              Zona de Perigo
            </h2>
            <p className="text-sm text-red-700/80 dark:text-red-300/80 font-sans mt-1 max-w-2xl">
              Excluir a empresa remove o cadastro e o histórico relacionado deste CNPJ. Use esta ação apenas quando tiver certeza.
            </p>
          </div>
          <button
            onClick={handleDeleteCompany}
            className="inline-flex items-center justify-center gap-2 h-11 px-4 rounded-xl border border-red-300 bg-white text-red-700 dark:border-red-800 dark:bg-red-950/10 dark:text-red-400 font-sans font-semibold text-sm hover:bg-red-100 dark:hover:bg-red-900/20 active:scale-[0.98] transition-all duration-200"
          >
            <Icon name="delete" size={18} />
            Excluir Empresa
          </button>
        </div>
      </div>


      </div>{/* /print:hidden */}
    </>
  );
}

function ClientCodeField({ cnpj, value, origin }: { cnpj: string; value?: string | null; origin?: string | null }) {
  const isSacado = origin === "SACADO_PRACA";
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [code, setCode] = useState(value ?? "");
  const [saving, setSaving] = useState(false);
  const [promoting, setPromoting] = useState(false);
  React.useEffect(() => setCode(value ?? ""), [value]);

  const cleanCnpj = (cnpj || "").replace(/\D/g, "");
  const API = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

  // Promove um sacado a cliente da carteira: passa a exigir código 4R.
  const addToCarteira = async () => {
    setPromoting(true);
    try {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const res = await fetch(`${API}/clients/document/${cleanCnpj}/origem`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ origin: "CARTEIRA" }),
      });
      if (!res.ok) {
        const t = await res.text().catch(() => "");
        throw new Error(t || "Falha ao adicionar à carteira");
      }
      toast.success("Empresa adicionada à carteira. Cadastre o código 4R.");
      setEditing(true);
      queryClient.invalidateQueries({ queryKey: ["clientProfile", cleanCnpj] });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Falha ao adicionar à carteira");
    } finally {
      setPromoting(false);
    }
  };

  const save = async () => {
    setSaving(true);
    try {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const res = await fetch(`${API}/clients/document/${cleanCnpj}/codigo-cliente`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ clientCode: code.trim() }),
      });
      if (!res.ok) {
        const t = await res.text().catch(() => "");
        throw new Error(t || "Falha ao salvar o código do cliente");
      }
      toast.success("Código do cliente salvo");
      setEditing(false);
      queryClient.invalidateQueries({ queryKey: ["clientProfile", cleanCnpj] });
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Falha ao salvar");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <dt className="text-[11px] font-sans font-bold text-gray-400 uppercase tracking-wide">Código do Cliente (4R)</dt>
      {editing ? (
        <dd className="mt-1 flex items-center gap-2">
          <input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Ex.: 000852"
            className="h-8 w-32 rounded-md border border-border-light bg-white px-2 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-surface-dark dark:text-white"
            autoFocus
          />
          <button
            type="button"
            onClick={save}
            disabled={saving}
            className="inline-flex h-8 items-center rounded-md bg-primary px-3 text-xs font-bold text-white hover:bg-primary/90 disabled:opacity-60"
          >
            Salvar
          </button>
          <button
            type="button"
            onClick={() => { setEditing(false); setCode(value ?? ""); }}
            className="inline-flex h-8 items-center rounded-md border border-border-light px-3 text-xs font-bold text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:hover:bg-white/5"
          >
            Cancelar
          </button>
        </dd>
      ) : value ? (
        <dd className="mt-0.5 flex items-center gap-2">
          <span className="text-sm font-serif font-medium text-grafite dark:text-gray-200">{value}</span>
          <button type="button" onClick={() => setEditing(true)} className="text-gray-400 hover:text-primary" title="Editar código">
            <Icon name="edit" size={16} />
          </button>
        </dd>
      ) : isSacado ? (
        <dd className="mt-1 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1 rounded-md bg-blue-50 px-2 py-1 text-xs font-bold text-blue-700 dark:bg-blue-500/10 dark:text-blue-300">
            <Icon name="badge" size={14} />
            Sacado — código 4R não necessário
          </span>
          <button
            type="button"
            onClick={addToCarteira}
            disabled={promoting}
            className="inline-flex h-7 items-center gap-1 rounded-md border border-border-light px-2 text-xs font-bold text-gray-600 hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
          >
            <Icon name={promoting ? "hourglass_empty" : "add_business"} size={14} />
            Adicionar à carteira
          </button>
        </dd>
      ) : (
        <dd className="mt-1 flex flex-wrap items-center gap-2">
          <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-2 py-1 text-xs font-bold text-amber-700 dark:bg-amber-500/10 dark:text-amber-300">
            <Icon name="warning" size={14} />
            Sem código de cliente vinculado
          </span>
          <button
            type="button"
            onClick={() => setEditing(true)}
            className="inline-flex h-7 items-center gap-1 rounded-md border border-border-light px-2 text-xs font-bold text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
          >
            <Icon name="add" size={14} />
            Cadastrar
          </button>
        </dd>
      )}
      {!value && !editing ? (
        <p className="mt-1 text-[11px] text-gray-400">
          {isSacado
            ? "Empresa cadastrada como sacado da Praça de Pagamento. O código 4R só é necessário se ela virar cliente da carteira (cedente)."
            : "Sem o código, o cedente não aparece na Praça de Pagamento."}
        </p>
      ) : null}
    </div>
  );
}
