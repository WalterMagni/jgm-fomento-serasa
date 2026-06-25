"use client";

import { useEffect } from "react";
import Icon from "@/components/ui/Icon";
import { createPortal } from "react-dom";
import dynamic from "next/dynamic";
import type { PaymentPlaceEntry } from "../../types/payment-place";
import { EntryAttachmentsReadOnly } from "./EntryAttachments";

const PaymentPlaceMap = dynamic(() => import("./PaymentPlaceMap"), { ssr: false });

function clean(v?: string | number | null) {
  if (v === null || v === undefined || v === "") return "-";
  return String(v);
}
function fmtKm(v?: number | null) {
  if (v === null || v === undefined) return null;
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(v)} km`;
}
function fmtCnpj(v?: string | null) {
  if (!v) return null;
  const d = v.replace(/\D/g, "");
  if (d.length !== 14) return v;
  return d.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}
function fmtDateTime(v?: string | null) {
  if (!v) return "-";
  return new Date(v).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}
function suggestionLabel(s?: string | null) {
  if (s === "PROVAVEL_SACADO") return "Provável sacado";
  if (s === "PROVAVEL_CEDENTE") return "Provável cedente";
  if (s === "INCONCLUSIVO") return "Inconclusivo";
  return "Sem sugestão";
}

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div>
      <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-0.5 text-sm text-grafite dark:text-white">{clean(value)}</p>
    </div>
  );
}

function DistanceCard({ label, value, color }: { label: string; value?: number | null; color: string }) {
  return (
    <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
      <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-1 whitespace-nowrap text-xl font-bold" style={{ color }}>{fmtKm(value) ?? "—"}</p>
    </div>
  );
}

export default function PaymentPlaceEntryReadOnlyModal({ entry, onClose }: { entry: PaymentPlaceEntry; onClose: () => void }) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);

  if (typeof document === "undefined") return null;

  const points = [
    { label: "Cedente", city: entry.clientCity, lat: entry.clientLatitude, lng: entry.clientLongitude, color: "#612035" },
    { label: "Agência", city: entry.agencyCityPdf, lat: entry.agencyLatitude, lng: entry.agencyLongitude, color: "#D1732C" },
    { label: "Sacado", city: entry.payerCity, lat: entry.payerLatitude, lng: entry.payerLongitude, color: "#2956E0" },
  ];
  const ai = entry.aiAnalysis;

  return createPortal(
    <div className="fixed inset-0 z-[120] flex items-start justify-center overflow-y-auto bg-black/40 px-4 py-6 backdrop-blur-sm" onClick={onClose}>
      <div className="w-full max-w-5xl overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-2xl dark:border-border-dark dark:bg-surface-dark" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-start justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-base font-bold text-grafite dark:text-white">{entry.titleNumber}</h2>
              {entry.analystDecision ? (
                <span className={`inline-flex h-6 items-center rounded-full px-2.5 text-xs font-bold ${
                  entry.analystDecision === "SACADO" ? "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300" : "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
                }`}>
                  {entry.analystDecision === "SACADO" ? "Praça Sacado" : "Praça Cedente"}
                </span>
              ) : null}
            </div>
            <p className="truncate text-xs text-gray-500">{clean(entry.payerName)} · {clean(entry.payerDocument)}</p>
            {entry.decidedAt ? (
              <p className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-300">
                <Icon name="how_to_reg" size={13} />
                Decidido{entry.decidedByName ? ` por ${entry.decidedByName}` : ""} em {fmtDateTime(entry.decidedAt)}
              </p>
            ) : null}
          </div>
          <button type="button" onClick={onClose} className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Fechar (Esc)" aria-label="Fechar">
            <Icon name="close" size={18} />
          </button>
        </div>

        <div className="max-h-[78vh] overflow-y-auto p-5">
          {entry.analystNotes ? (
            <div className="mb-4 rounded-lg border border-border-light bg-white p-3 dark:border-border-dark dark:bg-background-dark">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Observação da análise</p>
              <p className="mt-0.5 text-sm text-grafite dark:text-white">{entry.analystNotes}</p>
            </div>
          ) : null}

          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Distâncias geográficas</p>
          <p className="text-[11px] text-gray-400">Centroides de município (IBGE). Sinal de análise, não prova.</p>
          <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-3">
            <DistanceCard label="Cedente ↔ Agência" value={entry.distanceClientAgencyKm} color="#D1732C" />
            <DistanceCard label="Sacado ↔ Agência" value={entry.distanceAgencyPayerKm} color="#2956E0" />
            <DistanceCard label="Cedente ↔ Sacado" value={entry.distanceClientPayerKm} color="#612035" />
          </div>

          <div className="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
            <div className="space-y-4">
              {entry.automaticSuggestion ? (
                <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
                  <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Pré-análise automática</p>
                  <p className="mt-1 text-sm font-bold text-grafite dark:text-white">{suggestionLabel(entry.automaticSuggestion)} · Sacado {entry.scoreSacado ?? 0} / Cedente {entry.scoreCedente ?? 0}</p>
                  {entry.automaticEvidence ? (
                    <ul className="mt-2 space-y-1 border-t border-border-light pt-2 text-xs text-gray-600 dark:border-border-dark dark:text-gray-300">
                      {entry.automaticEvidence.split("\n").filter(Boolean).map((l, i) => <li key={i} className="flex gap-1.5"><span className="text-gray-300">•</span><span>{l}</span></li>)}
                    </ul>
                  ) : null}
                </div>
              ) : null}

              {ai?.summary ? (
                <div className="rounded-xl border border-violet-200 bg-violet-50/40 p-4 dark:border-violet-500/30 dark:bg-violet-500/5">
                  <p className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wide text-violet-700 dark:text-violet-300"><Icon name="auto_awesome" size={16} />Análise com IA (Gemini)</p>
                  <p className="mt-2 text-sm text-grafite dark:text-gray-200">{ai.summary}</p>
                  {ai.recommendation ? <p className="mt-2 rounded-lg bg-white px-3 py-2 text-xs text-grafite dark:bg-background-dark dark:text-gray-200"><span className="font-bold">Recomendação: </span>{ai.recommendation}</p> : null}
                </div>
              ) : null}

              <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
                <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Partes do título</p>
                <div className="mt-3 space-y-3 text-sm">
                  <div>
                    <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#612035" }}><span className="h-2.5 w-2.5 rounded-full" style={{ background: "#612035" }} />Cedente {entry.clientCode ? <span className="font-normal text-gray-400">· cód. {entry.clientCode}</span> : null}</p>
                    {entry.clientName ? <p className="mt-0.5 font-bold text-grafite dark:text-white">{entry.clientName}</p> : null}
                    {entry.clientDocument ? <p className="text-xs text-gray-500">{fmtCnpj(entry.clientDocument)}</p> : null}
                    <p className="text-grafite dark:text-white">{entry.clientAddress ?? clean(entry.clientCity)}</p>
                  </div>
                  <div>
                    <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#D1732C" }}><span className="h-2.5 w-2.5 rounded-full" style={{ background: "#D1732C" }} />Agência</p>
                    <p className="text-grafite dark:text-white">{entry.agencyAddressResolved ?? clean(entry.agencyCityPdf)}</p>
                  </div>
                  <div>
                    <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#2956E0" }}><span className="h-2.5 w-2.5 rounded-full" style={{ background: "#2956E0" }} />Sacado</p>
                    {entry.payerName ? <p className="mt-0.5 font-bold text-grafite dark:text-white">{entry.payerName}</p> : null}
                    {entry.payerDocument ? <p className="text-xs text-gray-500">{fmtCnpj(entry.payerDocument)}</p> : null}
                    <p className="text-grafite dark:text-white">{entry.payerAddress ?? clean(entry.payerCity)}</p>
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
                <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Dados do lançamento</p>
                <div className="mt-3 grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3">
                  <Field label="Título" value={entry.titleNumber} />
                  <Field label="Vencimento" value={entry.dueDate} />
                  <Field label="Valor pago" value={entry.paidValue} />
                  <Field label="Banco/agência" value={entry.bankAgency} />
                  <Field label="Instituição" value={entry.bankName ?? entry.bacenInstitutionName} />
                  <Field label="Seção" value={entry.section} />
                  <Field label="Ocorrência" value={entry.occurrence} />
                </div>
                {entry.occurrenceComplement ? <div className="mt-3"><Field label="Complemento" value={entry.occurrenceComplement} /></div> : null}
              </div>

              <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
                <p className="mb-3 text-xs font-bold uppercase tracking-wide text-gray-400">Anexos do título</p>
                <EntryAttachmentsReadOnly entryId={entry.id} />
              </div>
            </div>

            <div className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center gap-4 text-xs">
                <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#612035" }} />Cedente</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#D1732C" }} />Agência</span>
                <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#2956E0" }} />Sacado</span>
              </div>
              <div className="min-h-[300px] flex-1 overflow-hidden rounded-xl border border-border-light shadow-sm dark:border-border-dark">
                <PaymentPlaceMap points={points} />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>,
    document.body,
  );
}
