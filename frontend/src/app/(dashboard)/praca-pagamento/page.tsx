"use client";

import { ChangeEvent, Fragment, useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { toast } from "sonner";
import Link from "next/link";
import dynamic from "next/dynamic";
import {
  CompanyNotInPortfolioError,
  useArchivePaymentPlaceBatch,
  useBulkDecidePaymentPlace,
  useCompanyBranches,
  useLinkCedenteCnpj,
  useDeletePaymentPlaceBatch,
  useDecidePaymentPlaceEntry,
  useEnrichAgencyBacen,
  useEnrichPayerCnpj,
  useImportPaymentPlacePdf,
  usePaymentPlaceBatchDetails,
  usePaymentPlaceBatches,
  usePaymentPlaceIndicatorsAll,
  usePartyNote,
  useSavePartyNote,
} from "../../../hooks/usePaymentPlace";
import { useReopenPaymentPlaceEntry } from "../../../hooks/usePaymentPlaceCompany";
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

// Chave de dia local (YYYY-MM-DD) a partir de uma data ISO.
function toYmd(value?: string | null) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
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

function DistanceChip({ label, value, highlight, onClick }: { label: string; value?: number | null; highlight?: boolean; onClick?: () => void }) {
  const km = formatKm(value);
  if (!km) return null;
  const base = `inline-flex flex-col rounded-md px-2.5 py-1 leading-tight ${
    highlight
      ? "bg-primary/10 text-primary dark:bg-secondary/15 dark:text-secondary"
      : "bg-gray-100 text-gray-600 dark:bg-white/10 dark:text-gray-300"
  }`;
  const inner = (
    <>
      <span className="text-[11px] font-medium opacity-70">{label}</span>
      <span className="text-sm font-bold">{km}</span>
    </>
  );
  if (onClick) {
    return (
      <button type="button" onClick={onClick} title={`Ver mapa · ${label}`} className={`${base} text-left transition hover:ring-2 hover:ring-primary/40 dark:hover:ring-secondary/40`}>
        {inner}
      </button>
    );
  }
  return <span className={base}>{inner}</span>;
}

function DecisionPill({ decision }: { decision?: string | null }) {
  if (!decision) {
    return <span className="text-xs font-medium text-gray-400">Sem decisão</span>;
  }
  if (decision === "INCONCLUSIVO") {
    return (
      <span className="inline-flex h-7 items-center rounded-full bg-amber-100 px-3 text-xs font-bold text-amber-800 dark:bg-amber-500/15 dark:text-amber-300">
        Inconclusivo
      </span>
    );
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
  const [showBatchModal, setShowBatchModal] = useState(false);
  const [sectionFilter, setSectionFilter] = useState("");
  const [decisionFilter, setDecisionFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [reliabilityFilter, setReliabilityFilter] = useState("");
  const [search, setSearch] = useState("");
  const [notesByEntry, setNotesByEntry] = useState<Record<string, string>>({});
  const [expandedEntryId, setExpandedEntryId] = useState<string | null>(null);
  const [focusedEntryId, setFocusedEntryId] = useState<string | null>(null);
  const [sortByConfidence, setSortByConfidence] = useState(true);
  const [selectMode, setSelectMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [importMenuOpen, setImportMenuOpen] = useState(false);
  const [customImportOpen, setCustomImportOpen] = useState(false);
  const [customMonth, setCustomMonth] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [customDay, setCustomDay] = useState<string>(() => toYmd(new Date().toISOString()));
  const [customFiles, setCustomFiles] = useState<File[]>([]);
  const modalBodyRef = useRef<HTMLDivElement>(null);
  const [analysisTab, setAnalysisTab] = useState<"PENDENTES" | "DECIDIDOS" | "TODOS">("PENDENTES");
  const [listCollapsed, setListCollapsed] = useState(false);
  const [calendarMonth, setCalendarMonth] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [copyMenu, setCopyMenu] = useState<{ x: number; y: number; entry: PaymentPlaceEntry } | null>(null);
  const [copyMenuVisible, setCopyMenuVisible] = useState(false);
  const [mapEntry, setMapEntry] = useState<{ entry: PaymentPlaceEntry; pair: "ALL" | "CED_AG" | "SAC_AG" | "CED_SAC" } | null>(null);
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longPressStart = useRef<{ x: number; y: number } | null>(null);

  const batchesQuery = usePaymentPlaceBatches();
  const importMutation = useImportPaymentPlacePdf();
  const deleteBatchMutation = useDeletePaymentPlaceBatch();
  const archiveBatchMutation = useArchivePaymentPlaceBatch();

  const allBatches = useMemo(() => batchesQuery.data ?? [], [batchesQuery.data]);
  const batches = useMemo(() => allBatches.filter((item) => item.status !== "ARQUIVADO"), [allBatches]);
  const todayBatch = useMemo(() => batches.find((item) => isToday(item.importedAt)), [batches]);
  // Todos os lotes ativos são exibidos juntos (sem seleção). Mescla os lançamentos.
  const activeBatchIds = useMemo(() => batches.map((b) => b.id), [batches]);
  const batchDetails = usePaymentPlaceBatchDetails(activeBatchIds);
  const indicatorsQuery = usePaymentPlaceIndicatorsAll(activeBatchIds);
  const decideMutation = useDecidePaymentPlaceEntry();
  const bulkDecideMutation = useBulkDecidePaymentPlace();
  const enrichAgencyMutation = useEnrichAgencyBacen();
  const enrichPayerCnpjMutation = useEnrichPayerCnpj();
  const reopenMutation = useReopenPaymentPlaceEntry();
  const fileNameByBatch = useMemo(() => new Map(batches.map((b) => [b.id, b.fileName])), [batches]);
  const entries = useMemo(() => batchDetails.details.flatMap((d) => d.entries), [batchDetails.details]);
  const batchMeta = useMemo(
    () => ({
      auditEntries: batches.reduce((acc, b) => acc + (b.auditEntries ?? 0), 0),
      unlocatedAgencyEntries: batches.reduce((acc, b) => acc + (b.unlocatedAgencyEntries ?? 0), 0),
    }),
    [batches],
  );
  // Calendário de lotes: agrupa todos (ativos + arquivados) por dia de importação.
  const batchesByDay = useMemo(() => {
    const m = new Map<string, typeof allBatches>();
    for (const b of allBatches) {
      const key = toYmd(b.importedAt);
      if (!key) continue;
      const arr = m.get(key);
      if (arr) arr.push(b);
      else m.set(key, [b]);
    }
    return m;
  }, [allBatches]);

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
        (!categoryFilter || entry.institutionCategory === categoryFilter) &&
        (!reliabilityFilter || entry.geographicReliability === reliabilityFilter) &&
        (!decisionFilter || (decisionFilter === "SEM_DECISAO" ? !entry.analystDecision : entry.analystDecision === decisionFilter))
      );
    });
  }, [analysisTab, categoryFilter, decisionFilter, entries, reliabilityFilter, search, sectionFilter]);

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

  const handleFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    if (files.length === 0) return;

    // Importa sequencialmente: cada PDF gera seu próprio lote/card.
    let lastBatchId: string | null = null;
    let ok = 0;
    let lastError = "";
    for (let i = 0; i < files.length; i++) {
      if (files.length > 1) {
        toast.loading(`Importando arquivo ${i + 1} de ${files.length}...`, { id: "payment-place-import" });
      }
      try {
        const data = await importMutation.mutateAsync(files[i]);
        lastBatchId = data.batch.id;
        ok++;
      } catch (e) {
        lastError = e instanceof Error ? e.message : "Falha ao importar";
      }
    }
    if (files.length > 1) {
      if (ok === 0) toast.error(`Falha ao importar: ${lastError}`, { id: "payment-place-import", duration: 6000 });
      else toast.success(`${ok} de ${files.length} arquivos importados`, { id: "payment-place-import", duration: 5000 });
    }
    void lastBatchId; // todos os lotes ativos já aparecem juntos
  };

  // Importação personalizada: relatórios de dias anteriores, datados no dia escolhido.
  const openCustomImport = () => {
    const now = new Date();
    setCustomMonth(new Date(now.getFullYear(), now.getMonth(), 1));
    setCustomDay(toYmd(now.toISOString()));
    setCustomFiles([]);
    setImportMenuOpen(false);
    setCustomImportOpen(true);
  };

  const runCustomImport = async () => {
    if (customFiles.length === 0 || !customDay) return;
    let ok = 0;
    let lastError = "";
    for (let i = 0; i < customFiles.length; i++) {
      toast.loading(`Importando ${i + 1} de ${customFiles.length}...`, { id: "payment-place-import" });
      try {
        await importMutation.mutateAsync({ file: customFiles[i], referenceDate: customDay });
        ok++;
      } catch (e) {
        lastError = e instanceof Error ? e.message : "Falha ao importar";
      }
    }
    const dataLabel = new Date(`${customDay}T12:00:00`).toLocaleDateString("pt-BR");
    if (ok === 0) {
      toast.error(`Falha ao importar: ${lastError}`, { id: "payment-place-import", duration: 6000 });
      return; // mantém o modal aberto para nova tentativa
    }
    toast.success(`${ok} de ${customFiles.length} importado(s) em ${dataLabel}`, {
      id: "payment-place-import",
      duration: 5000,
    });
    setCustomImportOpen(false);
    setCustomFiles([]);
  };

  const deleteBatch = (batchId: string) => {
    const batchToDelete = batches.find((item) => item.id === batchId);
    const confirmed = window.confirm(`Apagar o lote "${batchToDelete?.fileName ?? "selecionado"}"?`);
    if (!confirmed) return;
    deleteBatchMutation.mutate(batchId);
  };

  const decide = (entry: PaymentPlaceEntry, decision: "SACADO" | "CEDENTE" | "INCONCLUSIVO") => {
    decideMutation.mutate({
      entryId: entry.id,
      decision,
      notes: notesByEntry[entry.id],
    });
  };

  // Reabre uma análise já decidida: volta para PENDENTE e remove o registro do lado da empresa.
  const reopen = (entry: PaymentPlaceEntry) => {
    if (!window.confirm("Reabrir esta análise? Ela volta para Pendentes e sai das informações da empresa.")) return;
    reopenMutation.mutate(entry.id, {
      onSuccess: () => {
        if (expandedEntryId === entry.id) setExpandedEntryId(null);
      },
    });
  };

  // Abre o calendário de lotes já focado no dia de hoje.
  const openBatchModal = () => {
    const now = new Date();
    setCalendarMonth(new Date(now.getFullYear(), now.getMonth(), 1));
    setSelectedDay(toYmd(now.toISOString()));
    setShowBatchModal(true);
  };

  // Menu de cópia rápida: long-press (~550ms) ou clique-direito numa linha.
  const clearLongPress = () => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    longPressStart.current = null;
  };

  const handleRowPointerDown = (entry: PaymentPlaceEntry, ev: React.PointerEvent) => {
    if (ev.button !== 0) return; // só botão principal / toque
    const { clientX, clientY } = ev;
    longPressStart.current = { x: clientX, y: clientY };
    longPressTimer.current = setTimeout(() => {
      setCopyMenu({ x: clientX, y: clientY, entry });
      longPressStart.current = null;
    }, 550);
  };

  const handleRowPointerMove = (ev: React.PointerEvent) => {
    if (!longPressStart.current) return;
    const dx = Math.abs(ev.clientX - longPressStart.current.x);
    const dy = Math.abs(ev.clientY - longPressStart.current.y);
    if (dx > 10 || dy > 10) clearLongPress(); // rolou/arrastou → cancela
  };

  const closeCopyMenu = () => {
    setCopyMenuVisible(false);
    setTimeout(() => setCopyMenu(null), 160); // espera o fade-out
  };

  const copyField = async (label: string, value?: string | number | null) => {
    const text = value == null ? "" : String(value).trim();
    if (!text) {
      toast.error(`Sem ${label}`);
      closeCopyMenu();
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${label} copiado`, { duration: 1500 });
    } catch {
      toast.error("Falha ao copiar");
    }
    closeCopyMenu();
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

  // Seleção em lote (modo selecionar).
  const toggleSelect = (id: string) => {
    setSelectedIds((cur) => {
      const next = new Set(cur);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };
  const clearSelection = () => setSelectedIds(new Set());
  const exitSelectMode = () => {
    setSelectMode(false);
    clearSelection();
  };
  const selectAllVisible = () => setSelectedIds(new Set(sortedEntries.map((e) => e.id)));
  const bulkDecideSelected = (decision: "SACADO" | "CEDENTE" | "INCONCLUSIVO") => {
    const decisions = Array.from(selectedIds).map((entryId) => ({ entryId, decision }));
    if (!decisions.length) return;
    bulkDecideMutation.mutate(decisions, { onSuccess: () => exitSelectMode() });
  };

  // Esc fecha o modal de detalhes, o mapa ou o menu de cópia.
  useEffect(() => {
    if (!expandedEntryId && !copyMenu && !mapEntry) return;
    const handler = (ev: KeyboardEvent) => {
      if (ev.key === "Escape") {
        if (copyMenu) closeCopyMenu();
        else if (mapEntry) setMapEntry(null);
        else setExpandedEntryId(null);
        ev.preventDefault();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [expandedEntryId, copyMenu, mapEntry]);

  // Aciona o fade-in no frame seguinte ao montar o menu de cópia.
  useEffect(() => {
    if (!copyMenu) return;
    const id = requestAnimationFrame(() => setCopyMenuVisible(true));
    return () => cancelAnimationFrame(id);
  }, [copyMenu]);


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
            onClick={openBatchModal}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-xl border border-border-light bg-white px-4 text-sm font-bold text-grafite shadow-sm transition-colors hover:bg-gray-50 dark:border-border-dark dark:bg-surface-dark dark:text-white dark:hover:bg-white/5"
          >
            <span className="material-icons-outlined text-[20px]">inventory_2</span>
            Lotes
          </button>
          <div className="relative inline-flex">
            <label className="inline-flex h-11 cursor-pointer items-center justify-center gap-2 rounded-l-xl bg-primary px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-primary/90">
              <span className="material-icons-outlined text-[20px]">upload_file</span>
              Importar PDF
              <input type="file" accept="application/pdf,.pdf" multiple className="hidden" onChange={handleFile} disabled={importMutation.isPending} />
            </label>
            <button
              type="button"
              onClick={() => setImportMenuOpen((v) => !v)}
              className="inline-flex h-11 w-9 items-center justify-center rounded-r-xl border-l border-white/25 bg-primary text-white shadow-sm transition-colors hover:bg-primary/90"
              title="Mais opções de importação"
              aria-label="Mais opções de importação"
            >
              <span className={`material-icons-outlined text-[20px] transition-transform ${importMenuOpen ? "rotate-180" : ""}`}>expand_more</span>
            </button>
            {importMenuOpen ? (
              <>
                <div className="fixed inset-0 z-10" onClick={() => setImportMenuOpen(false)} />
                <div className="absolute right-0 top-full z-20 mt-1 w-64 overflow-hidden rounded-xl border border-border-light bg-surface-light p-1 shadow-lg dark:border-border-dark dark:bg-surface-dark">
                  <button
                    type="button"
                    onClick={openCustomImport}
                    className="flex w-full items-start gap-2 rounded-lg px-3 py-2 text-left transition-colors hover:bg-gray-50 dark:hover:bg-white/5"
                  >
                    <span className="material-icons-outlined mt-0.5 text-[18px] text-primary dark:text-secondary">event</span>
                    <span>
                      <span className="block text-sm font-bold text-grafite dark:text-white">Importar relatórios anteriores</span>
                      <span className="block text-[11px] text-gray-500 dark:text-gray-400">Escolha o arquivo e o dia do lote</span>
                    </span>
                  </button>
                </div>
              </>
            ) : null}
          </div>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-6">
        <Metric label="Lançamentos" value={entries.length} />
        <Metric label="Auditoria eletrônica" value={batchMeta.auditEntries} />
        <Metric label="Agências não localizadas" value={batchMeta.unlocatedAgencyEntries} />
        <Metric label="Digitais/cooperativas" value={counters.digitalOrCooperative} />
        <Metric label="Baixa confiança" value={counters.lowReliability} tone="red" />
        <Metric label="Pendentes" value={counters.pending} tone="amber" />
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-grafite dark:text-white">Indicadores {batches.length > 1 ? "dos lotes" : "do lote"}</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Leitura rápida da qualidade da praça e da aderência entre sugestão automática e decisão humana.
            </p>
          </div>
          {batches.length > 1 ? (
            <p className="hidden max-w-[480px] truncate text-xs text-gray-400 lg:block">{batches.length} lotes ativos</p>
          ) : null}
        </div>

        {indicatorsQuery.isLoading && batches.length > 0 ? (
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
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(240px,1fr)_180px_170px_170px_160px_auto]">
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
                  <option value="INCONCLUSIVO">Inconclusivo</option>
                </select>
              </label>
              <button
                type="button"
                onClick={() => {
                  setSearch("");
                  setSectionFilter("");
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
                  <h2 className="truncate text-sm font-bold text-grafite dark:text-white">
                    {batches.length === 0 ? "Nenhum lote ativo" : batches.length === 1 ? batches[0].fileName : `Todos os lotes (${batches.length})`}
                  </h2>
                  <p className="text-xs text-gray-500 dark:text-gray-400">
                    {filteredEntries.length} de {entries.length} exibidos · Sacados {counters.payer} · Cedentes {counters.assignor}
                  </p>
                  {batches.length > 0 ? (
                    <p className="mt-0.5 inline-flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                      <span className="material-icons-outlined text-[14px]">schedule</span>
                      {batches.length === 1
                        ? `Importado em ${formatDateTime(batches[0].importedAt)}${batches[0].importedByName ? ` por ${batches[0].importedByName}` : ""}`
                        : `${batches.length} lotes ativos exibidos juntos`}
                    </p>
                  ) : null}
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => (selectMode ? exitSelectMode() : setSelectMode(true))}
                    className={`inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border px-3 text-xs font-bold transition-colors ${
                      selectMode
                        ? "border-primary bg-primary text-white dark:border-secondary dark:bg-secondary"
                        : "border-border-light text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    }`}
                    title="Selecionar vários lançamentos para decidir em lote"
                  >
                    <span className="material-icons-outlined text-[16px]">{selectMode ? "close" : "checklist"}</span>
                    {selectMode ? "Cancelar" : "Selecionar"}
                  </button>
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
                  {batches.length > 0 ? (
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
                        onClick={openBatchModal}
                        className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                        title="Gerenciar lotes (arquivar/apagar)"
                      >
                        <span className="material-icons-outlined text-[16px]">inventory_2</span>
                        Lotes
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

              {selectMode ? (
                <div className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-primary/5 p-2 dark:border-secondary/30 dark:bg-secondary/10">
                  <span className="px-1 text-sm font-bold text-primary dark:text-secondary">
                    {selectedIds.size} selecionado{selectedIds.size === 1 ? "" : "s"}
                  </span>
                  <button
                    type="button"
                    onClick={selectAllVisible}
                    className="inline-flex h-8 items-center rounded-lg border border-border-light bg-white px-2.5 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:bg-surface-dark dark:text-gray-300 dark:hover:bg-white/5"
                  >
                    Selecionar todos ({sortedEntries.length})
                  </button>
                  {selectedIds.size > 0 ? (
                    <button
                      type="button"
                      onClick={clearSelection}
                      className="inline-flex h-8 items-center rounded-lg border border-border-light bg-white px-2.5 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:bg-surface-dark dark:text-gray-300 dark:hover:bg-white/5"
                    >
                      Limpar
                    </button>
                  ) : null}
                  <span className="ml-auto flex items-center gap-2">
                    <span className="hidden text-xs font-medium text-gray-500 sm:inline">Definir selecionados como:</span>
                    <button
                      type="button"
                      onClick={() => bulkDecideSelected("SACADO")}
                      disabled={selectedIds.size === 0 || bulkDecideMutation.isPending}
                      className="inline-flex h-8 items-center rounded-lg bg-blue-600 px-3 text-xs font-bold text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Sacado
                    </button>
                    <button
                      type="button"
                      onClick={() => bulkDecideSelected("CEDENTE")}
                      disabled={selectedIds.size === 0 || bulkDecideMutation.isPending}
                      className="inline-flex h-8 items-center rounded-lg bg-slate-700 px-3 text-xs font-bold text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Cedente
                    </button>
                    <button
                      type="button"
                      onClick={() => bulkDecideSelected("INCONCLUSIVO")}
                      disabled={selectedIds.size === 0 || bulkDecideMutation.isPending}
                      className="inline-flex h-8 items-center rounded-lg bg-amber-500 px-3 text-xs font-bold text-white transition-colors hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      Inconclusivo
                    </button>
                  </span>
                </div>
              ) : null}
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
            ) : batchDetails.isLoading ? (
              <div className="p-6 text-sm text-gray-500">Carregando lançamentos...</div>
            ) : batches.length === 0 ? (
              <div className="p-6 text-sm text-gray-500">Importe um lote para iniciar a análise.</div>
            ) : sortedEntries.length === 0 ? (
              <div className="p-6 text-sm text-gray-500">Nenhum lançamento encontrado para os filtros atuais.</div>
            ) : (
              <div className="divide-y divide-border-light dark:divide-border-dark">
                {sortedEntries.map((entry) => {
                  const focused = entry.id === focusedEntryId;
                  const selected = selectedIds.has(entry.id);
                  const suggested = entry.automaticSuggestion === "PROVAVEL_SACADO" ? "SACADO" : entry.automaticSuggestion === "PROVAVEL_CEDENTE" ? "CEDENTE" : null;
                  return (
                    <Fragment key={entry.id}>
                      <div
                        id={`pp-row-${entry.id}`}
                        onClick={() => (selectMode ? toggleSelect(entry.id) : setFocusedEntryId(entry.id))}
                        onDoubleClick={() => { if (selectMode) return; setFocusedEntryId(entry.id); setExpandedEntryId(entry.id); }}
                        className={`flex flex-col gap-2 px-4 transition-colors lg:flex-row lg:items-center ${selectMode ? "cursor-pointer py-1.5" : "py-2.5"} ${
                          selected
                            ? "bg-primary/10 ring-1 ring-inset ring-primary/50 dark:bg-secondary/15 dark:ring-secondary/50"
                            : focused && !selectMode
                              ? "bg-primary/5 ring-1 ring-inset ring-primary/40 dark:bg-secondary/10 dark:ring-secondary/40"
                              : entry.reopenedAt
                                ? "bg-amber-50/70 ring-1 ring-inset ring-amber-300 dark:bg-amber-500/10 dark:ring-amber-500/40"
                                : "hover:bg-gray-50/70 dark:hover:bg-white/[0.03]"
                        }`}
                      >
                        {selectMode ? (
                          <span className={`flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-md border transition-colors ${selected ? "border-primary bg-primary text-white dark:border-secondary dark:bg-secondary" : "border-gray-300 dark:border-gray-600"}`}>
                            {selected ? <span className="material-icons-outlined text-[16px]">check</span> : null}
                          </span>
                        ) : null}
                        {/* Área de dados — long-press / clique-direito copia */}
                        <div
                          onContextMenu={(e) => { e.preventDefault(); setCopyMenu({ x: e.clientX, y: e.clientY, entry }); }}
                          onPointerDown={(e) => handleRowPointerDown(entry, e)}
                          onPointerMove={handleRowPointerMove}
                          onPointerUp={clearLongPress}
                          onPointerLeave={clearLongPress}
                          onPointerCancel={clearLongPress}
                          className="flex flex-col gap-2 lg:flex-row lg:items-center lg:cursor-context-menu"
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
                        </div>

                        {selectMode ? (
                          <div className="flex min-w-0 flex-1 items-center justify-end">
                            <SuggestionPill suggestion={entry.automaticSuggestion} confidence={entry.automaticConfidence} />
                          </div>
                        ) : null}

                        {/* Sugestão + distâncias — cada distância abre o mapa do par */}
                        {!selectMode ? (
                        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                          <SuggestionPill suggestion={entry.automaticSuggestion} confidence={entry.automaticConfidence} />
                          <DistanceChip label="Cedente ↔ Agência" value={entry.distanceClientAgencyKm} onClick={() => { setFocusedEntryId(entry.id); setMapEntry({ entry, pair: "CED_AG" }); }} />
                          <DistanceChip label="Sacado ↔ Agência" value={entry.distanceAgencyPayerKm} onClick={() => { setFocusedEntryId(entry.id); setMapEntry({ entry, pair: "SAC_AG" }); }} />
                          <DistanceChip label="Cedente ↔ Sacado" value={entry.distanceClientPayerKm} highlight onClick={() => { setFocusedEntryId(entry.id); setMapEntry({ entry, pair: "CED_SAC" }); }} />
                        </div>
                        ) : null}

                        {/* Decisão + ações */}
                        {!selectMode ? (
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
                            onClick={(e) => { e.stopPropagation(); decide(entry, "INCONCLUSIVO"); }}
                            disabled={decideMutation.isPending}
                            title="Marcar como inconclusivo (I)"
                            className={`inline-flex h-8 items-center justify-center rounded-lg px-3 text-xs font-bold transition-colors disabled:opacity-60 ${
                              entry.analystDecision === "INCONCLUSIVO"
                                ? "bg-amber-500 text-white"
                                : "border border-border-light text-gray-600 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            }`}
                          >
                            Inconclusivo
                          </button>
                          {entry.analystDecision ? (
                            <button
                              type="button"
                              onClick={(e) => { e.stopPropagation(); reopen(entry); }}
                              disabled={reopenMutation.isPending}
                              title="Reabrir análise (volta para Pendentes e sai da empresa)"
                              aria-label="Reabrir análise"
                              className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-amber-200 text-amber-600 transition-colors hover:bg-amber-50 disabled:opacity-60 dark:border-amber-500/30 dark:text-amber-300 dark:hover:bg-amber-500/10"
                            >
                              <span className="material-icons-outlined text-[16px]">undo</span>
                            </button>
                          ) : null}
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
                        ) : null}
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

            <div className="max-h-[560px] overflow-y-auto p-4">
              {/* Navegação de mês/ano */}
              <div className="mb-3 flex items-center justify-between gap-1">
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setCalendarMonth((d) => new Date(d.getFullYear() - 1, d.getMonth(), 1))}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    title="Ano anterior"
                    aria-label="Ano anterior"
                  >
                    <span className="material-icons-outlined text-[18px]">keyboard_double_arrow_left</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setCalendarMonth((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1))}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    title="Mês anterior"
                    aria-label="Mês anterior"
                  >
                    <span className="material-icons-outlined text-[18px]">chevron_left</span>
                  </button>
                </div>
                <p className="text-sm font-bold capitalize text-grafite dark:text-white">
                  {`${calendarMonth.toLocaleDateString("pt-BR", { month: "long" })}/${String(calendarMonth.getFullYear()).slice(-2)}`}
                </p>
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setCalendarMonth((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1))}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    title="Próximo mês"
                    aria-label="Próximo mês"
                  >
                    <span className="material-icons-outlined text-[18px]">chevron_right</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setCalendarMonth((d) => new Date(d.getFullYear() + 1, d.getMonth(), 1))}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                    title="Próximo ano"
                    aria-label="Próximo ano"
                  >
                    <span className="material-icons-outlined text-[18px]">keyboard_double_arrow_right</span>
                  </button>
                </div>
              </div>

              {/* Cabeçalho dos dias da semana */}
              <div className="grid grid-cols-7 gap-1 text-center text-[11px] font-bold uppercase text-gray-400">
                {["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"].map((d, i) => (
                  <span key={i}>{d}</span>
                ))}
              </div>

              {/* Grade do mês */}
              {(() => {
                const year = calendarMonth.getFullYear();
                const month = calendarMonth.getMonth();
                const firstWeekday = new Date(year, month, 1).getDay();
                const daysInMonth = new Date(year, month + 1, 0).getDate();
                const todayYmd = toYmd(new Date().toISOString());
                const cells: (number | null)[] = [];
                for (let i = 0; i < firstWeekday; i++) cells.push(null);
                for (let d = 1; d <= daysInMonth; d++) cells.push(d);
                return (
                  <div className="mt-1 grid grid-cols-7 gap-1">
                    {cells.map((day, idx) => {
                      if (day === null) return <div key={`b-${idx}`} />;
                      const ymd = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
                      const dayBatches = batchesByDay.get(ymd) ?? [];
                      const count = dayBatches.length;
                      const isTodayCell = ymd === todayYmd;
                      const selected = ymd === selectedDay;
                      return (
                        <button
                          key={ymd}
                          type="button"
                          onClick={() => setSelectedDay(count > 0 ? (selected ? null : ymd) : ymd)}
                          className={`relative flex h-10 items-center justify-center rounded-lg text-sm transition-colors ${
                            selected
                              ? "bg-primary text-white dark:bg-secondary"
                              : count > 0
                                ? "bg-primary/5 font-bold text-grafite hover:bg-primary/10 dark:bg-secondary/10 dark:text-white"
                                : "text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-white/5"
                          } ${isTodayCell && !selected ? "ring-1 ring-primary/50 dark:ring-secondary/50" : ""}`}
                        >
                          {day}
                          {count > 0 ? (
                            <span className={`absolute right-0.5 top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full px-1 text-[11px] font-bold leading-none ${selected ? "bg-white text-primary dark:text-secondary" : "bg-red-500 text-white"}`}>
                              {count}
                            </span>
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                );
              })()}

              {/* Lotes do dia selecionado */}
              <div className="mt-4 border-t border-border-light pt-3 dark:border-border-dark">
                {!selectedDay ? (
                  <p className="px-1 py-2 text-xs text-gray-400">Selecione um dia para ver os lotes importados.</p>
                ) : (batchesByDay.get(selectedDay) ?? []).length === 0 ? (
                  <p className="px-1 py-2 text-xs text-gray-400">Nenhum lote importado em {new Date(`${selectedDay}T12:00:00`).toLocaleDateString("pt-BR")}.</p>
                ) : (
                  <div className="space-y-2">
                    <p className="px-1 text-xs font-bold text-gray-500 dark:text-gray-300">
                      {new Date(`${selectedDay}T12:00:00`).toLocaleDateString("pt-BR", { weekday: "long", day: "2-digit", month: "long", year: "numeric" })}
                    </p>
                    {(batchesByDay.get(selectedDay) ?? []).map((item) => {
                      const archived = item.status === "ARQUIVADO";
                      return (
                        <div
                          key={item.id}
                          className={`flex items-start gap-3 rounded-lg border p-3 transition-colors ${
                            archived
                              ? "border-border-light bg-gray-50/50 dark:border-border-dark dark:bg-white/[0.02]"
                              : "border-border-light bg-white hover:bg-gray-50 dark:border-border-dark dark:bg-background-dark dark:hover:bg-white/5"
                          }`}
                        >
                          <div className="min-w-0 flex-1">
                            <div className="flex items-start justify-between gap-3">
                              <p className="line-clamp-2 text-sm font-bold text-grafite dark:text-white">
                                {item.fileName}
                                {archived ? <span className="ml-1.5 rounded bg-gray-200 px-1.5 py-0.5 text-[10px] font-bold text-gray-600 dark:bg-white/10 dark:text-gray-300">arquivado</span> : null}
                              </p>
                              <span className="rounded-md bg-gray-100 px-2 py-1 text-xs font-bold text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                                {item.totalEntries}
                              </span>
                            </div>
                            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{formatDateTime(item.importedAt)}</p>
                            <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                              {item.auditEntries} auditoria / {item.unlocatedAgencyEntries} agências
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() => archiveBatchMutation.mutate({ batchId: item.id, archived: !archived })}
                            disabled={archiveBatchMutation.isPending}
                            className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 disabled:opacity-60 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                            title={archived ? "Restaurar lote" : "Arquivar lote"}
                          >
                            <span className="material-icons-outlined text-[18px]">{archived ? "unarchive" : "inventory_2"}</span>
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
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {customImportOpen ? (
        <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/30 px-4 py-16 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-xl border border-border-light bg-white shadow-2xl dark:border-border-dark dark:bg-surface-dark">
            <div className="flex items-center justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
              <div>
                <h2 className="text-sm font-bold text-grafite dark:text-white">Importar relatório anterior</h2>
                <p className="text-xs text-gray-500 dark:text-gray-400">Escolha o arquivo e o dia do lote.</p>
              </div>
              <button
                type="button"
                onClick={() => setCustomImportOpen(false)}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                aria-label="Fechar"
              >
                <span className="material-icons-outlined text-[18px]">close</span>
              </button>
            </div>

            <div className="space-y-4 p-4">
              {/* Arquivo */}
              <div>
                <p className="mb-1 text-xs font-bold uppercase tracking-wide text-gray-400">Arquivo(s) PDF</p>
                <label className="flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-dashed border-border-light px-3 py-4 text-sm text-gray-500 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-400 dark:hover:bg-white/5">
                  <span className="material-icons-outlined text-[20px]">upload_file</span>
                  {customFiles.length === 0 ? "Selecionar PDF" : `${customFiles.length} arquivo(s) selecionado(s)`}
                  <input
                    type="file"
                    accept="application/pdf,.pdf"
                    multiple
                    className="hidden"
                    onChange={(e) => setCustomFiles(Array.from(e.target.files ?? []))}
                  />
                </label>
                {customFiles.length > 0 ? (
                  <ul className="mt-2 space-y-1">
                    {customFiles.map((f, i) => (
                      <li key={i} className="truncate text-xs text-gray-500 dark:text-gray-400">• {f.name}</li>
                    ))}
                  </ul>
                ) : null}
              </div>

              {/* Calendário de data do lote */}
              <div>
                <p className="mb-1 text-xs font-bold uppercase tracking-wide text-gray-400">Dia do lote</p>
                <div className="rounded-lg border border-border-light p-2 dark:border-border-dark">
                  <div className="mb-2 flex items-center justify-between gap-1">
                    <div className="flex items-center gap-1">
                      <button type="button" onClick={() => setCustomMonth((d) => new Date(d.getFullYear() - 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Ano anterior"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_left</span></button>
                      <button type="button" onClick={() => setCustomMonth((d) => new Date(d.getFullYear(), d.getMonth() - 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Mês anterior"><span className="material-icons-outlined text-[16px]">chevron_left</span></button>
                    </div>
                    <p className="text-sm font-bold capitalize text-grafite dark:text-white">{`${customMonth.toLocaleDateString("pt-BR", { month: "long" })}/${String(customMonth.getFullYear()).slice(-2)}`}</p>
                    <div className="flex items-center gap-1">
                      <button type="button" onClick={() => setCustomMonth((d) => new Date(d.getFullYear(), d.getMonth() + 1, 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo mês"><span className="material-icons-outlined text-[16px]">chevron_right</span></button>
                      <button type="button" onClick={() => setCustomMonth((d) => new Date(d.getFullYear() + 1, d.getMonth(), 1))} className="inline-flex h-7 w-7 items-center justify-center rounded-lg border border-border-light text-gray-500 hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5" title="Próximo ano"><span className="material-icons-outlined text-[16px]">keyboard_double_arrow_right</span></button>
                    </div>
                  </div>
                  <div className="grid grid-cols-7 gap-1 text-center text-[10px] font-bold uppercase text-gray-400">
                    {["D", "S", "T", "Q", "Q", "S", "S"].map((d, i) => <span key={i}>{d}</span>)}
                  </div>
                  {(() => {
                    const year = customMonth.getFullYear();
                    const month = customMonth.getMonth();
                    const firstWeekday = new Date(year, month, 1).getDay();
                    const daysInMonth = new Date(year, month + 1, 0).getDate();
                    const todayYmd = toYmd(new Date().toISOString());
                    const cells: (number | null)[] = [];
                    for (let i = 0; i < firstWeekday; i++) cells.push(null);
                    for (let d = 1; d <= daysInMonth; d++) cells.push(d);
                    return (
                      <div className="mt-1 grid grid-cols-7 gap-1">
                        {cells.map((day, idx) => {
                          if (day === null) return <div key={`b-${idx}`} />;
                          const ymd = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
                          const selected = ymd === customDay;
                          const isFuture = ymd > todayYmd;
                          return (
                            <button
                              key={ymd}
                              type="button"
                              disabled={isFuture}
                              onClick={() => setCustomDay(ymd)}
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
                    );
                  })()}
                </div>
                <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  Lote será datado em <strong>{new Date(`${customDay}T12:00:00`).toLocaleDateString("pt-BR")}</strong>.
                </p>
              </div>

              <div className="flex justify-end gap-2 pt-1">
                <button
                  type="button"
                  onClick={() => setCustomImportOpen(false)}
                  className="inline-flex h-10 items-center rounded-lg border border-border-light px-4 text-sm font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  onClick={runCustomImport}
                  disabled={customFiles.length === 0 || importMutation.isPending}
                  className="inline-flex h-10 items-center gap-1.5 rounded-lg bg-primary px-4 text-sm font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="material-icons-outlined text-[18px]">upload_file</span>
                  Importar
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {mapEntry && typeof document !== "undefined" ? (() => {
        const e = mapEntry.entry;
        const CED = { label: "Cedente", city: e.clientCity, lat: e.clientLatitude, lng: e.clientLongitude, color: "#612035" };
        const AG = { label: "Agência", city: e.agencyCityPdf, lat: e.agencyLatitude, lng: e.agencyLongitude, color: "#D1732C" };
        const SAC = { label: "Sacado", city: e.payerCity, lat: e.payerLatitude, lng: e.payerLongitude, color: "#2956E0" };
        const view = {
          ALL: { points: [CED, AG, SAC], card: <><DistanceCard label="Cedente ↔ Agência" from={e.clientCity} to={e.agencyCityPdf} value={e.distanceClientAgencyKm} color="#D1732C" /><DistanceCard label="Sacado ↔ Agência" from={e.payerCity} to={e.agencyCityPdf} value={e.distanceAgencyPayerKm} color="#2956E0" /><DistanceCard label="Cedente ↔ Sacado" from={e.clientCity} to={e.payerCity} value={e.distanceClientPayerKm} color="#612035" /></> },
          CED_AG: { points: [CED, AG], card: <DistanceCard label="Cedente ↔ Agência" from={e.clientCity} to={e.agencyCityPdf} value={e.distanceClientAgencyKm} color="#D1732C" /> },
          SAC_AG: { points: [SAC, AG], card: <DistanceCard label="Sacado ↔ Agência" from={e.payerCity} to={e.agencyCityPdf} value={e.distanceAgencyPayerKm} color="#2956E0" /> },
          CED_SAC: { points: [CED, SAC], card: <DistanceCard label="Cedente ↔ Sacado" from={e.clientCity} to={e.payerCity} value={e.distanceClientPayerKm} color="#612035" /> },
        }[mapEntry.pair];
        const setPair = (pair: typeof mapEntry.pair) => setMapEntry({ entry: e, pair });
        return createPortal(
        <div
          className="fixed inset-0 z-[125] flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm"
          onClick={() => setMapEntry(null)}
        >
          <div
            className="flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-2xl dark:border-border-dark dark:bg-surface-dark"
            onClick={(ev) => ev.stopPropagation()}
          >
            <div className="flex items-center justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-grafite dark:text-white">{e.titleNumber}</p>
                <p className="truncate text-xs text-gray-500 dark:text-gray-400">
                  Sacado: {clean(e.payerName)} · Cedente: {e.clientName ?? "—"}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setMapEntry(null)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 dark:hover:bg-white/10"
                aria-label="Fechar"
              >
                <span className="material-icons-outlined text-[20px]">close</span>
              </button>
            </div>
            <div className="flex flex-wrap items-center gap-1.5 border-b border-border-light px-4 py-2 dark:border-border-dark">
              {([
                ["ALL", "Todos"],
                ["CED_AG", "Cedente ↔ Agência"],
                ["SAC_AG", "Sacado ↔ Agência"],
                ["CED_SAC", "Cedente ↔ Sacado"],
              ] as const).map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => setPair(key)}
                  className={`rounded-full px-3 py-1 text-xs font-bold transition-colors ${
                    mapEntry.pair === key
                      ? "bg-primary text-white dark:bg-secondary"
                      : "bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-white/10 dark:text-gray-300 dark:hover:bg-white/20"
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
            <div className={`grid grid-cols-1 gap-2 p-4 ${mapEntry.pair === "ALL" ? "sm:grid-cols-3" : ""}`}>
              {view.card}
            </div>
            <div className="min-h-[340px] flex-1 overflow-hidden border-t border-border-light dark:border-border-dark">
              <PaymentPlaceMap points={view.points} />
            </div>
          </div>
        </div>,
        document.body,
        );
      })() : null}

      {copyMenu && typeof document !== "undefined" ? createPortal(
        <div
          className={`fixed inset-0 z-[130] bg-black/30 backdrop-blur-[2px] transition-opacity duration-150 ${copyMenuVisible ? "opacity-100" : "opacity-0"}`}
          onClick={closeCopyMenu}
          onContextMenu={(e) => { e.preventDefault(); closeCopyMenu(); }}
        >
          <div
            className={`absolute w-[280px] max-w-[calc(100vw-24px)] max-h-[calc(100vh-24px)] overflow-y-auto rounded-xl border border-border-light bg-surface-light py-1 shadow-2xl transition-all duration-150 dark:border-border-dark dark:bg-surface-dark ${
              copyMenuVisible ? "scale-100 opacity-100" : "scale-95 opacity-0"
            }`}
            style={{
              left: Math.max(8, Math.min(copyMenu.x, (typeof window !== "undefined" ? window.innerWidth : 9999) - 296)),
              top: Math.max(8, Math.min(copyMenu.y, (typeof window !== "undefined" ? window.innerHeight : 9999) - 332)),
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {([
              ["Nº do título", copyMenu.entry.titleNumber],
              ["Sacado", copyMenu.entry.payerName],
              ["Cedente", copyMenu.entry.clientName],
              ["Valor pago", formatCurrencyBr(copyMenu.entry.paidValue) ?? copyMenu.entry.paidValue],
              ["Vencimento", copyMenu.entry.dueDate],
              ["Instituição", copyMenu.entry.bankName ?? copyMenu.entry.bacenInstitutionName],
              ["Banco/Agência", copyMenu.entry.bankAgency],
            ] as const).map(([label, value]) => (
              <button
                key={label}
                type="button"
                onClick={() => copyField(label, value)}
                className="flex w-full flex-col px-3 py-1.5 text-left transition-colors hover:bg-gray-50 dark:hover:bg-white/5"
              >
                <span className="text-[10px] font-bold uppercase tracking-wide text-gray-400">{label}</span>
                <span className="text-sm font-medium leading-snug text-grafite dark:text-white">
                  {value == null || String(value).trim() === "" ? <span className="text-gray-400">—</span> : String(value)}
                </span>
              </button>
            ))}
          </div>
        </div>,
        document.body,
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
                {fileNameByBatch.get(expandedEntry.batchId) ? (
                  <p className="mt-0.5 inline-flex max-w-full items-center gap-1 text-[11px] text-gray-400" title={fileNameByBatch.get(expandedEntry.batchId)}>
                    <span className="material-icons-outlined text-[13px]">description</span>
                    <span className="truncate">{fileNameByBatch.get(expandedEntry.batchId)}</span>
                  </p>
                ) : null}
                {expandedEntry.decidedAt ? (
                  <p className="mt-0.5 inline-flex items-center gap-1 text-[11px] text-emerald-600 dark:text-emerald-300">
                    <span className="material-icons-outlined text-[13px]">how_to_reg</span>
                    Decidido{expandedEntry.decidedByName ? ` por ${expandedEntry.decidedByName}` : ""} em {formatDateTime(expandedEntry.decidedAt)}
                  </p>
                ) : null}
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
                  onClick={() => decide(expandedEntry, "INCONCLUSIVO")}
                  disabled={decideMutation.isPending}
                  title="Inconclusivo (I)"
                  className={`inline-flex h-9 items-center justify-center rounded-lg px-4 text-xs font-bold transition-colors disabled:opacity-60 ${
                    expandedEntry.analystDecision === "INCONCLUSIVO" ? "bg-amber-500 text-white" : "bg-amber-100 text-amber-800 hover:bg-amber-200 dark:bg-amber-500/15 dark:text-amber-300"
                  }`}
                >
                  Inconclusivo
                </button>
                {expandedEntry.analystDecision ? (
                  <button
                    type="button"
                    onClick={() => reopen(expandedEntry)}
                    disabled={reopenMutation.isPending}
                    title="Reabrir análise (volta para Pendentes e sai da empresa)"
                    className="inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border border-amber-200 px-3 text-xs font-bold text-amber-700 transition-colors hover:bg-amber-50 disabled:opacity-60 dark:border-amber-500/30 dark:text-amber-300 dark:hover:bg-amber-500/10"
                  >
                    <span className="material-icons-outlined text-[16px]">undo</span>
                    Reabrir
                  </button>
                ) : null}
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
              />
            </div>
          </div>
        </div>,
        document.body,
      ) : null}
    </div>
  );
}

function haversineKm(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371;
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLng = ((bLng - aLng) * Math.PI) / 180;
  const lat1 = (aLat * Math.PI) / 180;
  const lat2 = (bLat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return Math.round(R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)) * 10) / 10;
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

// Observação persistente da parte (cedente/sacado) — salva por documento, reaproveitada em todos os títulos.
function PartyNoteField({ partyType, document, label, color }: { partyType: "CEDENTE" | "SACADO"; document?: string | null; label: string; color: string }) {
  const hasDoc = Boolean(document && document.replace(/\D/g, ""));
  const noteQuery = usePartyNote(partyType, hasDoc ? document : null);
  const save = useSavePartyNote();
  const loaded = noteQuery.data?.note ?? "";
  const [edited, setEdited] = useState<string | null>(null);
  const value = edited !== null ? edited : loaded;

  const persist = () => {
    if (!hasDoc || edited === null) return;
    if (edited.trim() === loaded.trim()) {
      setEdited(null);
      return;
    }
    save.mutate(
      { partyType, document: document as string, note: edited },
      { onSuccess: () => setEdited(null) },
    );
  };

  return (
    <div>
      <p className="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-wide" style={{ color }}>
        <span className="h-2.5 w-2.5 rounded-full" style={{ background: color }} />
        {label}
      </p>
      {hasDoc ? (
        <>
          <textarea
            value={value}
            onChange={(e) => setEdited(e.target.value)}
            onBlur={persist}
            disabled={noteQuery.isLoading || save.isPending}
            rows={2}
            placeholder="Observação que vale para todos os títulos desta parte..."
            className="mt-1 w-full resize-y rounded-lg border border-border-light bg-white px-3 py-2 text-sm text-grafite outline-none transition focus:border-primary disabled:opacity-60 dark:border-border-dark dark:bg-background-dark dark:text-white"
          />
          <p className="mt-0.5 text-[11px] text-gray-400">
            {save.isPending ? "Salvando..." : "Salva automaticamente ao sair do campo · vale para todos os títulos desta parte."}
          </p>
        </>
      ) : (
        <p className="mt-1 text-xs text-gray-400">Disponível após o documento (CNPJ) ser vinculado.</p>
      )}
    </div>
  );
}

function CedenteLinkField({ entry }: { entry: PaymentPlaceEntry }) {
  const [cnpj, setCnpj] = useState("");
  const [confirmCnpj, setConfirmCnpj] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const link = useLinkCedenteCnpj();

  const submit = (create: boolean) => {
    const digits = cnpj.replace(/\D/g, "");
    if (digits.length !== 14) {
      setError("Informe um CNPJ com 14 dígitos");
      return;
    }
    setError(null);
    link.mutate(
      { entryId: entry.id, cnpj: digits, create },
      {
        onSuccess: () => {
          setConfirmCnpj(null);
          setCnpj("");
        },
        onError: (e) => {
          if (e instanceof CompanyNotInPortfolioError) {
            setConfirmCnpj(digits);
          } else {
            setError(e.message);
          }
        },
      },
    );
  };

  return (
    <div className="mt-2">
      <div className="flex items-center gap-2">
        <input
          value={cnpj}
          onChange={(e) => setCnpj(e.target.value)}
          placeholder={`CNPJ do cedente (cód. ${entry.clientCode ?? ""})`}
          inputMode="numeric"
          className="h-8 w-full max-w-[230px] rounded-md border border-border-light px-2 text-sm text-grafite outline-none focus:border-primary dark:border-border-dark dark:bg-background-dark dark:text-white"
        />
        <button
          type="button"
          onClick={() => submit(false)}
          disabled={link.isPending}
          className="inline-flex h-8 items-center gap-1 rounded-md bg-primary px-3 text-xs font-bold text-white transition hover:opacity-90 disabled:opacity-50"
        >
          <span className="material-icons-outlined text-[15px]">search</span>
          {link.isPending && !confirmCnpj ? "Consultando…" : "Consultar"}
        </button>
      </div>
      <p className="mt-1 text-[11px] text-gray-400">Busca na carteira e vincula o código a este CNPJ.</p>
      {error ? <p className="mt-1 text-[11px] text-red-500">{error}</p> : null}
      {confirmCnpj ? (
        <div className="mt-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900 dark:border-amber-700 dark:bg-amber-500/10 dark:text-amber-200">
          <p className="font-semibold">{formatCnpj(confirmCnpj)} não está na carteira.</p>
          <p className="mt-0.5">Criar a empresa com os dados do CNPJ Já e vincular o código {entry.clientCode}?</p>
          <div className="mt-2 flex gap-2">
            <button
              type="button"
              onClick={() => submit(true)}
              disabled={link.isPending}
              className="rounded-md bg-secondary px-3 py-1 font-bold text-white transition hover:opacity-90 disabled:opacity-50"
            >
              {link.isPending ? "Criando…" : "Criar e vincular"}
            </button>
            <button
              type="button"
              onClick={() => setConfirmCnpj(null)}
              className="rounded-md border border-amber-300 px-3 py-1 font-semibold text-amber-900 transition hover:bg-amber-100 dark:border-amber-700 dark:text-amber-200"
            >
              Cancelar
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function EntryDetail({ entry, onEnrichAgency, enriching, onEnrichPayerCnpj, enrichingPayerCnpj }: { entry: PaymentPlaceEntry; onEnrichAgency: () => void; enriching: boolean; onEnrichPayerCnpj: () => void; enrichingPayerCnpj: boolean }) {
  // Filiais do sacado (camada opcional, verde).
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

  // Filiais do cedente: busca SÓ no clique (custo de BigQuery). Cacheadas por raiz.
  // Clicar numa filial recalcula as distâncias do cedente.
  const [showCedenteBranches, setShowCedenteBranches] = useState(false);
  const [selectedCedenteBranch, setSelectedCedenteBranch] = useState<string | null>(null);
  const cedenteBranchesQuery = useCompanyBranches(entry.clientDocument ?? undefined, showCedenteBranches);
  const cedenteBranches = (cedenteBranchesQuery.data ?? []).filter(
    (b) => typeof b.latitude === "number" && typeof b.longitude === "number",
  );
  const cedenteBranchMarkers = cedenteBranches.map((b) => ({
    id: b.cnpj,
    label: b.matriz ? "Matriz (cedente)" : "Filial (cedente)",
    city: b.municipio,
    lat: b.latitude as number,
    lng: b.longitude as number,
  }));
  const selBranch = cedenteBranches.find((b) => b.cnpj === selectedCedenteBranch) ?? null;

  const agencyHasCoords = typeof entry.agencyLatitude === "number" && typeof entry.agencyLongitude === "number";
  const payerHasCoords = typeof entry.payerLatitude === "number" && typeof entry.payerLongitude === "number";
  const distClientAgency = selBranch && agencyHasCoords
    ? haversineKm(selBranch.latitude as number, selBranch.longitude as number, entry.agencyLatitude as number, entry.agencyLongitude as number)
    : entry.distanceClientAgencyKm;
  const distClientPayer = selBranch && payerHasCoords
    ? haversineKm(selBranch.latitude as number, selBranch.longitude as number, entry.payerLatitude as number, entry.payerLongitude as number)
    : entry.distanceClientPayerKm;
  const cedenteLabel = selBranch ? (selBranch.matriz ? "Matriz" : "Filial") : "Cedente";
  const cedenteCity = selBranch ? selBranch.municipio : entry.clientCity;

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
          <DistanceCard label={`${cedenteLabel} ↔ Agência`} from={cedenteCity} to={entry.agencyCityPdf} value={distClientAgency} color={selBranch ? "#7C3AED" : "#D1732C"} />
          <DistanceCard label="Sacado ↔ Agência" from={entry.payerCity} to={entry.agencyCityPdf} value={entry.distanceAgencyPayerKm} color="#2956E0" />
          <DistanceCard label={`${cedenteLabel} ↔ Sacado`} from={cedenteCity} to={entry.payerCity} value={distClientPayer} color={selBranch ? "#7C3AED" : "#612035"} />
        </div>
        {selBranch ? (
          <div className="mt-2 flex flex-wrap items-center gap-2 rounded-lg bg-violet-50 px-3 py-2 text-xs text-violet-800 dark:bg-violet-500/10 dark:text-violet-300">
            <span className="material-icons-outlined text-[14px]">alt_route</span>
            Distâncias recalculadas pela filial do cedente em <strong>{selBranch.municipio ?? "—"}</strong> ({selBranch.cnpj})
            <button type="button" onClick={() => setSelectedCedenteBranch(null)} className="ml-auto font-semibold underline">Limpar</button>
          </div>
        ) : null}
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <div className="space-y-4">
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
                <>
                  <p className="mt-0.5 text-sm text-gray-400">
                    Sem cadastro vinculado (município: {clean(entry.clientCity)})
                  </p>
                  {entry.clientCode ? <CedenteLinkField entry={entry} /> : null}
                </>
              )}
            </div>
            <PartyNoteField partyType="CEDENTE" document={entry.clientDocument} label="Observação do cedente" color="#612035" />
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
            <PartyNoteField partyType="SACADO" document={entry.payerDocument} label="Observação do sacado" color="#2956E0" />
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
          {showCedenteBranches && cedenteBranchMarkers.length > 0 ? (
            <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#7C3AED" }} />Filiais do cedente</span>
          ) : null}
          {showBranches ? (
            <span className="inline-flex items-center gap-1.5"><span className="h-3 w-3 rounded-full" style={{ background: "#1F9D55" }} />Filiais do sacado</span>
          ) : null}
          <div className="ml-auto flex items-center gap-2">
            {!showCedenteBranches ? (
              <button
                type="button"
                onClick={() => setShowCedenteBranches(true)}
                disabled={!entry.clientDocument}
                title={entry.clientDocument ? undefined : "Cedente sem CNPJ vinculado"}
                className="rounded-md border border-border-light px-2.5 py-1 text-[11px] font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
              >
                Filiais do cedente
              </button>
            ) : cedenteBranchesQuery.isFetching ? (
              <span className="text-[11px] text-gray-400">Carregando filiais do cedente…</span>
            ) : (
              <button
                type="button"
                onClick={() => { setShowCedenteBranches(false); setSelectedCedenteBranch(null); }}
                className="rounded-md border border-border-light px-2.5 py-1 text-[11px] font-semibold text-gray-600 transition hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
              >
                Ocultar filiais do cedente ({cedenteBranchMarkers.length})
              </button>
            )}
            <button
              type="button"
              onClick={() => setShowBranches((v) => !v)}
              disabled={!entry.payerDocument}
              className="rounded-md border border-border-light px-2.5 py-1 text-[11px] font-semibold text-gray-600 transition hover:bg-gray-50 disabled:opacity-40 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5"
            >
              {branchesQuery.isFetching
                ? "Carregando filiais…"
                : showBranches
                  ? `Ocultar filiais${branchPoints.length ? ` (${branchPoints.length})` : ""}`
                  : "Mostrar filiais do sacado"}
            </button>
          </div>
        </div>
        {showBranches && branchesQuery.isError ? (
          <p className="text-[11px] text-red-500">{(branchesQuery.error as Error)?.message ?? "Erro ao carregar filiais"}</p>
        ) : null}
        <div className="min-h-[300px] flex-1 overflow-hidden rounded-xl border border-border-light shadow-sm dark:border-border-dark">
          <PaymentPlaceMap
            points={points}
            branches={showCedenteBranches ? cedenteBranchMarkers : []}
            selectedBranchId={selectedCedenteBranch}
            onBranchClick={(id) => setSelectedCedenteBranch((cur) => (cur === id ? null : id))}
            branchColor="#7C3AED"
          />
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
