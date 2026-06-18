"use client";

import Link from "next/link";
import { Fragment, useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CreditAnalysisData, totalDebtFromAnalysis, totalPendingFromAnalysis } from "../../../../types/company-detail";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

function useCedentes() {
  return useQuery<CreditAnalysisData[]>({
    queryKey: ["visaoCedente"],
    queryFn: async () => {
      const res = await fetch(`${API_BASE}/reports/visao-cedente`, { headers: getAuthHeaders() });
      if (!res.ok) throw new Error("Erro ao buscar cedentes");
      return res.json();
    },
    staleTime: 2 * 60 * 1000,
  });
}

function formatCnpj(cnpj: string) {
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function formatDate(iso: string | null | undefined) {
  if (!iso) return "—";
  return new Date(iso).toLocaleDateString("pt-BR");
}

function formatCurrency(value: number | null | undefined) {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value);
}

function formatCompactNumber(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    notation: "compact",
    maximumFractionDigits: value >= 1000 ? 1 : 0,
  }).format(value);
}

function formatPercentage(value: number) {
  return new Intl.NumberFormat("pt-BR", {
    style: "percent",
    maximumFractionDigits: 1,
  }).format(value);
}

function getCapitalSocial(analysis: CreditAnalysisData): number | null {
  const v = analysis.partnerDetails?.companyData?.socialCapitalValue;
  return typeof v === "number" ? v : null;
}

function getUF(analysis: CreditAnalysisData): string {
  return (analysis.creditRatingDetails as Record<string, unknown> | undefined)?.address != null
    ? ((analysis.creditRatingDetails as { address?: { state?: string } })?.address?.state ?? "")
    : "";
}

function getCity(analysis: CreditAnalysisData): string {
  return (analysis.creditRatingDetails as Record<string, unknown> | undefined)?.address != null
    ? ((analysis.creditRatingDetails as { address?: { city?: string } })?.address?.city ?? "")
    : "";
}

function getConsultationTimestamp(analysis: CreditAnalysisData): number {
  return analysis.consultaEm ? new Date(analysis.consultaEm).getTime() : 0;
}

function normalizePrompt(value: string) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}

function parsePromptMoney(prompt: string): number | null {
  const normalized = normalizePrompt(prompt);
  const match = normalized.match(/(\d+(?:[.,]\d+)?)\s*(mi|milhao|milhoes|mil)?/);
  if (!match) return null;

  const base = Number.parseFloat(match[1].replace(",", "."));
  if (Number.isNaN(base)) return null;

  const suffix = match[2] ?? "";
  if (suffix.startsWith("mi") || suffix.startsWith("milhao") || suffix.startsWith("milhoes")) return base * 1_000_000;
  if (suffix.startsWith("mil")) return base * 1_000;
  return base;
}

function buildAiContext(prompt: string, companies: CreditAnalysisData[]) {
  const totalCapital = companies.reduce((sum, company) => sum + (getCapitalSocial(company) ?? 0), 0);
  const companiesWithCapital = companies.filter((company) => getCapitalSocial(company) != null).length;
  const capitalMedio = companiesWithCapital > 0 ? totalCapital / companiesWithCapital : 0;
  const topStates = Array.from(
    companies.reduce((acc, company) => {
      const uf = getUF(company) || "NI";
      acc.set(uf, (acc.get(uf) ?? 0) + 1);
      return acc;
    }, new Map<string, number>()),
  )
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([uf, count]) => `${uf}:${count}`);

  const amostraEmpresas = companies.slice(0, 12).map((company) => ({
    companyName: company.companyName,
    cnpj: formatCnpj(company.cnpj),
    uf: getUF(company),
    city: getCity(company),
    capitalSocial: getCapitalSocial(company),
    pendencias: totalPendingFromAnalysis(company),
    dividaTotal: totalDebtFromAnalysis(company),
    consultaEm: company.consultaEm,
  }));

  return JSON.stringify({
    prompt,
    totalEmpresas: companies.length,
    capitalTotal: totalCapital,
    capitalMedio,
    topEstados: topStates,
    amostraEmpresas,
  });
}

type SortField = "companyName" | "consultaEm" | "capital";

interface Filters {
  search: string;
  uf: string;
  capitalMin: string;
  capitalMax: string;
  dateFrom: string;
  dateTo: string;
}

interface StateMetric {
  uf: string;
  count: number;
  totalCapital: number;
  averageCapital: number | null;
  latestConsulta: string | null;
  cities: number;
  companies: CreditAnalysisData[];
}

interface CapitalBand {
  label: string;
  min: number;
  max: number | null;
  count: number;
}

interface AiReportResponse {
  available: boolean;
  title?: string;
  summary?: string;
  highlights?: string[];
  recommendations?: string[];
  errorMessage?: string;
}

const EMPTY_FILTERS: Filters = {
  search: "",
  uf: "",
  capitalMin: "",
  capitalMax: "",
  dateFrom: "",
  dateTo: "",
};

function mixChannel(start: number, end: number, ratio: number) {
  return Math.round(start + (end - start) * ratio);
}

function mixColor(start: [number, number, number], end: [number, number, number], ratio: number) {
  const safeRatio = Math.max(0, Math.min(1, ratio));
  const r = mixChannel(start[0], end[0], safeRatio);
  const g = mixChannel(start[1], end[1], safeRatio);
  const b = mixChannel(start[2], end[2], safeRatio);
  return `rgb(${r}, ${g}, ${b})`;
}

function getMapFill(ratio: number) {
  return mixColor([233, 225, 215], [118, 73, 32], ratio);
}

function getMapStroke(ratio: number) {
  return mixColor([255, 255, 255], [89, 53, 28], Math.max(0.15, ratio));
}

function QsaChip({ name, role }: { name: string; role?: string }) {
  return (
    <span className="inline-flex flex-col items-start rounded-2xl border border-white/70 bg-white/80 px-2.5 py-1.5 text-left shadow-[0_12px_30px_-24px_rgba(54,43,33,0.55)] backdrop-blur dark:border-white/10 dark:bg-white/5">
      <span className="text-xs font-bold text-grafite dark:text-white leading-tight">{name}</span>
      {role && <span className="text-[10px] text-gray-500 dark:text-gray-400 leading-tight">{role}</span>}
    </span>
  );
}

function PartnersList({ analysis }: { analysis: CreditAnalysisData }) {
  const partners = analysis.partnerDetails?.partnerCompleteReport?.partnersList ?? [];
  const directors = analysis.partnerDetails?.directorCompleteReport?.directorsList ?? [];

  if (partners.length === 0 && directors.length === 0) {
    return <span className="text-xs text-gray-400 italic">Não disponível</span>;
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {partners.slice(0, 2).map((p, i) => (
        <QsaChip key={`p-${i}`} name={p.name ?? "—"} role={`Sócio${p.capitalTotalValue != null ? ` · ${p.capitalTotalValue}%` : ""}`} />
      ))}
      {directors.slice(0, 1).map((d, i) => (
        <QsaChip key={`d-${i}`} name={d.name ?? "—"} role={d.role ?? "Administrador"} />
      ))}
      {partners.length + directors.length > 3 && (
        <span className="inline-flex items-center rounded-full border border-dashed border-primary/20 px-2.5 text-[11px] font-semibold text-primary/70">
          +{partners.length + directors.length - 3}
        </span>
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
  hint,
  icon,
  accent = "text-primary",
}: {
  label: string;
  value: string;
  hint: string;
  icon: string;
  accent?: string;
}) {
  return (
    <div className="relative overflow-hidden rounded-[28px] border border-white/60 bg-white/85 p-5 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/80">
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary/30 to-transparent" />
      <div className="mb-5 flex items-start justify-between gap-4">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">{label}</p>
          <p className={`mt-2 text-3xl font-bold text-grafite dark:text-white ${accent}`}>{value}</p>
        </div>
        <span className="material-icons-outlined text-[22px] text-primary/70">{icon}</span>
      </div>
      <p className="text-sm text-gray-500 dark:text-gray-400">{hint}</p>
    </div>
  );
}

function BrazilHeatMap({
  states,
  activeUF,
  hoveredUF,
  onHover,
  onLeave,
  onSelect,
}: {
  states: StateMetric[];
  activeUF: string | null;
  hoveredUF: string | null;
  onHover: (uf: string | null) => void;
  onLeave: () => void;
  onSelect: (uf: string) => void;
}) {
  const maxCount = states.reduce((max, state) => Math.max(max, state.count), 0);
  const [svgMarkup, setSvgMarkup] = useState<string>("");
  const [tooltip, setTooltip] = useState<{ uf: string; x: number; y: number } | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadSvg() {
      try {
        const res = await fetch("/brazil-states-map.svg");
        if (!res.ok) throw new Error("Falha ao carregar o mapa do Brasil.");
        const raw = await res.text();
        const prepared = raw
          .replace(/<style[\s\S]*?<\/style>/g, "")
          .replace(/id="([A-Z]{2})"/g, 'id="$1" data-uf="$1" tabindex="0" role="button" aria-label="Estado $1"');

        if (isMounted) {
          setSvgMarkup(prepared);
        }
      } catch {
        if (isMounted) {
          setSvgMarkup("");
        }
      }
    }

    void loadSvg();

    return () => {
      isMounted = false;
    };
  }, []);

  const cssRules = useMemo(() => {
    const metricMap = new Map(states.map((state) => [state.uf, state]));
    const rules = [
      ".brazil-svg-map svg{display:block;width:100%;height:100%;max-width:100%;max-height:100%;overflow:hidden;}",
      ".brazil-svg-map [data-uf]{cursor:pointer;transition:fill .22s ease,stroke .22s ease,filter .22s ease,transform .22s ease;transform-box:fill-box;transform-origin:center;outline:none;}",
      ".brazil-svg-map [data-uf]:focus-visible{filter:drop-shadow(0 0 0.65rem rgba(118,73,32,.32));}",
    ];

    const allUfs = [
      "AC", "AL", "AM", "AP", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT",
      "PA", "PB", "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO",
    ];

    allUfs.forEach((uf) => {
      const item = metricMap.get(uf);
      const ratio = item && maxCount > 0 ? item.count / maxCount : 0;
      const fill = getMapFill(item ? Math.max(0.18, ratio) : 0.06);
      const stroke = getMapStroke(item ? ratio : 0.05);
      const isHighlighted = activeUF === uf || hoveredUF === uf;

      rules.push(
        `.brazil-svg-map [data-uf="${uf}"]{fill:${isHighlighted ? "#221f1c" : fill};stroke:${isHighlighted ? "#f3d4a6" : stroke};stroke-width:${isHighlighted ? "1.8" : "1.1"};filter:${isHighlighted ? "drop-shadow(0 18px 30px rgba(34,31,28,.22))" : "none"};transform:${isHighlighted ? "translateY(-1px)" : "translateY(0)"};}`,
      );
    });

    return rules.join("");
  }, [states, maxCount, activeUF, hoveredUF]);

  const focusedState = states.find((state) => state.uf === (hoveredUF ?? activeUF)) ?? null;

  function extractUf(target: EventTarget | null) {
    if (!(target instanceof Element)) return null;
    return target.closest("[data-uf]")?.getAttribute("data-uf");
  }

  return (
    <div className="relative h-[472px] overflow-hidden rounded-[32px] border border-white/50 bg-[radial-gradient(circle_at_top_left,_rgba(132,94,55,0.16),_transparent_36%),radial-gradient(circle_at_bottom_right,_rgba(163,127,89,0.12),_transparent_28%),linear-gradient(180deg,_rgba(255,255,255,0.96),_rgba(246,241,236,0.92))] p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.55)] dark:border-white/10 dark:bg-[radial-gradient(circle_at_top_left,_rgba(132,94,55,0.18),_transparent_38%),radial-gradient(circle_at_bottom_right,_rgba(163,127,89,0.16),_transparent_30%),linear-gradient(180deg,_rgba(27,26,25,0.96),_rgba(18,18,18,0.96))]">
      <div className="absolute inset-0 opacity-60">
        <div className="absolute left-[15%] top-[12%] h-40 w-40 rounded-full bg-primary/10 blur-3xl" />
        <div className="absolute right-[16%] top-[24%] h-32 w-32 rounded-full bg-amber-200/35 blur-3xl dark:bg-primary/10" />
        <div className="absolute bottom-[10%] left-[36%] h-36 w-36 rounded-full bg-emerald-100/40 blur-3xl dark:bg-emerald-500/10" />
      </div>

      <div className="relative z-10 flex items-start justify-between gap-4">
        <div>
          <p className="text-[11px] font-semibold uppercase tracking-[0.26em] text-gray-500 dark:text-gray-400">Geografia Comercial</p>
          <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Mapa de concentração por UF</h2>
          <p className="mt-1 max-w-md text-sm text-gray-500 dark:text-gray-400">
            Passe o mouse para inspecionar densidade de leads e clique para fixar um estado no painel lateral.
          </p>
        </div>
        <div className="hidden rounded-full border border-white/60 bg-white/75 px-4 py-2 text-xs font-semibold text-gray-500 shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5 dark:text-gray-300 md:block">
          {states.length} UFs com empresas cedentes
        </div>
      </div>

      <div className="relative z-10 mt-8 h-[328px] overflow-hidden rounded-[30px] border border-primary/15 bg-[radial-gradient(circle_at_50%_44%,_rgba(255,255,255,0.98),_rgba(249,244,239,0.88)_56%,_rgba(243,234,225,0.72)_100%)] shadow-[inset_0_1px_0_rgba(255,255,255,0.6)] dark:bg-[radial-gradient(circle_at_50%_44%,_rgba(47,41,36,0.96),_rgba(27,24,22,0.88)_56%,_rgba(18,16,14,0.9)_100%)]">
        <div className="absolute inset-3 rounded-[26px] border border-white/50 bg-[radial-gradient(circle_at_50%_50%,_rgba(255,255,255,0.94),_rgba(255,255,255,0.52)_45%,_rgba(255,255,255,0.14)_100%)] dark:border-white/10 dark:bg-[radial-gradient(circle_at_50%_50%,_rgba(255,255,255,0.03),_rgba(255,255,255,0.01)_60%,_rgba(255,255,255,0)_100%)]" />
        <div className="pointer-events-none absolute inset-x-12 bottom-10 h-10 rounded-full bg-[radial-gradient(circle,_rgba(118,73,32,0.22),_transparent_68%)] blur-2xl dark:bg-[radial-gradient(circle,_rgba(0,0,0,0.38),_transparent_68%)]" />
        <div
          className="brazil-svg-map relative z-10 h-full w-full overflow-hidden px-8 py-6"
          onMouseOver={(event) => {
            const uf = extractUf(event.target);
            if (uf) onHover(uf);
          }}
          onMouseOut={(event) => {
            if (!(event.target instanceof Element)) return;
            const fromUf = event.target.closest("[data-uf]")?.getAttribute("data-uf");
            const toUf = extractUf(event.relatedTarget);
            if (fromUf && fromUf !== toUf) onLeave();
          }}
          onFocusCapture={(event) => {
            const uf = extractUf(event.target);
            if (uf) onHover(uf);
          }}
          onBlurCapture={() => onLeave()}
          onClick={(event) => {
            const uf = extractUf(event.target);
            if (uf) onSelect(uf);
          }}
          onMouseMove={(event) => {
            const uf = extractUf(event.target);
            if (!uf) return;
            const bounds = event.currentTarget.getBoundingClientRect();
            setTooltip({
              uf,
              x: event.clientX - bounds.left,
              y: event.clientY - bounds.top,
            });
          }}
        >
          <style>{cssRules}</style>
          {svgMarkup ? (
            <div className="h-full w-full [transform:scale(0.9)]" dangerouslySetInnerHTML={{ __html: svgMarkup }} />
          ) : (
            <div className="flex h-full items-center justify-center text-sm text-gray-500 dark:text-gray-400">
              Carregando mapa vetorial...
            </div>
          )}
        </div>

        {tooltip && (() => {
          const tooltipState = states.find((state) => state.uf === tooltip.uf);
          if (!tooltipState) return null;

          return (
            <div
              className="pointer-events-none absolute z-30 min-w-[190px] -translate-x-1/2 -translate-y-[112%] rounded-[22px] border border-white/70 bg-white/92 px-4 py-3 shadow-[0_18px_45px_-26px_rgba(76,55,35,0.7)] backdrop-blur dark:border-white/10 dark:bg-[#1e1b18]/92"
              style={{ left: tooltip.x, top: tooltip.y }}
            >
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">UF</p>
                  <p className="mt-0.5 text-xl font-bold text-grafite dark:text-white">{tooltipState.uf}</p>
                </div>
                <span className="rounded-full border border-primary/15 bg-primary/5 px-2.5 py-1 text-[10px] font-semibold text-primary">
                  {tooltipState.count} empresa{tooltipState.count !== 1 ? "s" : ""}
                </span>
              </div>
              <div className="mt-2 space-y-1 text-xs text-gray-500 dark:text-gray-400">
                <p>Capital médio {formatCurrency(tooltipState.averageCapital)}</p>
                <p>Última consulta {formatDate(tooltipState.latestConsulta)}</p>
              </div>
            </div>
          );
        })()}

        <div className="absolute bottom-4 left-5 z-20 rounded-2xl border border-white/60 bg-white/85 px-4 py-3 shadow-sm backdrop-blur dark:border-white/10 dark:bg-[#1e1b18]/85">
          <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">Intensidade</p>
          <div className="mt-2 flex items-center gap-2">
            <span className="text-[10px] font-semibold text-gray-400">Baixa</span>
            <div className="h-2 w-28 rounded-full bg-[linear-gradient(90deg,_rgb(233,225,215),_rgb(118,73,32))]" />
            <span className="text-[10px] font-semibold text-gray-400">Alta</span>
          </div>
        </div>

        {focusedState && (
          <div className="absolute right-5 top-4 z-20 min-w-[180px] rounded-[24px] border border-white/60 bg-white/88 px-4 py-4 shadow-[0_20px_40px_-28px_rgba(76,55,35,0.65)] backdrop-blur dark:border-white/10 dark:bg-[#1e1b18]/88">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">UF em destaque</p>
                <p className="mt-1 text-2xl font-bold text-grafite dark:text-white">{focusedState.uf}</p>
              </div>
              <span className="rounded-full border border-primary/15 bg-primary/5 px-2.5 py-1 text-[10px] font-semibold text-primary">
                {focusedState.count} empresa{focusedState.count !== 1 ? "s" : ""}
              </span>
            </div>
            <div className="mt-3 space-y-1.5 text-xs text-gray-500 dark:text-gray-400">
              <p>Capital médio {formatCurrency(focusedState.averageCapital)}</p>
              <p>Última consulta {formatDate(focusedState.latestConsulta)}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function renderSortIcon(field: SortField, sortField: SortField, sortDir: "asc" | "desc") {
  if (sortField !== field) {
    return <span className="material-icons-outlined text-[14px] opacity-20">sort</span>;
  }

  return <span className="material-icons-outlined text-[14px]">{sortDir === "asc" ? "arrow_upward" : "arrow_downward"}</span>;
}

export default function VisaoCedentePage() {
  const { data: cedentes = [], isLoading, error } = useCedentes();
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [sortField, setSortField] = useState<SortField>("consultaEm");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [expandedCnpj, setExpandedCnpj] = useState<string | null>(null);
  const [activeUF, setActiveUF] = useState<string | null>(null);
  const [hoveredUF, setHoveredUF] = useState<string | null>(null);
  const [aiPrompt, setAiPrompt] = useState("");
  const [aiReport, setAiReport] = useState<AiReportResponse | null>(null);
  const [isAiLoading, setIsAiLoading] = useState(false);

  const ufs = useMemo(() => {
    const set = new Set(cedentes.map((c) => getUF(c)).filter(Boolean));
    return Array.from(set).sort();
  }, [cedentes]);

  const filtered = useMemo(() => {
    let list = [...cedentes];

    if (filters.search) {
      const q = filters.search.toLowerCase();
      list = list.filter(
        (c) => c.companyName?.toLowerCase().includes(q) || c.cnpj?.includes(filters.search.replace(/\D/g, "")),
      );
    }
    if (filters.uf) {
      list = list.filter((c) => getUF(c) === filters.uf);
    }
    if (filters.capitalMin) {
      const min = parseFloat(filters.capitalMin);
      list = list.filter((c) => (getCapitalSocial(c) ?? 0) >= min);
    }
    if (filters.capitalMax) {
      const max = parseFloat(filters.capitalMax);
      list = list.filter((c) => (getCapitalSocial(c) ?? Infinity) <= max);
    }
    if (filters.dateFrom) {
      list = list.filter((c) => c.consultaEm && new Date(c.consultaEm) >= new Date(filters.dateFrom));
    }
    if (filters.dateTo) {
      list = list.filter((c) => c.consultaEm && new Date(c.consultaEm) <= new Date(`${filters.dateTo}T23:59:59`));
    }

    list.sort((a, b) => {
      let va: number | string = 0;
      let vb: number | string = 0;

      if (sortField === "companyName") {
        va = a.companyName?.toLowerCase() ?? "";
        vb = b.companyName?.toLowerCase() ?? "";
      } else if (sortField === "consultaEm") {
        va = a.consultaEm ?? "";
        vb = b.consultaEm ?? "";
      } else {
        va = getCapitalSocial(a) ?? 0;
        vb = getCapitalSocial(b) ?? 0;
      }

      if (va < vb) return sortDir === "asc" ? -1 : 1;
      if (va > vb) return sortDir === "asc" ? 1 : -1;
      return 0;
    });

    return list;
  }, [cedentes, filters, sortField, sortDir]);

  const aiMatchedCompanies = useMemo(() => {
    const prompt = normalizePrompt(aiPrompt.trim());
    if (!prompt) return filtered;

    let list = [...filtered];
    const parsedMoney = parsePromptMoney(prompt);

    if ((prompt.includes("acima de") || prompt.includes("maior que") || prompt.includes("superior a")) && parsedMoney != null) {
      list = list.filter((company) => (getCapitalSocial(company) ?? 0) >= parsedMoney);
    }

    if ((prompt.includes("abaixo de") || prompt.includes("menor que") || prompt.includes("inferior a")) && parsedMoney != null) {
      list = list.filter((company) => (getCapitalSocial(company) ?? Number.POSITIVE_INFINITY) <= parsedMoney);
    }

    if (prompt.includes("sem pendencia") || prompt.includes("sem pendencias") || prompt.includes("sem negativ")) {
      list = list.filter((company) => totalPendingFromAnalysis(company) === 0);
    }

    if (prompt.includes("com pendencia") || prompt.includes("com pendencias") || prompt.includes("com negativ")) {
      list = list.filter((company) => totalPendingFromAnalysis(company) > 0);
    }

    if (prompt.includes("sem protesto") || prompt.includes("sem protestos")) {
      list = list.filter((company) => (company.negativeSummary?.notary?.summary?.count ?? 0) === 0);
    }

    if (prompt.includes("capital informado")) {
      list = list.filter((company) => getCapitalSocial(company) != null);
    }

    const ufMatch = prompt.match(/\b(ac|al|am|ap|ba|ce|df|es|go|ma|mg|ms|mt|pa|pb|pe|pi|pr|rj|rn|ro|rr|rs|sc|se|sp|to)\b/);
    if (ufMatch) {
      const uf = ufMatch[1].toUpperCase();
      list = list.filter((company) => getUF(company) === uf);
    }

    return list;
  }, [aiPrompt, filtered]);

  const stateMetrics = useMemo<StateMetric[]>(() => {
    const grouped = new Map<string, CreditAnalysisData[]>();

    filtered.forEach((company) => {
      const uf = getUF(company);
      if (!uf) return;
      const list = grouped.get(uf) ?? [];
      list.push(company);
      grouped.set(uf, list);
    });

    return Array.from(grouped.entries())
      .map(([uf, companies]) => {
        const capitals = companies.map((company) => getCapitalSocial(company)).filter((value): value is number => value != null);
        const totalCapital = capitals.reduce((sum, value) => sum + value, 0);
        const latest = companies.reduce<string | null>((acc, company) => {
          if (!company.consultaEm) return acc;
          if (!acc) return company.consultaEm;
          return new Date(company.consultaEm) > new Date(acc) ? company.consultaEm : acc;
        }, null);
        const citySet = new Set(companies.map((company) => getCity(company)).filter(Boolean));
        const topCompanies = [...companies].sort((a, b) => (getCapitalSocial(b) ?? 0) - (getCapitalSocial(a) ?? 0)).slice(0, 6);

        return {
          uf,
          count: companies.length,
          totalCapital,
          averageCapital: capitals.length > 0 ? totalCapital / capitals.length : null,
          latestConsulta: latest,
          cities: citySet.size,
          companies: topCompanies,
        };
      })
      .sort((a, b) => b.count - a.count || b.totalCapital - a.totalCapital);
  }, [filtered]);

  const capitalBands = useMemo<CapitalBand[]>(() => {
    const bands: Omit<CapitalBand, "count">[] = [
      { label: "Até 100 mil", min: 0, max: 100_000 },
      { label: "100 mil a 500 mil", min: 100_000, max: 500_000 },
      { label: "500 mil a 1 mi", min: 500_000, max: 1_000_000 },
      { label: "Acima de 1 mi", min: 1_000_000, max: null },
    ];

    return bands.map((band) => ({
      ...band,
      count: filtered.filter((company) => {
        const capital = getCapitalSocial(company);
        if (capital == null) return false;
        if (band.max == null) return capital >= band.min;
        return capital >= band.min && capital < band.max;
      }).length,
    }));
  }, [filtered]);

  const totalCapital = useMemo(
    () => filtered.reduce((sum, company) => sum + (getCapitalSocial(company) ?? 0), 0),
    [filtered],
  );

  const companiesWithCapital = useMemo(
    () => filtered.filter((company) => getCapitalSocial(company) != null).length,
    [filtered],
  );

  const averageCapital = companiesWithCapital > 0 ? totalCapital / companiesWithCapital : null;

  const latestConsulta = useMemo(() => {
    if (!filtered.length) return null;
    const latest = [...filtered].sort((a, b) => getConsultationTimestamp(b) - getConsultationTimestamp(a))[0];
    return latest?.consultaEm ?? null;
  }, [filtered]);

  const concentrationShare = useMemo(() => {
    if (!filtered.length) return 0;
    const topThree = stateMetrics.slice(0, 3).reduce((sum, item) => sum + item.count, 0);
    return topThree / filtered.length;
  }, [filtered, stateMetrics]);

  const resolvedActiveUF =
    (filters.uf && stateMetrics.some((item) => item.uf === filters.uf) && filters.uf) ||
    (activeUF && stateMetrics.some((item) => item.uf === activeUF) && activeUF) ||
    stateMetrics[0]?.uf ||
    null;

  const selectedState = stateMetrics.find((item) => item.uf === (hoveredUF ?? resolvedActiveUF)) ?? stateMetrics[0] ?? null;
  const topStates = stateMetrics.slice(0, 5);
  const hasFilters = Object.values(filters).some(Boolean);
  const capitalBandMax = capitalBands.reduce((max, band) => Math.max(max, band.count), 0);
  const aiMatchedCapital = aiMatchedCompanies.reduce((sum, company) => sum + (getCapitalSocial(company) ?? 0), 0);
  const aiMatchedWithoutPendencies = aiMatchedCompanies.filter((company) => totalPendingFromAnalysis(company) === 0).length;

  async function generateAiReport() {
    if (!aiPrompt.trim()) return;

    setIsAiLoading(true);
    try {
      const response = await fetch(`${API_BASE}/reports/visao-cedente/ai-report`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: JSON.stringify({
          prompt: aiPrompt,
          context: buildAiContext(aiPrompt, aiMatchedCompanies),
        }),
      });

      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const json: AiReportResponse = await response.json();
      setAiReport(json);
    } catch {
      setAiReport({
        available: false,
        errorMessage: "Não foi possível gerar o relatório via IA agora.",
      });
    } finally {
      setIsAiLoading(false);
    }
  }

  function toggleSort(field: SortField) {
    if (sortField === field) {
      setSortDir((current) => (current === "asc" ? "desc" : "asc"));
      return;
    }

    setSortField(field);
    setSortDir("asc");
  }

  return (
    <>
      <div className="relative mb-8 overflow-hidden rounded-[36px] border border-white/60 bg-[radial-gradient(circle_at_top_left,_rgba(128,93,59,0.20),_transparent_28%),radial-gradient(circle_at_80%_20%,_rgba(188,157,122,0.16),_transparent_24%),linear-gradient(180deg,_#fcfaf7_0%,_#f4efe9_100%)] p-7 shadow-[0_35px_90px_-60px_rgba(76,55,35,0.85)] dark:border-white/10 dark:bg-[radial-gradient(circle_at_top_left,_rgba(128,93,59,0.18),_transparent_30%),radial-gradient(circle_at_80%_20%,_rgba(188,157,122,0.12),_transparent_24%),linear-gradient(180deg,_#1f1d1b_0%,_#141311_100%)] md:p-8">
        <div className="absolute inset-0 opacity-70">
          <div className="absolute left-[-4%] top-[-10%] h-56 w-56 rounded-full bg-primary/10 blur-3xl" />
          <div className="absolute right-[6%] top-[8%] h-48 w-48 rounded-full bg-amber-200/30 blur-3xl dark:bg-primary/10" />
        </div>

        <div className="relative z-10 flex flex-col gap-8 xl:flex-row xl:items-end xl:justify-between">
          <div className="max-w-3xl">
            <nav className="mb-3 flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
              <Link href="/" className="hover:text-primary transition-colors">Carteira</Link>
              <span className="material-icons-outlined text-[14px]">chevron_right</span>
              <span className="text-grafite dark:text-white font-medium">Relatórios</span>
              <span className="material-icons-outlined text-[14px]">chevron_right</span>
              <span className="text-grafite dark:text-white font-medium">Visão Cedente</span>
            </nav>

            <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-white/70 bg-white/70 px-4 py-2 text-[11px] font-semibold uppercase tracking-[0.28em] text-primary shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5">
              <span className="material-icons-outlined text-[16px]">insights</span>
              BI Comercial
            </div>

            <h1 className="max-w-2xl font-sans text-4xl font-bold leading-tight text-grafite dark:text-white md:text-5xl">
              Visão Cedente com leitura de território, capital e concentração comercial
            </h1>
            <p className="mt-4 max-w-2xl text-base text-gray-600 dark:text-gray-300">
              Um painel para prospecção ativa: veja onde estão os cedentes, quanto capital concentram e quais estados pedem uma ofensiva comercial mais direcionada.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:min-w-[420px]">
            <div className="rounded-[24px] border border-white/60 bg-white/78 px-4 py-4 text-center shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">Cedentes</p>
              <p className="mt-2 text-3xl font-bold text-green-600 dark:text-green-400">{formatCompactNumber(cedentes.length)}</p>
            </div>
            <div className="rounded-[24px] border border-white/60 bg-white/78 px-4 py-4 text-center shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">Filtrados</p>
              <p className="mt-2 text-3xl font-bold text-grafite dark:text-white">{formatCompactNumber(filtered.length)}</p>
            </div>
            <div className="rounded-[24px] border border-white/60 bg-white/78 px-4 py-4 text-center shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">UFs</p>
              <p className="mt-2 text-3xl font-bold text-grafite dark:text-white">{formatCompactNumber(stateMetrics.length)}</p>
            </div>
            <div className="rounded-[24px] border border-white/60 bg-white/78 px-4 py-4 text-center shadow-sm backdrop-blur dark:border-white/10 dark:bg-white/5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">Top 3 UFs</p>
              <p className="mt-2 text-3xl font-bold text-grafite dark:text-white">{formatPercentage(concentrationShare)}</p>
            </div>
          </div>
        </div>
      </div>

      <div className="mb-8 grid grid-cols-1 gap-4 xl:grid-cols-4">
        <MetricCard
          label="Capital Médio"
          value={formatCurrency(averageCapital)}
          hint="Média apenas entre empresas com capital social disponível."
          icon="query_stats"
        />
        <MetricCard
          label="Capital Total"
          value={formatCurrency(totalCapital)}
          hint="Soma consolidada do capital social dos cedentes visíveis."
          icon="account_balance_wallet"
          accent="text-[#7a4c21]"
        />
        <MetricCard
          label="Última Consulta"
          value={formatDate(latestConsulta)}
          hint="Data mais recente de consulta dentro do recorte atual."
          icon="schedule"
          accent="text-[#3d4c67]"
        />
        <MetricCard
          label="Cobertura Territorial"
          value={`${stateMetrics.length} UFs`}
          hint="Quantidade de estados com pelo menos uma empresa cedente."
          icon="public"
          accent="text-[#49684d]"
        />
      </div>

      <div className="mb-8 grid grid-cols-1 gap-6 xl:grid-cols-[1.35fr_0.95fr]">
        <BrazilHeatMap
          states={stateMetrics}
          activeUF={resolvedActiveUF}
          hoveredUF={hoveredUF}
          onHover={setHoveredUF}
          onLeave={() => setHoveredUF(null)}
          onSelect={setActiveUF}
        />

        <div className="rounded-[32px] border border-white/60 bg-white/88 p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/85">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Estado em Foco</p>
              <h2 className="mt-2 font-sans text-3xl font-bold text-grafite dark:text-white">
                {selectedState ? selectedState.uf : "Sem dados"}
              </h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                {selectedState
                  ? `Expansão comercial com ${selectedState.count} empresa${selectedState.count !== 1 ? "s" : ""} mapeada${selectedState.count !== 1 ? "s" : ""}.`
                  : "Nenhuma UF disponível com os filtros atuais."}
              </p>
            </div>
            {selectedState && (
              <button
                type="button"
                onClick={() => setFilters((current) => ({ ...current, uf: current.uf === selectedState.uf ? "" : selectedState.uf }))}
                className="rounded-full border border-primary/20 bg-primary/5 px-4 py-2 text-xs font-semibold text-primary transition-colors hover:bg-primary hover:text-white"
              >
                {filters.uf === selectedState.uf ? "Remover filtro" : "Filtrar tabela por UF"}
              </button>
            )}
          </div>

          {selectedState ? (
            <>
              <div className="mt-6 grid grid-cols-2 gap-3">
                <div className="rounded-[24px] border border-gray-100 bg-[#f9f5f0] px-4 py-4 dark:border-white/10 dark:bg-white/5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Empresas</p>
                  <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{selectedState.count}</p>
                </div>
                <div className="rounded-[24px] border border-gray-100 bg-[#f9f5f0] px-4 py-4 dark:border-white/10 dark:bg-white/5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Cidades</p>
                  <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{selectedState.cities}</p>
                </div>
                <div className="rounded-[24px] border border-gray-100 bg-[#f9f5f0] px-4 py-4 dark:border-white/10 dark:bg-white/5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Capital Médio</p>
                  <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{formatCurrency(selectedState.averageCapital)}</p>
                </div>
                <div className="rounded-[24px] border border-gray-100 bg-[#f9f5f0] px-4 py-4 dark:border-white/10 dark:bg-white/5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Última Consulta</p>
                  <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{formatDate(selectedState.latestConsulta)}</p>
                </div>
              </div>

              <div className="mt-6">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">
                    Empresas em destaque
                  </p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">Ordenadas por capital social</p>
                </div>

                <div className="space-y-3">
                  {selectedState.companies.map((company) => (
                    <div
                      key={company.cnpj}
                      className="rounded-[24px] border border-gray-100 bg-white/95 px-4 py-4 shadow-sm dark:border-white/10 dark:bg-white/5"
                    >
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0">
                          <Link
                            href={`/clients/${company.cnpj}`}
                            className="block truncate text-sm font-bold text-grafite transition-colors hover:text-primary dark:text-white dark:hover:text-primary"
                          >
                            {company.companyName ?? "—"}
                          </Link>
                          <p className="mt-1 text-xs font-mono text-gray-500 dark:text-gray-400">{formatCnpj(company.cnpj)}</p>
                        </div>
                        <div className="shrink-0 rounded-full border border-primary/10 bg-primary/5 px-3 py-1 text-xs font-semibold text-primary">
                          {formatCurrency(getCapitalSocial(company))}
                        </div>
                      </div>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <span className="rounded-full border border-gray-200 px-3 py-1 text-[11px] font-semibold text-gray-600 dark:border-white/10 dark:text-gray-300">
                          Consulta {formatDate(company.consultaEm)}
                        </span>
                        <span className="rounded-full border border-gray-200 px-3 py-1 text-[11px] font-semibold text-gray-600 dark:border-white/10 dark:text-gray-300">
                          {getCity(company) || "Cidade não informada"}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <div className="mt-8 rounded-[24px] border border-dashed border-gray-200 px-5 py-10 text-center text-sm text-gray-500 dark:border-white/10 dark:text-gray-400">
              Ajuste os filtros para voltar a visualizar a distribuição por UF.
            </div>
          )}
        </div>
      </div>

      <div className="mb-8 rounded-[32px] border border-white/60 bg-white/88 p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/85">
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-[1.1fr_0.9fr]">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Copiloto IA</p>
            <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Geração de relatório em linguagem natural</h2>
            <p className="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">
              Escreva o recorte que você quer analisar. A página interpreta critérios como capital social, ausência de pendências e UF, e o Gemini transforma o resultado em leitura executiva.
            </p>

            <div className="mt-5 flex flex-wrap gap-2">
              {[
                "Empresas com capital acima de 1 mi",
                "Trazer empresas sem pendências",
                "Empresas de SP com capital informado",
              ].map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => setAiPrompt(suggestion)}
                  className="rounded-full border border-primary/15 bg-primary/5 px-3 py-2 text-xs font-semibold text-primary transition-colors hover:bg-primary hover:text-white"
                >
                  {suggestion}
                </button>
              ))}
            </div>

            <div className="mt-5">
              <textarea
                value={aiPrompt}
                onChange={(event) => setAiPrompt(event.target.value)}
                rows={4}
                placeholder="Ex.: traga empresas com capital social acima de 1 mi e sem pendências"
                className="w-full rounded-[24px] border border-gray-200 bg-white px-4 py-3 text-sm text-grafite outline-none transition-colors placeholder:text-gray-400 focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              />
            </div>

            <div className="mt-4 flex flex-wrap items-center gap-3">
              <button
                type="button"
                onClick={() => void generateAiReport()}
                disabled={!aiPrompt.trim() || isAiLoading}
                className="inline-flex items-center gap-2 rounded-full bg-grafite px-5 py-3 text-sm font-semibold text-white transition-all hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50 dark:bg-primary dark:hover:bg-[#8d5b2d]"
              >
                <span className={`material-icons-outlined text-[18px] ${isAiLoading ? "animate-spin" : ""}`}>
                  {isAiLoading ? "sync" : "auto_awesome"}
                </span>
                {isAiLoading ? "Gerando relatório" : "Gerar relatório com IA"}
              </button>
              <p className="text-xs text-gray-500 dark:text-gray-400">
                O relatório usa o recorte atual da tela e refina pelo texto digitado.
              </p>
            </div>
          </div>

          <div className="rounded-[28px] border border-primary/10 bg-[linear-gradient(180deg,_rgba(251,248,244,0.95),_rgba(246,239,232,0.95))] p-5 dark:border-white/10 dark:bg-[linear-gradient(180deg,_rgba(255,255,255,0.03),_rgba(255,255,255,0.02))]">
            <div className="mb-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-gray-500 dark:text-gray-400">Prévia do recorte</p>
                <h3 className="mt-2 font-sans text-xl font-bold text-grafite dark:text-white">Resultado interpretado</h3>
              </div>
              <span className="rounded-full border border-primary/15 bg-white/80 px-3 py-1 text-xs font-semibold text-primary dark:bg-white/5">
                {aiMatchedCompanies.length} empresa{aiMatchedCompanies.length !== 1 ? "s" : ""}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-[22px] border border-gray-100 bg-white/90 px-4 py-4 dark:border-white/10 dark:bg-white/5">
                <p className="text-[11px] uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Capital Total</p>
                <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{formatCurrency(aiMatchedCapital)}</p>
              </div>
              <div className="rounded-[22px] border border-gray-100 bg-white/90 px-4 py-4 dark:border-white/10 dark:bg-white/5">
                <p className="text-[11px] uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Sem Pendências</p>
                <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{aiMatchedWithoutPendencies}</p>
              </div>
            </div>

            <div className="mt-4">
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">Empresas em destaque</p>
              <div className="space-y-2">
                {aiMatchedCompanies.slice(0, 4).map((company) => (
                  <div key={company.cnpj} className="rounded-[20px] border border-gray-100 bg-white/90 px-4 py-3 dark:border-white/10 dark:bg-white/5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-bold text-grafite dark:text-white">{company.companyName}</p>
                        <p className="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                          {getUF(company) || "UF não informada"} · {formatCnpj(company.cnpj)}
                        </p>
                      </div>
                      <span className="shrink-0 rounded-full border border-primary/15 bg-primary/5 px-2.5 py-1 text-[10px] font-semibold text-primary">
                        {formatCurrency(getCapitalSocial(company))}
                      </span>
                    </div>
                  </div>
                ))}
                {aiMatchedCompanies.length === 0 && (
                  <div className="rounded-[20px] border border-dashed border-gray-200 px-4 py-6 text-center text-sm text-gray-500 dark:border-white/10 dark:text-gray-400">
                    Nenhuma empresa atende ao recorte pedido.
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>

        {(aiReport || isAiLoading) && (
          <div className="mt-6 rounded-[28px] border border-sky-200/70 bg-[linear-gradient(135deg,_rgba(240,249,255,0.96),_rgba(232,245,255,0.92))] p-5 shadow-[0_18px_50px_-36px_rgba(58,123,175,0.65)] dark:border-sky-800/40 dark:bg-[linear-gradient(135deg,_rgba(18,48,72,0.34),_rgba(10,25,39,0.38))]">
            <div className="mb-4 flex items-center gap-3">
              <span className={`material-icons-outlined text-sky-600 dark:text-sky-300 ${isAiLoading ? "animate-spin" : ""}`}>
                {isAiLoading ? "sync" : "auto_awesome"}
              </span>
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-sky-600/80 dark:text-sky-300/80">Narrativa Gemini</p>
                <h3 className="font-sans text-xl font-bold text-sky-950 dark:text-sky-100">
                  {aiReport?.title || "Analisando recorte solicitado"}
                </h3>
              </div>
            </div>

            {isAiLoading && (
              <p className="text-sm text-sky-800 dark:text-sky-200">Interpretando o recorte e montando um parecer executivo...</p>
            )}

            {!isAiLoading && aiReport?.available && (
              <div className="space-y-4">
                <p className="text-sm leading-7 text-sky-950 dark:text-sky-100">{aiReport.summary}</p>
                {(aiReport.highlights?.length ?? 0) > 0 && (
                  <div>
                    <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-sky-700 dark:text-sky-300">Destaques</p>
                    <div className="grid grid-cols-1 gap-2 md:grid-cols-3">
                      {aiReport.highlights?.map((item, index) => (
                        <div key={index} className="rounded-[20px] border border-white/60 bg-white/75 px-4 py-3 text-sm text-sky-950 shadow-sm dark:border-white/10 dark:bg-white/5 dark:text-sky-100">
                          {item}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {(aiReport.recommendations?.length ?? 0) > 0 && (
                  <div>
                    <p className="mb-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-sky-700 dark:text-sky-300">Ações sugeridas</p>
                    <div className="space-y-2">
                      {aiReport.recommendations?.map((item, index) => (
                        <div key={index} className="rounded-[20px] border border-sky-200/60 bg-white/70 px-4 py-3 text-sm text-sky-950 dark:border-sky-700/40 dark:bg-white/5 dark:text-sky-100">
                          {item}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {!isAiLoading && aiReport && !aiReport.available && (
              <p className="text-sm text-sky-900 dark:text-sky-100">{aiReport.errorMessage}</p>
            )}
          </div>
        )}
      </div>

      <div className="mb-8 grid grid-cols-1 gap-6 lg:grid-cols-[0.95fr_1.05fr]">
        <div className="rounded-[32px] border border-white/60 bg-white/88 p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/85">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Ranking Territorial</p>
              <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Top estados por volume</h2>
            </div>
            <span className="material-icons-outlined text-[22px] text-primary/60">leaderboard</span>
          </div>

          <div className="space-y-3">
            {topStates.length === 0 ? (
              <p className="rounded-[24px] border border-dashed border-gray-200 px-4 py-8 text-center text-sm text-gray-500 dark:border-white/10 dark:text-gray-400">
                Sem dados suficientes para montar o ranking.
              </p>
            ) : (
              topStates.map((state, index) => {
                const width = filtered.length > 0 ? Math.max((state.count / filtered.length) * 100, 12) : 12;

                return (
                  <button
                    key={state.uf}
                    type="button"
                    onClick={() => setActiveUF(state.uf)}
                    className={`w-full rounded-[24px] border px-4 py-4 text-left transition-all ${
                      resolvedActiveUF === state.uf
                        ? "border-primary/25 bg-primary/5"
                        : "border-gray-100 bg-[#faf7f3] hover:border-primary/15 hover:bg-white dark:border-white/10 dark:bg-white/5 dark:hover:bg-white/[0.07]"
                    }`}
                  >
                    <div className="mb-3 flex items-center justify-between gap-3">
                      <div className="flex items-center gap-3">
                        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-grafite text-sm font-bold text-white dark:bg-primary">
                          {index + 1}
                        </span>
                        <div>
                          <p className="text-sm font-bold text-grafite dark:text-white">{state.uf}</p>
                          <p className="text-xs text-gray-500 dark:text-gray-400">{state.cities} cidades mapeadas</p>
                        </div>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-bold text-grafite dark:text-white">{state.count}</p>
                        <p className="text-[11px] uppercase tracking-[0.16em] text-gray-400">empresas</p>
                      </div>
                    </div>

                    <div className="mb-3 h-2 rounded-full bg-white shadow-inner dark:bg-white/10">
                      <div className="h-full rounded-full bg-gradient-to-r from-[#7b4d24] via-primary to-[#d3a16f]" style={{ width: `${width}%` }} />
                    </div>

                    <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400">
                      <span>Capital médio {formatCurrency(state.averageCapital)}</span>
                      <span>Última consulta {formatDate(state.latestConsulta)}</span>
                    </div>
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div className="rounded-[32px] border border-white/60 bg-white/88 p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/85">
          <div className="mb-5 flex items-center justify-between">
            <div>
              <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Faixas de Capital</p>
              <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Distribuição da base</h2>
            </div>
            <span className="material-icons-outlined text-[22px] text-primary/60">stacked_bar_chart</span>
          </div>

          <div className="space-y-4">
            {capitalBands.map((band) => {
              const width = capitalBandMax > 0 ? Math.max((band.count / capitalBandMax) * 100, band.count > 0 ? 10 : 0) : 0;

              return (
                <div key={band.label}>
                  <div className="mb-2 flex items-center justify-between gap-4">
                    <p className="text-sm font-semibold text-grafite dark:text-white">{band.label}</p>
                    <p className="text-xs uppercase tracking-[0.18em] text-gray-500 dark:text-gray-400">{band.count} empresa{band.count !== 1 ? "s" : ""}</p>
                  </div>
                  <div className="h-3 rounded-full bg-[#f2ece4] dark:bg-white/10">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-[#324c6b] via-[#5678a0] to-[#9eb9d5] transition-all"
                      style={{ width: `${width}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <div className="mt-8 rounded-[24px] border border-dashed border-primary/20 bg-primary/5 px-5 py-4 dark:bg-primary/10">
            <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-primary/80">Leitura Rápida</p>
            <p className="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
              {topStates.length > 0
                ? `${topStates[0].uf} lidera em volume, enquanto as 3 principais UFs concentram ${formatPercentage(concentrationShare)} da base atual. Isso ajuda a diferenciar onde faz sentido abrir frente comercial e onde vale fazer ação cirúrgica.`
                : "Com os filtros atuais, não há dados suficientes para consolidar uma leitura de concentração."}
            </p>
          </div>
        </div>
      </div>

      <div className="mb-6 rounded-[32px] border border-white/60 bg-white/88 p-6 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/85">
        <div className="mb-5 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Exploração da Base</p>
            <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Filtros analíticos</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              Refine o recorte e deixe a tabela abaixo como apoio operacional para consulta e abertura de perfil.
            </p>
          </div>

          {hasFilters && (
            <button
              type="button"
              onClick={() => setFilters(EMPTY_FILTERS)}
              className="inline-flex items-center gap-2 rounded-full border border-gray-200 px-4 py-2 text-xs font-semibold text-gray-500 transition-colors hover:border-primary hover:text-primary dark:border-white/10 dark:text-gray-400 dark:hover:border-primary dark:hover:text-primary"
            >
              <span className="material-icons-outlined text-[15px]">clear</span>
              Limpar filtros
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 gap-3 md:grid-cols-12">
          <div className="relative md:col-span-4">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400">
              <span className="material-icons-outlined text-[18px]">search</span>
            </span>
            <input
              value={filters.search}
              onChange={(e) => setFilters((current) => ({ ...current, search: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white py-3 pl-10 pr-4 text-sm text-grafite outline-none transition-colors placeholder:text-gray-400 focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              placeholder="Buscar por razão social ou CNPJ..."
            />
          </div>

          <div className="md:col-span-2">
            <select
              value={filters.uf}
              onChange={(e) => setFilters((current) => ({ ...current, uf: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white px-3 py-3 text-sm text-grafite outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
            >
              <option value="">Todos os Estados</option>
              {ufs.map((uf) => (
                <option key={uf} value={uf}>
                  {uf}
                </option>
              ))}
            </select>
          </div>

          <div className="md:col-span-2">
            <input
              type="number"
              value={filters.capitalMin}
              onChange={(e) => setFilters((current) => ({ ...current, capitalMin: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white px-3 py-3 text-sm text-grafite outline-none transition-colors placeholder:text-gray-400 focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              placeholder="Capital mín (R$)"
            />
          </div>

          <div className="md:col-span-2">
            <input
              type="number"
              value={filters.capitalMax}
              onChange={(e) => setFilters((current) => ({ ...current, capitalMax: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white px-3 py-3 text-sm text-grafite outline-none transition-colors placeholder:text-gray-400 focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              placeholder="Capital máx (R$)"
            />
          </div>

          <div className="md:col-span-1">
            <input
              type="date"
              value={filters.dateFrom}
              onChange={(e) => setFilters((current) => ({ ...current, dateFrom: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white px-3 py-3 text-sm text-grafite outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              title="Data consulta — de"
            />
          </div>

          <div className="md:col-span-1">
            <input
              type="date"
              value={filters.dateTo}
              onChange={(e) => setFilters((current) => ({ ...current, dateTo: e.target.value }))}
              className="w-full rounded-2xl border border-gray-200 bg-white px-3 py-3 text-sm text-grafite outline-none transition-colors focus:border-primary focus:ring-1 focus:ring-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
              title="Data consulta — até"
            />
          </div>
        </div>
      </div>

      <div className="overflow-hidden rounded-[32px] border border-white/60 bg-white/92 shadow-[0_30px_80px_-50px_rgba(76,55,35,0.45)] backdrop-blur dark:border-white/10 dark:bg-surface-dark/88">
        <div className="flex flex-col gap-3 border-b border-gray-100 px-6 py-5 dark:border-white/10 md:flex-row md:items-end md:justify-between">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.24em] text-gray-500 dark:text-gray-400">Base Operacional</p>
            <h2 className="mt-2 font-sans text-2xl font-bold text-grafite dark:text-white">Empresas cedentes</h2>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              Consulta detalhada da base para aprofundar análise, abrir perfil e revisar composição societária.
            </p>
          </div>
          <div className="rounded-full border border-gray-200 bg-white px-4 py-2 text-sm text-gray-500 shadow-sm dark:border-white/10 dark:bg-white/5 dark:text-gray-300">
            <span className="font-semibold text-grafite dark:text-white">{filtered.length}</span> empresa{filtered.length !== 1 ? "s" : ""}
            {hasFilters && <span> de {cedentes.length}</span>}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[1040px] text-left text-sm">
            <thead className="border-b border-gray-100 bg-[#faf6f1] dark:border-white/10 dark:bg-white/5">
              <tr className="text-[11px] font-semibold uppercase tracking-[0.14em] text-gray-500 dark:text-gray-400">
                <th
                  className="cursor-pointer px-6 py-4 transition-colors hover:bg-white/70 dark:hover:bg-white/[0.04]"
                  onClick={() => toggleSort("companyName")}
                >
                  <div className="flex items-center gap-1">
                    Empresa / CNPJ {renderSortIcon("companyName", sortField, sortDir)}
                  </div>
                </th>
                <th className="px-6 py-4">UF</th>
                <th
                  className="cursor-pointer px-6 py-4 transition-colors hover:bg-white/70 dark:hover:bg-white/[0.04]"
                  onClick={() => toggleSort("capital")}
                >
                  <div className="flex items-center gap-1">
                    Capital Social {renderSortIcon("capital", sortField, sortDir)}
                  </div>
                </th>
                <th className="px-6 py-4">Quadro Societário</th>
                <th
                  className="cursor-pointer px-6 py-4 transition-colors hover:bg-white/70 dark:hover:bg-white/[0.04]"
                  onClick={() => toggleSort("consultaEm")}
                >
                  <div className="flex items-center gap-1">
                    Consulta {renderSortIcon("consultaEm", sortField, sortDir)}
                  </div>
                </th>
                <th className="px-6 py-4 text-right">Ações</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-white/10">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-500 dark:text-gray-400">
                    <span className="material-icons-outlined mb-2 block animate-spin text-2xl">sync</span>
                    Carregando relatório...
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-red-500">
                    <span className="material-icons-outlined mb-2 block text-2xl">error_outline</span>
                    Erro ao carregar dados. Verifique sua conexão.
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-500 dark:text-gray-400">
                    {hasFilters ? "Nenhuma empresa encontrada com os filtros aplicados." : "Nenhuma empresa com Visão Cedente registrada ainda."}
                  </td>
                </tr>
              ) : (
                filtered.map((company) => {
                  const isExpanded = expandedCnpj === company.cnpj;
                  const uf = getUF(company);
                  const capital = getCapitalSocial(company);
                  const partners = company.partnerDetails?.partnerCompleteReport?.partnersList ?? [];
                  const directors = company.partnerDetails?.directorCompleteReport?.directorsList ?? [];

                  return (
                    <Fragment key={company.cnpj}>
                      <tr className={`transition-colors hover:bg-[#fcf9f5] dark:hover:bg-white/[0.03] ${isExpanded ? "bg-primary/5" : ""}`}>
                        <td className="px-6 py-4">
                          <div className="flex flex-col">
                            <Link
                              href={`/clients/${company.cnpj}`}
                              className="font-bold text-grafite transition-colors hover:text-primary dark:text-white dark:hover:text-primary"
                            >
                              {company.companyName ?? "—"}
                            </Link>
                            <span className="mt-0.5 text-xs font-mono text-gray-500 dark:text-gray-400">
                              {formatCnpj(company.cnpj)}
                            </span>
                            <span className="mt-2 inline-flex w-fit items-center gap-1 rounded-full border border-green-200 bg-green-50 px-2.5 py-0.5 text-[10px] font-semibold text-green-700 dark:border-green-800 dark:bg-green-900/20 dark:text-green-400">
                              <span className="inline-block h-1.5 w-1.5 rounded-full bg-green-500" />
                              CEDENTE
                            </span>
                          </div>
                        </td>

                        <td className="px-6 py-4">
                          {uf ? (
                            <button
                              type="button"
                              onClick={() => setActiveUF(uf)}
                              className="rounded-full border border-gray-200 bg-[#f7f1eb] px-3 py-1 text-xs font-bold text-grafite transition-colors hover:border-primary hover:text-primary dark:border-white/10 dark:bg-white/5 dark:text-white"
                            >
                              {uf}
                            </button>
                          ) : (
                            "—"
                          )}
                        </td>

                        <td className="px-6 py-4 font-medium text-grafite dark:text-gray-200">{formatCurrency(capital)}</td>

                        <td className="max-w-xs px-6 py-4">
                          <PartnersList analysis={company} />
                        </td>

                        <td className="px-6 py-4 text-xs text-gray-500 dark:text-gray-400">{formatDate(company.consultaEm)}</td>

                        <td className="px-6 py-4 text-right">
                          <div className="flex items-center justify-end gap-1">
                            <button
                              type="button"
                              onClick={() => setExpandedCnpj(isExpanded ? null : company.cnpj)}
                              title={isExpanded ? "Recolher" : "Expandir QSA"}
                              className="rounded-xl p-2 text-gray-400 transition-colors hover:bg-primary/10 hover:text-primary"
                            >
                              <span className="material-icons-outlined text-[18px]">
                                {isExpanded ? "expand_less" : "expand_more"}
                              </span>
                            </button>
                            <Link
                              href={`/clients/${company.cnpj}`}
                              title="Ver perfil completo"
                              className="rounded-xl p-2 text-gray-400 transition-colors hover:bg-primary/10 hover:text-primary"
                            >
                              <span className="material-icons-outlined text-[18px]">visibility</span>
                            </Link>
                            <Link
                              href={`/clients/${company.cnpj}`}
                              title="Abrir relatório"
                              className="rounded-xl p-2 text-gray-400 transition-colors hover:bg-gray-100 hover:text-grafite dark:hover:bg-white/5 dark:hover:text-white"
                            >
                              <span className="material-icons-outlined text-[18px]">picture_as_pdf</span>
                            </Link>
                          </div>
                        </td>
                      </tr>

                      {isExpanded && (
                        <tr className="border-t border-primary/10 bg-[#fcf9f5] dark:border-primary/20 dark:bg-white/[0.03]">
                          <td colSpan={6} className="px-8 py-5">
                            <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
                              <div>
                                <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">
                                  Dados da Empresa
                                </p>
                                <dl className="space-y-2 text-sm">
                                  <div className="flex gap-2">
                                    <dt className="w-24 shrink-0 text-gray-500">Capital:</dt>
                                    <dd className="font-medium text-grafite dark:text-white">{formatCurrency(capital)}</dd>
                                  </div>
                                  <div className="flex gap-2">
                                    <dt className="w-24 shrink-0 text-gray-500">Estado:</dt>
                                    <dd className="font-medium text-grafite dark:text-white">{uf || "—"}</dd>
                                  </div>
                                  <div className="flex gap-2">
                                    <dt className="w-24 shrink-0 text-gray-500">Cidade:</dt>
                                    <dd className="font-medium text-grafite dark:text-white">{getCity(company) || "—"}</dd>
                                  </div>
                                  <div className="flex gap-2">
                                    <dt className="w-24 shrink-0 text-gray-500">Consulta:</dt>
                                    <dd className="font-medium text-grafite dark:text-white">{formatDate(company.consultaEm)}</dd>
                                  </div>
                                </dl>
                              </div>

                              <div>
                                <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">
                                  Sócios ({partners.length})
                                </p>
                                <div className="flex flex-col gap-2">
                                  {partners.length === 0 ? (
                                    <span className="text-xs italic text-gray-400">Não disponível</span>
                                  ) : (
                                    partners.map((partner, index) => (
                                      <div
                                        key={index}
                                        className="flex items-center justify-between rounded-2xl border border-gray-100 bg-white px-3 py-2 dark:border-white/10 dark:bg-white/5"
                                      >
                                        <div>
                                          <p className="text-xs font-bold text-grafite dark:text-white">{partner.name ?? "—"}</p>
                                          <p className="text-[10px] font-mono text-gray-500">{partner.documentId ?? ""}</p>
                                        </div>
                                        <span className="text-xs font-semibold text-primary">
                                          {partner.capitalTotalValue != null ? `${partner.capitalTotalValue}%` : ""}
                                        </span>
                                      </div>
                                    ))
                                  )}
                                </div>
                              </div>

                              <div>
                                <p className="mb-3 text-[11px] font-semibold uppercase tracking-[0.2em] text-gray-500 dark:text-gray-400">
                                  Diretores / Administradores ({directors.length})
                                </p>
                                <div className="flex flex-col gap-2">
                                  {directors.length === 0 ? (
                                    <span className="text-xs italic text-gray-400">Não disponível</span>
                                  ) : (
                                    directors.map((director, index) => (
                                      <div
                                        key={index}
                                        className="rounded-2xl border border-gray-100 bg-white px-3 py-2 dark:border-white/10 dark:bg-white/5"
                                      >
                                        <p className="text-xs font-bold text-grafite dark:text-white">{director.name ?? "—"}</p>
                                        <p className="text-[10px] text-gray-500">{director.role ?? "Administrador"}</p>
                                      </div>
                                    ))
                                  )}
                                </div>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between border-t border-gray-100 px-6 py-4 dark:border-white/10">
          <p className="text-sm text-gray-500 dark:text-gray-400">
            <span className="font-medium text-grafite dark:text-white">{filtered.length}</span> empresa{filtered.length !== 1 ? "s" : ""} encontrada{filtered.length !== 1 ? "s" : ""}
            {hasFilters && <span> de <span className="font-medium">{cedentes.length}</span></span>}
          </p>
          <Link
            href="/"
            className="inline-flex items-center gap-1 text-sm text-gray-500 transition-colors hover:text-primary dark:text-gray-400 dark:hover:text-primary"
          >
            <span className="material-icons-outlined text-[16px]">arrow_back</span>
            Voltar para carteira
          </Link>
        </div>
      </div>
    </>
  );
}
