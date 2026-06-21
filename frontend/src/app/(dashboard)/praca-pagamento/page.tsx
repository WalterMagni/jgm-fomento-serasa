"use client";

import { ChangeEvent, Fragment, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import dynamic from "next/dynamic";
import {
  useAnalyzeWithAi,
  useArchivePaymentPlaceBatch,
  useBulkDecidePaymentPlace,
  useCompanyBranches,
  useDeletePaymentPlaceBatch,
  useDecidePaymentPlaceEntry,
  useEnrichAgencyBacen,
  useEnrichPayerCnpj,
  useImportPaymentPlacePdf,
  usePaymentPlaceBatch,
  usePaymentPlaceBatches,
  usePaymentPlaceIndicators,
} from "../../../hooks/usePaymentPlace";
import { PaymentPlaceEntry } from "../../../types/payment-place";

const PaymentPlaceMap = dynamic(() => import("../../../components/payment-place/PaymentPlaceMap"), {
  ssr: false,
  loading: () => (
    <div className="flex h-full min-h-[260px] items-center justify-center rounded-lg border border-dashed border-border-light bg-gray-50 text-xs text-gray-500 dark:border-border-dark dark:bg-white/5">
      Carregando mapa...
    </div>
  ),
});

function formatCnpj(value?: string | null) {
  if (!value) return null;
  const d = value.replace(/\D/g, "");
  if (d.length !== 14) return value;
  return d.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function formatKm(value?: number | null) {
  if (value === null || value === undefined) return null;
  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(value)} km`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatPercent(value?: number | null) {
  if (value === null || value === undefined) return "0,0%";
  return `${new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value)}%`;
}

function formatCurrencyBr(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value === "number") {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
  }
  const normalized = String(value).trim();
  if (!normalized) return null;
  if (normalized.includes("R$")) return normalized;
  const parsed = Number(normalized.replace(/\./g, "").replace(",", ".").replace(/[^\d.-]/g, ""));
  if (Number.isNaN(parsed)) return normalized;
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(parsed);
}

function isToday(value?: string | null) {
  if (!value) return false;
  const date = new Date(value);
  const now = new Date();
  return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth() && date.getDate() === now.getDate();
}

function clean(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return "-";
  return String(value);
}

function normalize(value: string) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}

function categoryLabel(category?: string | null) {
  if (category === "TRADICIONAL") return "Tradicional";
  if (category === "DIGITAL") return "Digital";
  if (category === "COOPERATIVA") return "Cooperativa";
  if (category === "FINANCEIRA") return "Financeira";
  if (category === "INDETERMINADA") return "Indeterminada";
  return "Não classificado";
}

function reliabilityLabel(reliability?: string | null) {
  if (reliability === "ALTA") return "Alta";
  if (reliability === "MEDIA") return "Média";
  if (reliability === "BAIXA") return "Baixa";
  if (reliability === "INDETERMINADA") return "Indeterminada";
  return "Não avaliada";
}

function suggestionLabel(suggestion?: string | null) {
  if (suggestion === "PROVAVEL_SACADO") return "Provável sacado";
  if (suggestion === "PROVAVEL_CEDENTE") return "Provável cedente";
  if (suggestion === "INCONCLUSIVO") return "Inconclusivo";
  return "Sem sugestão";
}

function CategoryPill({ category }: { category?: string | null }) {
  const tone =
    category === "TRADICIONAL"
      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300"
      : category === "COOPERATIVA"
        ? "bg-sky-50 text-sky-700 dark:bg-sky-500/10 dark:text-sky-300"
        : category === "DIGITAL"
          ? "bg-violet-50 text-violet-700 dark:bg-violet-500/10 dark:text-violet-300"
          : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300";

  return (
    <span className={`inline-flex h-7 items-center rounded-full px-3 text-xs font-bold ${tone}`}>
      {categoryLabel(category)}
    </span>
  );
}

function ReliabilityPill({ reliability }: { reliability?: string | null }) {
  const tone =
    reliability === "ALTA"
      ? "bg-emerald-50 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300"
      : reliability === "MEDIA"
        ? "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300"
        : reliability === "BAIXA"
          ? "bg-red-50 text-red-700 dark:bg-red-500/10 dark:text-red-300"
          : "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300";

  return (
    <span className={`inline-flex h-7 items-center rounded-full px-3 text-xs font-bold ${tone}`}>
      {reliabilityLabel(reliability)}
    </span>
  );
}

function SuggestionPill({ suggestion, confidence }: { suggestion?: string | null; confidence?: string | null }) {
  const tone =
    suggestion === "PROVAVEL_SACADO"
      ? "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300"
      : suggestion === "PROVAVEL_CEDENTE"
        ? "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
        : "bg-amber-50 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300";
  return (
    <span className={`inline-flex h-7 items-center gap-1 rounded-full px-3 text-xs font-bold ${tone}`}>
      {suggestionLabel(suggestion)}
      {confidence ? <span className="font-medium opacity-70">· {reliabilityLabel(confidence).toLowerCase()}</span> : null}
    </span>
  );
}

function DistanceChip({ label, value, highlight }: { label: string; value?: number | null; highlight?: boolean }) {
  const km = formatKm(value);
  if (!km) return null;
  return (
    <span
      className={`inline-flex flex-col rounded-md px-2.5 py-1 leading-tight ${
        highlight
          ? "bg-primary/10 text-primary dark:bg-secondary/15 dark:text-secondary"
          : "bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-gray-300"
      }`}
    >
      <span className="text-[11px] font-medium opacity-70">{label}</span>
      <span className="text-sm font-bold">{km}</span>
    </span>
  );
}

function DecisionPill({ decision }: { decision?: string | null }) {
  if (!decision) {
    return <span className="text-xs font-medium text-gray-400">Sem decisão</span>;
  }
  const isPayer = decision === "SACADO";
  return (
    <span
      className={`inline-flex h-7 items-center rounded-full px-3 text-xs font-bold ${
        isPayer
          ? "bg-blue-50 text-blue-700 dark:bg-blue-500/10 dark:text-blue-300"
          : "bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-200"
      }`}
    >
      {isPayer ? "Sacado" : "Cedente"}
    </span>
  );
}

export default function PaymentPlacePage() {
  const [selectedBatchId, setSelectedBatchId] = useState<string | undefined>();
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [sectionFilter, setSectionFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [decisionFilter, setDecisionFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [reliabilityFilter, setReliabilityFilter] = useState("");
  const [search, setSearch] = useState("");
  const [notesByEntry, setNotesByEntry] = useState<Record<string, string>>({});
  const [expandedEntryId, setExpandedEntryId] = useState<string | null>(null);
  const [focusedEntryId, setFocusedEntryId] = useState<string | null>(null);
  const [sortByConfidence, setSortByConfidence] = useState(true);
  const modalBodyRef = useRef<HTMLDivElement>(null);
  const [analysisTab, setAnalysisTab] = useState<"PENDENTES" | "DECIDIDOS" | "TODOS">("PENDENTES");
  const [listCollapsed, setListCollapsed] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  const batchesQuery = usePaymentPlaceBatches();
  const importMutation = useImportPaymentPlacePdf();
  const deleteBatchMutation = useDeletePaymentPlaceBatch();
  const archiveBatchMutation = useArchivePaymentPlaceBatch();

  const allBatches = useMemo(() => batchesQuery.data ?? [], [batchesQuery.data]);
  const batches = useMemo(() => allBatches.filter((item) => item.status !== "ARQUIVADO"), [allBatches]);
  const archivedBatches = useMemo(() => allBatches.filter((item) => item.status === "ARQUIVADO"), [allBatches]);
  const todayBatch = useMemo(() => batches.find((item) => isToday(item.importedAt)), [batches]);
  const activeBatchId = selectedBatchId ?? todayBatch?.id ?? batches[0]?.id;
  const batchQuery = usePaymentPlaceBatch(activeBatchId);
  const indicatorsQuery = usePaymentPlaceIndicators(activeBatchId);
  const decideMutation = useDecidePaymentPlaceEntry(activeBatchId);
  const bulkDecideMutation = useBulkDecidePaymentPlace(activeBatchId);
  const enrichAgencyMutation = useEnrichAgencyBacen(activeBatchId);
  const enrichPayerCnpjMutation = useEnrichPayerCnpj(activeBatchId);
  const aiMutation = useAnalyzeWithAi(activeBatchId);
  const batch = batchQuery.data?.batch;
  const entries = useMemo(() => batchQuery.data?.entries ?? [], [batchQuery.data?.entries]);

  const filteredEntries = useMemo(() => {
    const query = normalize(search);
    return entries.filter((entry) => {
      const haystack = normalize(
        [
          entry.externalId,
          entry.clientCode,
          entry.titleNumber,
          entry.payerDocument,
          entry.payerName,
          entry.clientName,
          entry.clientDocument,
          entry.clientCity,
          entry.agencyCityPdf,
          entry.payerCity,
          entry.bankAgency,
          entry.bankName,
          entry.bacenInstitutionName,
          entry.bacenInstitutionType,
          entry.institutionCategory,
          entry.geographicReliability,
          entry.automaticSuggestion,
          entry.occurrenceComplement,
        ]
          .filter(Boolean)
          .join(" "),
      );
      const tabOk =
        analysisTab === "TODOS" ||
        (analysisTab === "PENDENTES" ? !entry.analystDecision : Boolean(entry.analystDecision));
      return (
        tabOk &&
        (!query || haystack.includes(query)) &&
        (!sectionFilter || entry.section === sectionFilter) &&
        (!statusFilter || entry.analysisStatus === statusFilter) &&
        (!categoryFilter || entry.institutionCategory === categoryFilter) &&
        (!reliabilityFilter || entry.geographicReliability === reliabilityFilter) &&
        (!decisionFilter || (decisionFilter === "SEM_DECISAO" ? !entry.analystDecision : entry.analystDecision === decisionFilter))
      );
    });
  }, [analysisTab, categoryFilter, decisionFilter, entries, reliabilityFilter, search, sectionFilter, statusFilter]);

  const counters = useMemo(() => {
    const reviewed = entries.filter((entry) => entry.analysisStatus === "ANALISE_CONCLUIDA").length;
    const payer = entries.filter((entry) => entry.analystDecision === "SACADO").length;
    const assignor = entries.filter((entry) => entry.analystDecision === "CEDENTE").length;
    const lowReliability = entries.filter((entry) => entry.geographicReliability === "BAIXA").length;
    const digitalOrCooperative = entries.filter((entry) => entry.institutionCategory === "DIGITAL" || entry.institutionCategory === "COOPERATIVA").length;
    return {
      reviewed,
      pending: Math.max(entries.length - reviewed, 0),
      payer,
      assignor,
      lowReliability,
      digitalOrCooperative,
    };
  }, [entries]);

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      importMutation.mutate(file, {
        onSuccess: (data) => setSelectedBatchId(data.batch.id),
      });
    }
    event.target.value = "";
  };

  const deleteBatch = (batchId: string) => {
    const batchToDelete = batches.find((item) => item.id === batchId);
    const confirmed = window.confirm(`Apagar o lote "${batchToDelete?.fileName ?? "selecionado"}"?`);
    if (!confirmed) return;

    deleteBatchMutation.mutate(batchId, {
      onSuccess: () => {
        if (activeBatchId === batchId) {
          setSelectedBatchId(undefined);
        }
      },
    });
  };

  const archiveBatch = () => {
    if (!activeBatchId) return;
    archiveBatchMutation.mutate(
      { batchId: activeBatchId, archived: true },
      { onSuccess: () => setSelectedBatchId(undefined) },
    );
  };

  const decide = (entry: PaymentPlaceEntry, decision: "SACADO" | "CEDENTE") => {
    decideMutation.mutate({
      entryId: entry.id,
      decision,
      notes: notesByEntry[entry.id],
    });
  };

  const confidenceRank = (c?: string | null) => (c === "ALTA" ? 0 : c === "MEDIA" ? 1 : c === "BAIXA" ? 2 : 3);

  const sortedEntries = useMemo(() => {
    if (!sortByConfidence) return filteredEntries;
    return [...filteredEntries].sort((a, b) => {
      const undecidedA = a.analystDecision ? 1 : 0;
      const undecidedB = b.analystDecision ? 1 : 0;
      if (undecidedA !== undecidedB) return undecidedA - undecidedB; // pendentes primeiro
      const reopenedA = a.reopenedAt ? 0 : 1;
      const reopenedB = b.reopenedAt ? 0 : 1;
      if (reopenedA !== reopenedB) return reopenedA - reopenedB; // reabertos no topo dos pendentes
      return confidenceRank(a.automaticConfidence) - confidenceRank(b.automaticConfidence);
    });
  }, [filteredEntries, sortByConfidence]);

  const highConfidenceUndecided = useMemo(
    () =>
      entries.filter(
        (e) =>
          !e.analystDecision &&
          e.automaticConfidence === "ALTA" &&
          (e.automaticSuggestion === "PROVAVEL_SACADO" || e.automaticSuggestion === "PROVAVEL_CEDENTE"),
      ),
    [entries],
  );

  const progressPct = entries.length ? Math.round((counters.reviewed / entries.length) * 100) : 0;
  const expandedEntry = useMemo(() => entries.find((e) => e.id === expandedEntryId) ?? null, [entries, expandedEntryId]);

  const acceptHighConfidence = () => {
    const decisions = highConfidenceUndecided.map((e) => ({
      entryId: e.id,
      decision: (e.automaticSuggestion === "PROVAVEL_SACADO" ? "SACADO" : "CEDENTE") as "SACADO" | "CEDENTE",
    }));
    if (decisions.length) bulkDecideMutation.mutate(decisions);
  };

  // Mantém o foco do teclado em um lançamento válido da lista visível.
  useEffect(() => {
    if (sortedEntries.length === 0) {
      if (focusedEntryId !== null) setFocusedEntryId(null);
      return;
    }
    if (!focusedEntryId || !sortedEntries.some((e) => e.id === focusedEntryId)) {
      setFocusedEntryId(sortedEntries[0].id);
    }
  }, [sortedEntries, focusedEntryId]);

  // Atalhos de teclado: S=sacado, C=cedente, ↑/↓ navega, E/Enter abre detalhe, Esc fecha.
  useEffect(() => {
    const handler = (ev: KeyboardEvent) => {
      const key = ev.key.toLowerCase();

      // Esc fecha o modal, mesmo com foco em campo de texto.
      if (ev.key === "Escape" && expandedEntryId) {
        setExpandedEntryId(null);
        ev.preventDefault();
        return;
      }

      const target = ev.target as HTMLElement | null;
      const typing = !!target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT" || target.isContentEditable);
      if (typing) return;

      // Com o modal aberto: S/C decide, I analisa IA, ←/→ navega lançamentos, ↑/↓ rola o conteúdo.
      if (expandedEntryId) {
        const openIdx = sortedEntries.findIndex((e) => e.id === expandedEntryId);
        const open = sortedEntries[openIdx] ?? entries.find((e) => e.id === expandedEntryId);
        const openAt = (i: number) => {
          const t = sortedEntries[Math.max(0, Math.min(i, sortedEntries.length - 1))];
          if (t) {
            setExpandedEntryId(t.id);
            setFocusedEntryId(t.id);
            modalBodyRef.current?.scrollTo({ top: 0 });
          }
        };
        if (open && key === "s") { decide(open, "SACADO"); ev.preventDefault(); }
        else if (open && key === "c") { decide(open, "CEDENTE"); ev.preventDefault(); }
        else if (open && key === "i") { aiMutation.mutate(open.id); ev.preventDefault(); }
        else if (ev.key === "ArrowRight") { openAt(openIdx + 1); ev.preventDefault(); }
        else if (ev.key === "ArrowLeft") { openAt(openIdx - 1); ev.preventDefault(); }
        else if (ev.key === "ArrowDown") { modalBodyRef.current?.scrollBy({ top: 220, behavior: "smooth" }); ev.preventDefault(); }
        else if (ev.key === "ArrowUp") { modalBodyRef.current?.scrollBy({ top: -220, behavior: "smooth" }); ev.preventDefault(); }
        return;
      }

      if (sortedEntries.length === 0) return;
      const idx = Math.max(0, sortedEntries.findIndex((e) => e.id === focusedEntryId));
      const current = sortedEntries[idx];
      const focusAt = (i: number) => setFocusedEntryId(sortedEntries[Math.max(0, Math.min(i, sortedEntries.length - 1))].id);

      if (key === "s") {
        decide(current, "SACADO");
        focusAt(idx + 1);
        ev.preventDefault();
      } else if (key === "c") {
        decide(current, "CEDENTE");
        focusAt(idx + 1);
        ev.preventDefault();
      } else if (key === "i") {
        aiMutation.mutate(current.id);
        ev.preventDefault();
      } else if (ev.key === "ArrowDown") {
        focusAt(idx + 1);
        ev.preventDefault();
      } else if (ev.key === "ArrowUp") {
        focusAt(idx - 1);
        ev.preventDefault();
      } else if (key === "e" || ev.key === "Enter") {
        setExpandedEntryId(current.id);
        ev.preventDefault();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortedEntries, focusedEntryId, expandedEntryId, entries, aiMutation]);

  // Rola o lançamento focado para dentro da área visível.
  useEffect(() => {
    if (!focusedEntryId || expandedEntryId) return;
    document.getElementById(`pp-row-${focusedEntryId}`)?.scrollIntoView({ block: "nearest" });
  }, [focusedEntryId, expandedEntryId]);

  return (
    <div className="mx-auto max-w-[1900px] space-y-5">
      <header className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <nav className="mb-2 flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <Link href="/" className="transition-colors hover:text-primary">
              Carteira
            </Link>
            <span>/</span>
            <span className="font-medium text-grafite dark:text-white">Praça de Pagamento</span>
          </nav>
          <h1 className="font-sans text-3xl font-bold text-grafite dark:text-white">Praça de Pagamento</h1>
          <p className="mt-1 max-w-3xl text-sm text-gray-500 dark:text-gray-400">
            Importe o retorno bancário diário, revise os lançamentos de auditoria e classifique cada caso como sacado ou cedente.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div
            className={`inline-flex h-11 items-center gap-2 rounded-xl border px-4 text-sm font-bold ${
              todayBatch
                ? "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300"
                : "border-red-200 bg-red-50 text-red-700 dark:border-red-500/30 dark:bg-red-500/10 dark:text-red-300"
            }`}
          >
            <span className="material-icons-outlined text-[20px]">{todayBatch ? "check_circle" : "error"}</span>
            {todayBatch ? "Lote do dia importado" : "Lote do dia não importado"}
          </div>
          <button
            type="button"
            onClick={() => setShowBatchModal(true)}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-border-light bg-white px-4 text-sm font-bold text-grafite shadow-sm transition-colors hover:bg-gray-50 dark:border-border-dark dark:bg-surface-dark dark:text-white dark:hover:bg-white/5"
          >
            <span className="material-icons-outlined text-[20px]">inventory_2</span>
            Lotes
          </button>
          <label className="inline-flex h-11 cursor-pointer items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-primary/90">
            <span className="material-icons-outlined text-[20px]">upload_file</span>
            Importar PDF
            <input type="file" accept="application/pdf,.pdf" className="hidden" onChange={handleFile} disabled={importMutation.isPending} />
          </label>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-6">
        <Metric label="Lançamentos" value={entries.length} />
        <Metric label="Auditoria eletrônica" value={batch?.auditEntries ?? 0} />
        <Metric label="Agências não localizadas" value={batch?.unlocatedAgencyEntries ?? 0} />
        <Metric label="Digitais/cooperativas" value={counters.digitalOrCooperative} />
        <Metric label="Baixa confiança" value={counters.lowReliability} tone="red" />
        <Metric label="Pendentes" value={counters.pending} tone="amber" />
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-grafite dark:text-white">Indicadores do lote</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Leitura rápida da qualidade da praça e da aderência entre sugestão automática e decisão humana.
            </p>
          </div>
          {indicatorsQuery.data?.fileName ? (
            <p className="hidden max-w-[480px] truncate text-xs text-gray-400 lg:block">{indicatorsQuery.data.fileName}</p>
          ) : null}
        </div>

        {indicatorsQuery.isLoading && activeBatchId ? (
          <div className="rounded-xl border border-border-light bg-surface-light p-4 text-sm text-gray-500 shadow-sm dark:border-border-dark dark:bg-surface-dark">
            Carregando indicadores do lote...
          </div>
        ) : indicatorsQuery.data ? (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
            <InsightMetric
              label="Agências localizadas"
              value={formatPercent(indicatorsQuery.data.locatedAgencyPct)}
              detail={`${indicatorsQuery.data.locatedAgencyCount} de ${indicatorsQuery.data.totalEntries} com endereço/cidade resolvidos`}
              tone="emerald"
            />
            <InsightMetric
              label="Baixa confiança geográfica"
              value={formatPercent(indicatorsQuery.data.lowReliabilityPct)}
              detail={`${indicatorsQuery.data.lowReliabilityCount} de ${indicatorsQuery.data.totalEntries} marcados como baixa`}
              tone="red"
            />
            <InsightMetric
              label="Concordância sugestão × analista"
              value={formatPercent(indicatorsQuery.data.agreementPct)}
              detail={`${indicatorsQuery.data.agreementCount} de ${indicatorsQuery.data.comparableDecisionCount} casos comparáveis`}
              tone="blue"
            />
            <InsightMetric
              label="Divergência sugestão × analista"
              value={formatPercent(indicatorsQuery.data.disagreementPct)}
              detail={`${indicatorsQuery.data.disagreementCount} de ${indicatorsQuery.data.comparableDecisionCount} casos comparáveis`}
              tone="amber"
            />
          </div>
        ) : null}
      </section>

      <section className="space-y-4">
        <main className="min-w-0 space-y-4">
          <section className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(240px,1fr)_180px_160px_170px_170px_160px_auto]">
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Busca</span>
                <input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Título, sacado, cedente, documento, cidade, banco/agência..."
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                />
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Seção</span>
                <select
                  value={sectionFilter}
                  onChange={(event) => setSectionFilter(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                >
                  <option value="">Todas</option>
                  <option value="Auditoria Eletrônica">Auditoria Eletrônica</option>
                  <option value="Agências Não Localizadas">Agências Não Localizadas</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Status</span>
                <select
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                >
                  <option value="">Todos</option>
                  <option value="ANALISE_PENDENTE">Pendente</option>
                  <option value="ANALISE_CONCLUIDA">Concluído</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Instituição</span>
                <select
                  value={categoryFilter}
                  onChange={(event) => setCategoryFilter(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                >
                  <option value="">Todas</option>
                  <option value="TRADICIONAL">Tradicional</option>
                  <option value="DIGITAL">Digital</option>
                  <option value="COOPERATIVA">Cooperativa</option>
                  <option value="FINANCEIRA">Financeira</option>
                  <option value="INDETERMINADA">Indeterminada</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Confiança</span>
                <select
                  value={reliabilityFilter}
                  onChange={(event) => setReliabilityFilter(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                >
                  <option value="">Todas</option>
                  <option value="ALTA">Alta</option>
                  <option value="MEDIA">Média</option>
                  <option value="BAIXA">Baixa</option>
                  <option value="INDETERMINADA">Indeterminada</option>
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Decisão</span>
                <select
                  value={decisionFilter}
                  onChange={(event) => setDecisionFilter(event.target.value)}
                  className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                >
                  <option value="">Todas</option>
                  <option value="SEM_DECISAO">Sem decisão</option>
                  <option value="SACADO">Sacado</option>
                  <option value="CEDENTE">Cedente</option>
                </select>
              </label>
              <button
                type="button"
                onClick={() => {
                  setSearch("");
                  setSectionFilter("");
                  setStatusFilter("");
                  setCategoryFilter("");
                  setReliabilityFilter("");
                  setDecisionFilter("");
                }}
                className="mt-5 h-10 rounded-lg border border-border-light px-3 text-sm font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
              >
                Limpar
              </button>
            </div>
          </section>

          <section className="overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark">
            <div className="flex flex-col gap-3 border-b border-border-light p-4 dark:border-border-dark">
              <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div className="min-w-0">
                  <h2 className="truncate text-sm font-bold text-grafite dark:text-white">{batch ? batch.fileName : "Nenhum lote selecionado"}</h2>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {filteredEntries.length} de {entries.length} exibidos · Sacados {counters.payer} · Cedentes {counters.assignor}
                  </p>
                  {batch ? (
                    <p className="mt-0.5 inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                      <span className="material-icons-outlined text-[14px]">schedule</span>
                      Importado em {formatDateTime(batch.importedAt)}
                      {batch.importedByName ? ` por ${batch.importedByName}` : ""}
                    </p>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={acceptHighConfidence}
                    disabled={highConfidenceUndecided.length === 0 || bulkDecideMutation.isPending}
                    className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-3 text-xs font-bold text-white shadow-sm transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50"
                    title="Aceitar as sugestões de alta confiança ainda não decididas"
                  >
                    <span className="material-icons-outlined text-[16px]">done_all</span>
                    Aceitar {highConfidenceUndecided.length} de alta confiança
                  </button>
                  <button
                    type="button"
                    onClick={() => setSortByConfidence((v) => !v)}
                    className={`inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border px-3 text-xs font-bold transition-colors ${
                      sortByConfidence
                        ? "border-primary bg-primary/5 text-primary dark:border-secondary dark:text-secondary"
                        : "border-border-light text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    }`}
                    title="Ordenar por confiança (pendentes primeiro)"
                  >
                    <span className="material-icons-outlined text-[16px]">sort</span>
                    Confiança
                  </button>
                  {activeBatchId ? (
                    <>
                      <button
                        type="button"
                        onClick={() => setListCollapsed((v) => !v)}
                        className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                        title={listCollapsed ? "Expandir lista" : "Minimizar lista"}
                        aria-label={listCollapsed ? "Expandir lista" : "Minimizar lista"}
                      >
                        <span className="material-icons-outlined text-[18px]">{listCollapsed ? "unfold_more" : "unfold_less"}</span>
                      </button>
                      <button
                        type="button"
                        onClick={archiveBatch}
                        disabled={archiveBatchMutation.isPending}
                        className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                        title="Arquivar este lote"
                      >
                        <span className="material-icons-outlined text-[16px]">inventory_2</span>
                        Arquivar
                      </button>
                    </>
                  ) : null}
                </div>
              </div>

              <div className="flex items-center gap-1 rounded-lg bg-gray-100 p-1 dark:bg-white/5">
                {([
                  ["PENDENTES", `Pendentes (${counters.pending})`],
                  ["DECIDIDOS", `Decididos (${counters.reviewed})`],
                  ["TODOS", `Todos (${entries.length})`],
                ] as const).map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setAnalysisTab(key)}
                    className={`h-8 flex-1 rounded-md text-xs font-bold transition-colors ${
                      analysisTab === key
                        ? "bg-white text-grafite shadow-sm dark:bg-surface-dark dark:text-white"
                        : "text-gray-500 hover:text-grafite dark:text-gray-400 dark:hover:text-white"
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-3">
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-gray-200 dark:bg-white/10">
                  <div className="h-full rounded-full bg-emerald-500 transition-all" style={{ width: `${progressPct}%` }} />
                </div>
                <span className="whitespace-nowrap text-xs font-bold text-gray-600 dark:text-gray-300">
                  {counters.reviewed}/{entries.length} decididos
                </span>
              </div>
              <p className="text-[11px] text-gray-400">
                Atalhos: <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">S</kbd> sacado ·{" "}
                <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">C</kbd> cedente ·{" "}
                <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">↑↓</kbd> navegar ·{" "}
                <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">E</kbd> detalhes ·{" "}
                <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">I</kbd> análise IA
              </p>
            </div>

            {listCollapsed ? (
              <button
                type="button"
                onClick={() => setListCollapsed(false)}
                className="flex w-full items-center justify-center gap-2 p-4 text-xs font-bold text-gray-500 transition-colors hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-white/5"
              >
                <span className="material-icons-outlined text-[18px]">unfold_more</span>
                Lista minimizada — {counters.pending} pendentes. Clique para expandir.
              </button>
            ) : batchQuery.isLoading ? (
              <div className="p-6 text-sm text-gray-500">Carregando lançamentos...</div>
            ) : !activeBatchId ? (
              <div className="p-6 text-sm text-gray-500">Importe ou selecione um lote para iniciar a análise.</div>
            ) : sortedEntries.length === 0 ? (
              <div className="p-6 text-sm text-gray-500">Nenhum lançamento encontrado para os filtros atuais.</div>
            ) : (
              <div className="divide-y divide-border-light dark:divide-border-dark">
                {sortedEntries.map((entry) => {
                  const focused = entry.id === focusedEntryId;
                  const suggested = entry.automaticSuggestion === "PROVAVEL_SACADO" ? "SACADO" : entry.automaticSuggestion === "PROVAVEL_CEDENTE" ? "CEDENTE" : null;
                  return (
                    <Fragment key={entry.id}>
                      <div
                        id={`pp-row-${entry.id}`}
                        onClick={() => setFocusedEntryId(entry.id)}
                        onDoubleClick={() => { setFocusedEntryId(entry.id); setExpandedEntryId(entry.id); }}
                        className={`flex flex-col gap-2 px-4 py-2.5 transition-colors lg:flex-row lg:items-center ${
                          focused
                            ? "bg-primary/5 ring-1 ring-inset ring-primary/40 dark:bg-secondary/10 dark:ring-secondary/40"
                            : entry.reopenedAt
                              ? "bg-amber-50/70 ring-1 ring-inset ring-amber-300 dark:bg-amber-500/10 dark:ring-amber-500/40"
                              : "hover:bg-gray-50/70 dark:hover:bg-white/[0.03]"
                        }`}
                      >
                        {/* Identificação */}
                        <div className="min-w-0 lg:w-[280px]">
                          <div className="flex items-center gap-2">
                            <p className="truncate font-bold text-grafite dark:text-white">{entry.titleNumber}</p>
                            {entry.reopenedAt ? (
                              <span className="inline-flex items-center gap-0.5 rounded bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold text-white">
                                <span className="material-icons-outlined text-[12px]">undo</span>reaberto
                              </span>
                            ) : null}
                            {entry.section === "Agências Não Localizadas" ? (
                              <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">não loc.</span>
                            ) : null}
                          </div>
                          <p className="truncate text-xs text-gray-500">
                            <span className="font-bold text-blue-600/70 dark:text-blue-300/70">Sacado:</span> {clean(entry.payerName)}
                          </p>
                          <p className="truncate text-xs text-gray-500">
                            <span className="font-bold text-primary/70 dark:text-secondary/70">Cedente:</span>{" "}
                            {entry.clientName ?? <span className="text-gray-400">cód. {clean(entry.clientCode)} (sem cadastro)</span>}
                          </p>
                        </div>

                        <div className="min-w-0 lg:w-[360px]">
                          <div className="space-y-0.5 text-[11px] text-gray-500 dark:text-gray-400">
                            <div className="min-w-0">
                              <p className="truncate" title={clean(entry.bankName ?? entry.bacenInstitutionName)}>
                                <span className="font-bold text-gray-600 dark:text-gray-300">Instituição:</span>{" "}
                                {clean(entry.bankName ?? entry.bacenInstitutionName)}
                              </p>
                              <p className="truncate">
                                <span className="font-bold text-gray-600 dark:text-gray-300">Banco/Agência:</span> {clean(entry.bankAgency)}
                              </p>
                              <p className="truncate">
                                <span className="font-bold text-gray-600 dark:text-gray-300">Vencimento:</span> {clean(entry.dueDate)}
                              </p>
                              <p className="truncate">
                                <span className="font-bold text-gray-600 dark:text-gray-300">Valor pago:</span>{" "}
                                {formatCurrencyBr(entry.paidValue) ?? clean(entry.paidValue)}
                              </p>
                            </div>
                          </div>
                        </div>

                        {/* Sugestão + distâncias */}
                        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                          <SuggestionPill suggestion={entry.automaticSuggestion} confidence={entry.automaticConfidence} />
                          <DistanceChip label="Cedente ↔ Agência" value={entry.distanceClientAgencyKm} />
                          <DistanceChip label="Sacado ↔ Agência" value={entry.distanceAgencyPayerKm} />
                          <DistanceChip label="Cedente ↔ Sacado" value={entry.distanceClientPayerKm} highlight />
                        </div>

                        {/* Decisão + ações */}
                        <div className="flex items-center justify-end gap-2 lg:w-[300px]">
                          {entry.analystDecision ? <DecisionPill decision={entry.analystDecision} /> : null}
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); decide(entry, "SACADO"); }}
                            disabled={decideMutation.isPending}
                            className={`inline-flex h-8 items-center justify-center rounded-lg px-3 text-xs font-bold transition-colors disabled:opacity-60 ${
                              entry.analystDecision === "SACADO"
                                ? "bg-blue-600 text-white"
                                : suggested === "SACADO"
                                  ? "bg-blue-100 text-blue-700 ring-1 ring-blue-300 hover:bg-blue-200 dark:bg-blue-500/15 dark:text-blue-300"
                                  : "border border-border-light text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            }`}
                          >
                            Sacado
                          </button>
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); decide(entry, "CEDENTE"); }}
                            disabled={decideMutation.isPending}
                            className={`inline-flex h-8 items-center justify-center rounded-lg px-3 text-xs font-bold transition-colors disabled:opacity-60 ${
                              entry.analystDecision === "CEDENTE"
                                ? "bg-slate-700 text-white"
                                : suggested === "CEDENTE"
                                  ? "bg-slate-200 text-slate-700 ring-1 ring-slate-300 hover:bg-slate-300 dark:bg-slate-600/40 dark:text-slate-200"
                                  : "border border-border-light text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            }`}
                          >
                            Cedente
                          </button>
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); setFocusedEntryId(entry.id); setExpandedEntryId(entry.id); }}
                            className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            title="Ver detalhes"
                            aria-label="Ver detalhes"
                          >
                            <span className="material-icons-outlined text-[18px]">open_in_full</span>
                          </button>
                        </div>
                      </div>
                    </Fragment>
                  );
                })}
              </div>
            )}
          </section>
        </main>
      </section>

      {showBatchModal ? (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/30 px-4 py-20 backdrop-blur-sm">
          <div className="w-full max-w-2xl overflow-hidden rounded-xl border border-border-light bg-white shadow-2xl dark:border-border-dark dark:bg-surface-dark">
            <div className="flex items-center justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
              <div>
                <h2 className="text-sm font-bold text-grafite dark:text-white">Lotes importados</h2>
                <p className="text-xs text-gray-500 dark:text-gray-400">{batches.length} registros disponíveis</p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => batchesQuery.refetch()}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                  title="Atualizar lotes"
                >
                  <span className="material-icons-outlined text-[18px]">refresh</span>
                </button>
                <button
                  type="button"
                  onClick={() => setShowBatchModal(false)}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                  title="Fechar lotes"
                  aria-label="Fechar lotes"
                >
                  <span className="material-icons-outlined text-[18px]">close</span>
                </button>
              </div>
            </div>

            <div className="max-h-[520px] overflow-y-auto p-3">
              {batchesQuery.isLoading ? (
                <div className="p-4 text-sm text-gray-500">Carregando lotes...</div>
              ) : batches.length === 0 ? (
                <div className="p-4 text-sm text-gray-500">Nenhum lote importado.</div>
              ) : (
                <div className="space-y-2">
                  {batches.map((item) => {
                    const active = item.id === activeBatchId;
                    return (
                      <div
                        key={item.id}
                        className={`flex items-start gap-3 rounded-lg border p-3 transition-colors ${
                          active
                            ? "border-primary bg-primary/5 dark:border-secondary dark:bg-secondary/10"
                            : "border-border-light bg-white hover:bg-gray-50 dark:border-border-dark dark:bg-background-dark dark:hover:bg-white/5"
                        }`}
                      >
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedBatchId(item.id);
                            setShowBatchModal(false);
                          }}
                          className="min-w-0 flex-1 text-left"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <p className="line-clamp-2 text-sm font-bold text-grafite dark:text-white">{item.fileName}</p>
                            <span className="rounded-md bg-gray-100 px-2 py-1 text-xs font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                              {item.totalEntries}
                            </span>
                          </div>
                          <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">{formatDateTime(item.importedAt)}</p>
                          <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                            {item.auditEntries} auditoria / {item.unlocatedAgencyEntries} agências
                          </p>
                        </button>
                        <button
                          type="button"
                          onClick={() => archiveBatchMutation.mutate({ batchId: item.id, archived: true })}
                          disabled={archiveBatchMutation.isPending}
                          className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                          title="Arquivar lote"
                        >
                          <span className="material-icons-outlined text-[18px]">inventory_2</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => deleteBatch(item.id)}
                          disabled={deleteBatchMutation.isPending}
                          className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-red-100 text-red-600 transition-colors hover:bg-red-50 disabled:opacity-60 dark:border-red-500/20 dark:text-red-300 dark:hover:bg-red-500/10"
                          title="Apagar lote"
                        >
                          <span className="material-icons-outlined text-[18px]">delete</span>
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}

              {archivedBatches.length > 0 ? (
                <div className="mt-3 border-t border-border-light pt-3 dark:border-border-dark">
                  <button
                    type="button"
                    onClick={() => setShowArchived((v) => !v)}
                    className="flex w-full items-center justify-between rounded-lg px-2 py-1.5 text-xs font-bold text-gray-500 transition-colors hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-white/5"
                  >
                    <span className="inline-flex items-center gap-1.5">
                      <span className="material-icons-outlined text-[16px]">inventory_2</span>
                      Arquivados ({archivedBatches.length})
                    </span>
                    <span className="material-icons-outlined text-[18px]">{showArchived ? "expand_less" : "expand_more"}</span>
                  </button>
                  {showArchived ? (
                    <div className="mt-2 space-y-2">
                      {archivedBatches.map((item) => (
                        <div
                          key={item.id}
                          className="flex items-center gap-3 rounded-lg border border-border-light bg-gray-50/50 p-3 dark:border-border-dark dark:bg-white/[0.02]"
                        >
                          <div className="min-w-0 flex-1">
                            <p className="line-clamp-1 text-sm font-medium text-gray-600 dark:text-gray-300">{item.fileName}</p>
                            <p className="mt-1 text-xs text-gray-400">{formatDateTime(item.importedAt)} · {item.totalEntries} lançamentos</p>
                          </div>
                          <button
                            type="button"
                            onClick={() => archiveBatchMutation.mutate({ batchId: item.id, archived: false })}
                            disabled={archiveBatchMutation.isPending}
                            className="inline-flex h-9 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            title="Restaurar lote"
                          >
                            <span className="material-icons-outlined text-[16px]">unarchive</span>
                            Restaurar
                          </button>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}

      {expandedEntry && typeof document !== "undefined" ? createPortal(
        <div
          className="fixed inset-0 z-[120] flex items-start justify-center overflow-y-auto bg-black/40 px-4 py-6 backdrop-blur-sm"
          onClick={() => setExpandedEntryId(null)}
        >
          <div
            className="w-full max-w-5xl overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-2xl dark:border-border-dark dark:bg-surface-dark"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h2 className="truncate text-base font-bold text-grafite dark:text-white">{expandedEntry.titleNumber}</h2>
                  <SuggestionPill suggestion={expandedEntry.automaticSuggestion} confidence={expandedEntry.automaticConfidence} />
                  {expandedEntry.analystDecision ? <DecisionPill decision={expandedEntry.analystDecision} /> : null}
                </div>
                <p className="truncate text-xs text-gray-500">{clean(expandedEntry.payerName)} · {clean(expandedEntry.payerDocument)}</p>
                {expandedEntry.decidedAt ? (
                  <p className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-300">
                    <span className="material-icons-outlined text-[13px]">how_to_reg</span>
                    Decidido{expandedEntry.decidedByName ? ` por ${expandedEntry.decidedByName}` : ""} em {formatDateTime(expandedEntry.decidedAt)}
                  </p>
                ) : null}
                <p className="mt-0.5 hidden text-[11px] text-gray-400 lg:block">
                  <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">←→</kbd> trocar lançamento ·{" "}
                  <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">↑↓</kbd> rolar ·{" "}
                  <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">S</kbd>/<kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">C</kbd> decidir ·{" "}
                  <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">I</kbd> IA ·{" "}
                  <kbd className="rounded bg-gray-100 px-1 font-mono dark:bg-white/10">Esc</kbd> fechar
                </p>
              </div>
              <div className="flex flex-shrink-0 items-center gap-2">
                <button
                  type="button"
                  onClick={() => decide(expandedEntry, "SACADO")}
                  disabled={decideMutation.isPending}
                  className={`inline-flex h-9 items-center justify-center rounded-lg px-4 text-xs font-bold transition-colors disabled:opacity-60 ${
                    expandedEntry.analystDecision === "SACADO" ? "bg-blue-600 text-white" : "bg-blue-100 text-blue-700 hover:bg-blue-200 dark:bg-blue-500/15 dark:text-blue-300"
                  }`}
                >
                  Sacado
                </button>
                <button
                  type="button"
                  onClick={() => decide(expandedEntry, "CEDENTE")}
                  disabled={decideMutation.isPending}
                  className={`inline-flex h-9 items-center justify-center rounded-lg px-4 text-xs font-bold transition-colors disabled:opacity-60 ${
                    expandedEntry.analystDecision === "CEDENTE" ? "bg-slate-700 text-white" : "bg-slate-200 text-slate-700 hover:bg-slate-300 dark:bg-slate-600/40 dark:text-slate-200"
                  }`}
                >
                  Cedente
                </button>
                <button
                  type="button"
                  onClick={() => setExpandedEntryId(null)}
                  className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                  title="Fechar (Esc)"
                  aria-label="Fechar"
                >
                  <span className="material-icons-outlined text-[18px]">close</span>
                </button>
              </div>
            </div>
            <div ref={modalBodyRef} className="max-h-[78vh] overflow-y-auto p-5">
              <div className="mb-4">
                <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Observação da análise</p>
                <textarea
                  value={notesByEntry[expandedEntry.id] ?? expandedEntry.analystNotes ?? ""}
                  onChange={(event) => setNotesByEntry((current) => ({ ...current, [expandedEntry.id]: event.target.value }))}
                  onBlur={() => {
                    const note = notesByEntry[expandedEntry.id];
                    if (expandedEntry.analystDecision && note !== undefined && note !== (expandedEntry.analystNotes ?? "")) {
                      decideMutation.mutate({ entryId: expandedEntry.id, decision: expandedEntry.analystDecision, notes: note });
                    }
                  }}
                  rows={2}
                  placeholder="Anotação (salva ao decidir ou ao sair do campo se já houver decisão)"
                  className="mt-1 w-full rounded-lg border border-border-light bg-white px-3 py-2 text-xs text-grafite outline-none transition focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
                />
              </div>
              <EntryDetail
                entry={expandedEntry}
                onEnrichAgency={() => enrichAgencyMutation.mutate(expandedEntry.id)}
                enriching={enrichAgencyMutation.isPending}
                onEnrichPayerCnpj={() => enrichPayerCnpjMutation.mutate(expandedEntry.id)}
                enrichingPayerCnpj={enrichPayerCnpjMutation.isPending}
                onAnalyzeAi={() => aiMutation.mutate(expandedEntry.id)}
                analyzingAi={aiMutation.isPending}
              />
            </div>
          </div>
        </div>,
        document.body,
      ) : null}
    </div>
  );
}

function DistanceCard({ label, from, to, value, color }: { label: string; from?: string | null; to?: string | null; value?: number | null; color: string }) {
  const km = formatKm(value);
  return (
    <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
      <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-1 whitespace-nowrap text-xl font-bold" style={{ color }}>{km ?? "—"}</p>
      <p className="mt-1 truncate text-xs text-gray-500" title={`${clean(from)} → ${clean(to)}`}>
        {clean(from)} → {clean(to)}
      </p>
    </div>
  );
}

function InsightMetric({
  label,
  value,
  detail,
  tone = "gray",
}: {
  label: string;
  value: string;
  detail: string;
  tone?: "gray" | "emerald" | "red" | "blue" | "amber";
}) {
  const tones: Record<string, string> = {
    gray: "border-border-light bg-surface-light dark:border-border-dark dark:bg-surface-dark",
    emerald: "border-emerald-200 bg-emerald-50/70 dark:border-emerald-500/30 dark:bg-emerald-500/10",
    red: "border-red-200 bg-red-50/70 dark:border-red-500/30 dark:bg-red-500/10",
    blue: "border-blue-200 bg-blue-50/70 dark:border-blue-500/30 dark:bg-blue-500/10",
    amber: "border-amber-200 bg-amber-50/70 dark:border-amber-500/30 dark:bg-amber-500/10",
  };

  return (
    <div className={`rounded-xl border p-4 shadow-sm ${tones[tone]}`}>
      <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{value}</p>
      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{detail}</p>
    </div>
  );
}

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div>
      <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className="mt-0.5 text-sm text-grafite dark:text-white">{clean(value)}</p>
    </div>
  );
}

function EntryDetail({ entry, onEnrichAgency, enriching, onEnrichPayerCnpj, enrichingPayerCnpj, onAnalyzeAi, analyzingAi }: { entry: PaymentPlaceEntry; onEnrichAgency: () => void; enriching: boolean; onEnrichPayerCnpj: () => void; enrichingPayerCnpj: boolean; onAnalyzeAi: () => void; analyzingAi: boolean }) {
  const [showBranches, setShowBranches] = useState(false);
  const branchesQuery = useCompanyBranches(entry.payerDocument ?? undefined, showBranches);
  const branchPoints = (branchesQuery.data ?? [])
    .filter((b) => typeof b.latitude === "number" && typeof b.longitude === "number")
    .map((b) => ({
      label: b.matriz ? "Matriz (sacado)" : "Filial (sacado)",
      city: b.municipio,
      lat: b.latitude,
      lng: b.longitude,
      color: "#1F9D55",
    }));

  const points = [
    { label: "Cedente", city: entry.clientCity, lat: entry.clientLatitude, lng: entry.clientLongitude, color: "#612035" },
    { label: "Agência", city: entry.agencyCityPdf, lat: entry.agencyLatitude, lng: entry.agencyLongitude, color: "#D1732C" },
    { label: "Sacado", city: entry.payerCity, lat: entry.payerLatitude, lng: entry.payerLongitude, color: "#2956E0" },
    ...(showBranches ? branchPoints : []),
  ];

  return (
    <div className="space-y-5">
      <div>
        <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Distâncias geográficas</p>
        <p className="text-[11px] text-gray-400">Centroide do município (IBGE); a agência usa o endereço exato quando localizado. Sinal de análise, não prova.</p>
        <div className="mt-2 grid grid-cols-1 gap-3 sm:grid-cols-3">
          <DistanceCard label="Cedente ↔ Agência" from={entry.clientCity} to={entry.agencyCityPdf} value={entry.distanceClientAgencyKm} color="#D1732C" />
          <DistanceCard label="Sacado ↔ Agência" from={entry.payerCity} to={entry.agencyCityPdf} value={entry.distanceAgencyPayerKm} color="#2956E0" />
          <DistanceCard label="Cedente ↔ Sacado" from={entry.clientCity} to={entry.payerCity} value={entry.distanceClientPayerKm} color="#612035" />
        </div>
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <div className="space-y-4">
        <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
          <div className="flex items-center justify-between gap-2">
            <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Pré-análise automática</p>
            <SuggestionPill suggestion={entry.automaticSuggestion} confidence={entry.automaticConfidence} />
          </div>
          <div className="mt-3 flex items-center gap-4 text-sm">
            <span className="font-bold text-blue-600 dark:text-blue-300">Sacado {entry.scoreSacado ?? 0}</span>
            <span className="text-gray-300">·</span>
            <span className="font-bold text-slate-600 dark:text-slate-300">Cedente {entry.scoreCedente ?? 0}</span>
          </div>
          {entry.automaticEvidence ? (
            <ul className="mt-3 space-y-1 border-t border-border-light pt-3 text-xs text-gray-600 dark:border-border-dark dark:text-gray-300">
              {entry.automaticEvidence.split("\n").filter(Boolean).map((line, i) => (
                <li key={i} className="flex gap-1.5">
                  <span className="text-gray-300">•</span>
                  <span>{line}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>

        <div className="rounded-xl border border-violet-200 bg-violet-50/40 p-4 shadow-sm dark:border-violet-500/30 dark:bg-violet-500/5">
          <div className="flex items-center justify-between gap-2">
            <p className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wide text-violet-700 dark:text-violet-300">
              <span className="material-icons-outlined text-[16px]">auto_awesome</span>
              Análise com IA (Gemini)
            </p>
            <button
              type="button"
              onClick={onAnalyzeAi}
              disabled={analyzingAi}
              className="inline-flex h-7 items-center gap-1 rounded-lg bg-violet-600 px-2.5 text-[11px] font-bold text-white transition-colors hover:bg-violet-700 disabled:opacity-60"
            >
              <span className="material-icons-outlined text-[14px]">{analyzingAi ? "hourglass_empty" : "auto_awesome"}</span>
              {entry.aiAnalysis?.summary ? "Reanalisar" : "Analisar"}
            </button>
          </div>
          {entry.aiAnalysis?.summary ? (
            <div className="mt-3 space-y-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <SuggestionPill suggestion={entry.aiAnalysis.suggestion} confidence={entry.aiAnalysis.confidence} />
              </div>
              <p className="text-grafite dark:text-gray-200">{entry.aiAnalysis.summary}</p>
              {entry.aiAnalysis.factorsFor?.length ? (
                <div>
                  <p className="text-[11px] font-bold uppercase tracking-wide text-emerald-600 dark:text-emerald-300">A favor</p>
                  <ul className="mt-1 space-y-0.5 text-xs text-gray-600 dark:text-gray-300">
                    {entry.aiAnalysis.factorsFor.map((f, i) => <li key={i} className="flex gap-1.5"><span className="text-emerald-500">+</span>{f}</li>)}
                  </ul>
                </div>
              ) : null}
              {entry.aiAnalysis.factorsAgainst?.length ? (
                <div>
                  <p className="text-[11px] font-bold uppercase tracking-wide text-amber-600 dark:text-amber-300">Contra / incertezas</p>
                  <ul className="mt-1 space-y-0.5 text-xs text-gray-600 dark:text-gray-300">
                    {entry.aiAnalysis.factorsAgainst.map((f, i) => <li key={i} className="flex gap-1.5"><span className="text-amber-500">−</span>{f}</li>)}
                  </ul>
                </div>
              ) : null}
              {entry.aiAnalysis.recommendation ? (
                <p className="rounded-lg bg-white px-3 py-2 text-xs text-grafite dark:bg-background-dark dark:text-gray-200">
                  <span className="font-bold">Recomendação: </span>{entry.aiAnalysis.recommendation}
                </p>
              ) : null}
              {entry.aiAnalyzedAt ? <p className="text-[11px] text-gray-400">Gerado em {formatDateTime(entry.aiAnalyzedAt)} · a IA é apoio, a decisão é do analista</p> : null}
            </div>
          ) : (
            <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">Gere uma justificativa em linguagem natural com base nos dados e no score. A IA é apoio — a decisão é do analista.</p>
          )}
        </div>

        <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Partes do título</p>
          <div className="mt-3 space-y-3">
            <div>
              <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#612035" }}>
                <span className="h-2.5 w-2.5 rounded-full" style={{ background: "#612035" }} />
                Cedente {entry.clientCode ? <span className="font-normal text-gray-400">· cód. {entry.clientCode}</span> : null}
              </p>
              {entry.clientName ? (
                <>
                  {entry.clientDocument ? (
                    <Link
                      href={`/clients/${entry.clientDocument.replace(/\D/g, "")}`}
                      className="group mt-0.5 inline-flex items-center gap-1 text-sm font-bold text-primary transition-colors hover:underline dark:text-secondary"
                      title="Abrir ficha da empresa"
                    >
                      {entry.clientName}
                      <span className="material-icons-outlined text-[15px] opacity-60 transition-opacity group-hover:opacity-100">open_in_new</span>
                    </Link>
                  ) : (
                    <p className="mt-0.5 text-sm font-bold text-grafite dark:text-white">{entry.clientName}</p>
                  )}
                  {entry.clientDocument ? <p className="text-xs text-gray-500">{formatCnpj(entry.clientDocument)}</p> : null}
                  <p className="text-sm text-grafite dark:text-white">{entry.clientAddress ?? clean(entry.clientCity)}</p>
                </>
              ) : (
                <p className="mt-0.5 text-sm text-gray-400">
                  Sem cadastro vinculado (município: {clean(entry.clientCity)})
                </p>
              )}
            </div>
            <div>
              <div className="flex items-center justify-between gap-2">
                <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#D1732C" }}>
                  <span className="h-2.5 w-2.5 rounded-full" style={{ background: "#D1732C" }} />
                  Agência
                </p>
                <button
                  type="button"
                  onClick={onEnrichAgency}
                  disabled={enriching}
                  className="inline-flex h-7 items-center gap-1 rounded-lg border border-border-light px-2.5 text-[11px] font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                >
                  <span className="material-icons-outlined text-[14px]">{enriching ? "hourglass_empty" : "travel_explore"}</span>
                  {entry.agencyAddressResolved ? "Atualizar (Bacen)" : "Buscar no Bacen"}
                </button>
              </div>
              <p className="mt-0.5 text-sm text-grafite dark:text-white">
                {entry.agencyAddressResolved ?? <span className="text-gray-400">Agência não localizada no cadastro Bacen para este banco/código</span>}
              </p>
            </div>
            <div>
              <div className="flex items-center justify-between gap-2">
                <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color: "#2956E0" }}>
                  <span className="h-2.5 w-2.5 rounded-full" style={{ background: "#2956E0" }} />
                  Sacado
                </p>
                {entry.payerDocument && !entry.payerAddress ? (
                  <button
                    type="button"
                    onClick={onEnrichPayerCnpj}
                    disabled={enrichingPayerCnpj}
                    className="inline-flex h-7 items-center gap-1 rounded-lg border border-border-light px-2.5 text-[11px] font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    title="Consultar endereço do sacado pelo CNPJ (CNPJ Já) e recalcular as distâncias"
                  >
                    <span className="material-icons-outlined text-[14px]">{enrichingPayerCnpj ? "hourglass_empty" : "person_search"}</span>
                    Consultar CNPJ
                  </button>
                ) : null}
              </div>
              {entry.payerName ? (
                // Só vira link quando o sacado está cadastrado (payerAddress resolvido do company_details).
                // Sem cadastro, a ficha /clients/{cnpj} abriria em branco — então fica texto até consultar o CNPJ.
                entry.payerDocument && entry.payerAddress ? (
                  <Link
                    href={`/clients/${entry.payerDocument.replace(/\D/g, "")}`}
                    className="group mt-0.5 inline-flex items-center gap-1 text-sm font-bold text-primary transition-colors hover:underline dark:text-secondary"
                    title="Abrir ficha do sacado"
                  >
                    {entry.payerName}
                    <span className="material-icons-outlined text-[15px] opacity-60 transition-opacity group-hover:opacity-100">open_in_new</span>
                  </Link>
                ) : (
                  <p className="mt-0.5 text-sm font-bold text-grafite dark:text-white">{entry.payerName}</p>
                )
              ) : null}
              {entry.payerDocument ? <p className="text-xs text-gray-500">{formatCnpj(entry.payerDocument)}</p> : null}
              <p className="text-sm text-grafite dark:text-white">
                {entry.payerAddress ?? (
                  <>
                    {clean(entry.payerCity)}
                    <span className="ml-1 text-xs text-gray-400">(sem CNPJ na base — endereço por município)</span>
                  </>
                )}
              </p>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-border-light bg-white p-4 shadow-sm dark:border-border-dark dark:bg-background-dark">
          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Dados completos do lançamento</p>
          <div className="mt-3 grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3">
            <Field label="Título" value={entry.titleNumber} />
            <Field label="ID externo" value={entry.externalId} />
            <Field label="Cód. cedente" value={entry.clientCode} />
            <Field label="Vencimento" value={entry.dueDate} />
            <Field label="Valor título" value={entry.titleValue} />
            <Field label="Valor pago" value={entry.paidValue} />
            <Field label="Sacado" value={entry.payerName} />
            <Field label="Documento sacado" value={entry.payerDocument} />
            <Field label="Seção" value={entry.section} />
            <Field label="Banco/agência" value={entry.bankAgency} />
            <Field label="Cód. banco" value={entry.bankCode} />
            <Field label="Cód. agência" value={entry.agencyCode} />
            <Field label="Instituição" value={entry.bankName ?? entry.bacenInstitutionName} />
            <Field label="Tipo Bacen" value={entry.bacenInstitutionType} />
            <Field label="Categoria" value={categoryLabel(entry.institutionCategory)} />
            <Field label="Ocorrência" value={entry.occurrence} />
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <CategoryPill category={entry.institutionCategory} />
            <ReliabilityPill reliability={entry.geographicReliability} />
          </div>
          {entry.occurrenceComplement ? (
            <div className="mt-3">
              <Field label="Complemento da ocorrência" value={entry.occurrenceComplement} />
            </div>
          ) : null}
          {entry.geographicReliabilityReason ? (
            <div className="mt-3">
              <p className="text-[11px] font-bold uppercase tracking-wide text-gray-400">Motivo da confiabilidade</p>
              <p className="mt-0.5 text-sm text-gray-600 dark:text-gray-300">{entry.geographicReliabilityReason}</p>
            </div>
          ) : null}
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-4 text-xs">
          <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#612035" }} />Cedente</span>
          <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#D1732C" }} />Agência</span>
          <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#2956E0" }} />Sacado</span>
          {showBranches ? (
            <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#1F9D55" }} />Filiais do sacado</span>
          ) : null}
          <button
            type="button"
            onClick={() => setShowBranches((v) => !v)}
            disabled={!entry.payerDocument}
            className="ml-auto rounded-md border border-border-light px-2.5 py-1 text-[11px] font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
          >
            {branchesQuery.isFetching
              ? "Carregando filiais…"
              : showBranches
                ? `Ocultar filiais${branchPoints.length ? ` (${branchPoints.length})` : ""}`
                : "Mostrar filiais do sacado"}
          </button>
        </div>
        {showBranches && branchesQuery.isError ? (
          <p className="text-[11px] text-red-500">{(branchesQuery.error as Error)?.message ?? "Erro ao carregar filiais"}</p>
        ) : null}
        <div className="min-h-[300px] flex-1 overflow-hidden rounded-xl border border-border-light shadow-sm dark:border-border-dark">
          <PaymentPlaceMap points={points} />
        </div>
      </div>
      </div>
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: number; tone?: "amber" | "green" | "red" }) {
  const toneClass =
    tone === "green"
      ? "text-emerald-600 dark:text-emerald-300"
      : tone === "red"
        ? "text-red-600 dark:text-red-300"
      : tone === "amber"
        ? "text-amber-600 dark:text-amber-300"
        : "text-grafite dark:text-white";

  return (
    <div className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
      <p className="text-xs font-bold uppercase tracking-wide text-gray-400">{label}</p>
      <p className={`mt-2 text-2xl font-bold ${toneClass}`}>{value}</p>
    </div>
  );
}
