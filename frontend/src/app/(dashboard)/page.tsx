"use client";

import Link from "next/link";
import { useState, useMemo, useRef, useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useCompanyList } from "../../hooks/useCompanyList";
import { useDashboardMetrics } from "../../hooks/useDashboardMetrics";
import { useImportCsv } from "../../hooks/useImportCsv";
import { totalPendingFromAnalysis, totalDebtFromAnalysis } from "../../types/company-detail";
import { toast } from "sonner";

// ─── Tooltip genérico com posicionamento inteligente ─────────────────────────
function Tooltip({ content, children }: { content: React.ReactNode; children: React.ReactNode }) {
  const [visible, setVisible] = useState(false);
  const ref = useRef<HTMLSpanElement>(null);
  const [above, setAbove] = useState(false);

  useEffect(() => {
    if (visible && ref.current) {
      const rect = ref.current.getBoundingClientRect();
      setAbove(rect.bottom + 180 > window.innerHeight);
    }
  }, [visible]);

  return (
    <span
      ref={ref}
      className="relative inline-flex items-center"
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocus={() => setVisible(true)}
      onBlur={() => setVisible(false)}
    >
      {children}
      {visible && (
        <span
          role="tooltip"
          className={`absolute z-50 left-1/2 -translate-x-1/2 w-64 rounded-lg bg-grafite dark:bg-gray-900 text-white text-xs leading-relaxed p-3 shadow-xl border border-gray-700 pointer-events-none ${
            above ? "bottom-full mb-2" : "top-full mt-2"
          }`}
        >
          {content}
          <span
            className={`absolute left-1/2 -translate-x-1/2 w-2 h-2 bg-grafite dark:bg-gray-900 border-gray-700 rotate-45 ${
              above
                ? "bottom-[-5px] border-b border-r"
                : "top-[-5px] border-t border-l"
            }`}
          />
        </span>
      )}
    </span>
  );
}


// ─── Badge de Visão Cedente ───────────────────────────────────────────────────
function VisaoCedenteBadge({ visaoCedente }: { visaoCedente: string | null | undefined }) {
  if (!visaoCedente || visaoCedente === 'PENDENTE') {
    return (
      <span className="inline-flex items-center gap-1 text-xs font-semibold text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-full px-2.5 py-0.5">
        <span className="w-1.5 h-1.5 rounded-full bg-gray-400 inline-block" />
        Pendente
      </span>
    );
  }
  if (visaoCedente === 'SIM') {
    return (
      <Tooltip content={
        <div>
          <p className="font-bold mb-1">Visão Cedente — SIM</p>
          <p className="text-gray-300 text-[11px]">Esta empresa possui histórico de operações como cedente em factoring/fomento. É um lead qualificado para prospecção comercial.</p>
        </div>
      }>
        <span className="inline-flex items-center gap-1 text-xs font-bold text-green-700 dark:text-green-400 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-full px-2.5 py-0.5 cursor-help">
          <span className="w-1.5 h-1.5 rounded-full bg-green-500 inline-block" />
          Cedente
        </span>
      </Tooltip>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 text-xs font-semibold text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-full px-2.5 py-0.5">
      <span className="w-1.5 h-1.5 rounded-full bg-red-400 inline-block" />
      Não
    </span>
  );
}

// ─── Badge de pendências ──────────────────────────────────────────────────────
function PendenciaBadge({ analysis }: { analysis: import("../../types/company-detail").CreditAnalysisData | null | undefined }) {
  if (!analysis) {
    return <span className="text-xs text-gray-400 italic font-serif">Sem análise</span>;
  }
  const count = totalPendingFromAnalysis(analysis);
  const debt  = totalDebtFromAnalysis(analysis);

  if (count === 0) {
    return (
      <span className="inline-flex items-center gap-1 text-xs font-bold text-green-700 dark:text-green-400 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-full px-2.5 py-0.5">
        <span className="material-icons-outlined text-[12px]">check_circle</span>
        Nada consta
      </span>
    );
  }

  const formatted = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", notation: "compact", maximumFractionDigits: 1 }).format(debt);
  return (
    <Tooltip content={
      <div>
        <p className="font-bold mb-1">Pendências Financeiras</p>
        <p className="text-gray-300 text-[11px]">Total de ocorrências em PEFIN, REFIN, protestos, cheques sem fundo, cobranças e ações judiciais.</p>
        <p className="mt-1 text-white font-bold">{count} ocorrência{count > 1 ? 's' : ''} · {new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(debt)}</p>
      </div>
    }>
      <span
        className="inline-flex items-center gap-1 text-xs font-bold text-red-700 dark:text-red-400 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-full px-2.5 py-0.5 cursor-help"
        tabIndex={0}
      >
        <span className="material-icons-outlined text-[12px]">warning_amber</span>
        {count} · {formatted}
      </span>
    </Tooltip>
  );
}

function hasNadaConstaSignal(analysis: import("../../types/company-detail").CreditAnalysisData | null | undefined) {
  return analysis?.negativeSummary?.message?.trim().toUpperCase() === "NADA CONSTA";
}

function NadaConstaIndicator({ analysis }: { analysis: import("../../types/company-detail").CreditAnalysisData | null | undefined }) {
  if (!hasNadaConstaSignal(analysis)) return null;

  return (
    <Tooltip
      content={
        <div>
          <p className="font-bold mb-1">Sinalização &quot;NADA CONSTA&quot;</p>
          <p className="text-gray-300 text-[11px]">
            A Serasa retornou a sinalização &quot;NADA CONSTA&quot;. Esse retorno deve ser tratado com atenção,
            pois pode indicar restrição de exibição de ocorrências e não necessariamente ausência de risco.
          </p>
        </div>
      }
    >
      <span
        className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-red-200 bg-red-50 text-red-600 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400 cursor-help"
        aria-label='Empresa com sinalização "NADA CONSTA"'
        tabIndex={0}
      >
        <span className="material-icons-outlined text-[14px] leading-none">priority_high</span>
      </span>
    </Tooltip>
  );
}

export default function GestaoCarteiraPage() {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [searchQuery, setSearchQuery] = useState("");
  const [filteredCedente, setFilteredCedente] = useState("");
  const [activeCardFilter, setActiveCardFilter] = useState<'' | 'analyzed' | 'cedente' | 'pending'>('');
  const [nameSortDir, setNameSortDir] = useState<'asc' | 'desc' | null>(null);
  
  // Modal states
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newCnpj, setNewCnpj] = useState("");
  const [isFetchingCnpja, setIsFetchingCnpja] = useState(false);
  const [isFetchingSerasa, setIsFetchingSerasa] = useState(false);
  const [modalError, setModalError] = useState("");

  const queryClient = useQueryClient();
  const { data: rawMetrics, isLoading: isMetricsLoading } = useDashboardMetrics();

  const metrics = rawMetrics ?? null;
  const effectiveVisaoCedente = filteredCedente || (activeCardFilter === 'cedente' ? 'SIM' : '');
  const effectiveAnalysisStatus =
    activeCardFilter === 'analyzed'
      ? 'ANALYZED'
      : activeCardFilter === 'pending'
        ? 'PENDING'
        : '';
  const { companies, pageData, isLoading: isCompaniesLoading } = useCompanyList(
    page,
    pageSize,
    searchQuery,
    effectiveVisaoCedente,
    effectiveAnalysisStatus,
  );
  const { importCsv, isImporting } = useImportCsv();

  const filteredCompanies = useMemo(() => {
    const result = [...companies];
    if (nameSortDir) {
      result.sort((a, b) => {
        const nameA = (a.companyDetail?.companyName || a.client?.name || "").toLowerCase();
        const nameB = (b.companyDetail?.companyName || b.client?.name || "").toLowerCase();
        return nameSortDir === 'asc' ? nameA.localeCompare(nameB) : nameB.localeCompare(nameA);
      });
    }
    return result;
  }, [companies, nameSortDir]);

  const handleEnrich = async (provider: 'cnpja' | 'serasa') => {
    if (!newCnpj.replace(/\D/g, '')) {
      setModalError("Por favor, informe um CNPJ válido.");
      return;
    }
    setModalError("");
    if (provider === 'cnpja') setIsFetchingCnpja(true);
    else setIsFetchingSerasa(true);

    try {
      const token = localStorage.getItem('serasa_token');
      const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';
      const cleanCnpj = newCnpj.replace(/\D/g, '');
      const res = await fetch(`${baseUrl}/company/enrich/${provider}/${cleanCnpj}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
      });
      if (!res.ok) {
        const errBody = await res.json().catch(() => ({}));
        throw new Error(errBody?.message || errBody?.error || `Falha ao buscar dados no ${provider.toUpperCase()} (HTTP ${res.status})`);
      }
      
      // Busca pelo CNPJ recém-adicionado para mostrar o resultado imediatamente
      setPage(0);
      setSearchQuery(cleanCnpj);
      await queryClient.invalidateQueries({ queryKey: ['companyList'] });
      await queryClient.invalidateQueries({ queryKey: ['dashboardMetrics'] });
      toast.success(`Dados do ${provider.toUpperCase()} adicionados com sucesso!`);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setModalError(err.message || 'Erro inesperado.');
      } else {
        setModalError('Erro inesperado.');
      }
    } finally {
      if (provider === 'cnpja') setIsFetchingCnpja(false);
      else setIsFetchingSerasa(false);
    }
  };

  const cancelModal = () => {
    setIsModalOpen(false);
    setNewCnpj("");
    setModalError("");
  };

  return (
    <>
      <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-end">
        <div>
          <nav className="mb-2 flex items-center text-sm text-gray-500 dark:text-gray-400">
            <span className="text-grafite dark:text-white font-medium">Carteira</span>
          </nav>
          <h1 className="font-sans text-3xl font-bold text-grafite dark:text-white">Gestão de Carteira</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Gerencie e analise o risco de crédito dos seus clientes.</p>
        </div>
        <div className="flex gap-3">
          <input
            type="file"
            id="csv-upload"
            accept=".csv"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0];
              if (file) {
                importCsv(file);
                e.target.value = ''; // Reset for consecutive uploads
              }
            }}
          />
          <button
            onClick={() => document.getElementById('csv-upload')?.click()}
            disabled={isImporting}
            className="inline-flex items-center justify-center gap-2 rounded-lg border border-border-light bg-surface-light px-4 py-2 text-sm font-medium text-grafite transition-all duration-200 ease-out hover:bg-gray-50 hover:-translate-y-[1px] active:scale-[0.98] disabled:opacity-60 disabled:cursor-not-allowed disabled:transform-none dark:border-border-dark dark:bg-surface-dark dark:text-white dark:hover:bg-gray-700 shadow-sm"
          >
            <span className={`material-icons-outlined text-lg ${isImporting ? 'animate-spin' : ''}`}>
              {isImporting ? 'sync' : 'upload_file'}
            </span>
            {isImporting ? 'Importando...' : 'Importar CSV'}
          </button>
          <button 
            onClick={() => setIsModalOpen(true)}
            className="relative inline-flex items-center justify-center gap-2 px-4 py-2 bg-gradient-to-r from-primary to-primary-hover text-white text-sm font-medium rounded-lg transition-all duration-200 ease-out hover:shadow-lg hover:shadow-primary/30 hover:-translate-y-[1px] shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(0,26,65,0.3)] active:scale-[0.98] active:translate-y-0 active:shadow-none focus:outline-none overflow-hidden group"
          >
            <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300 ease-out" />
            <span className="material-icons-outlined text-lg relative z-10">add</span>
            <span className="relative z-10">Novo Cliente</span>
          </button>
        </div>
      </div>

      {/* KPIs / Dashboard Summary */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-white dark:bg-surface-dark p-6 rounded-2xl border border-gray-100 dark:border-gray-800 shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] flex flex-col transition-transform duration-300 hover:-translate-y-1">
          <span className="text-gray-500 dark:text-gray-400 font-sans text-sm font-medium mb-2">Total de Clientes</span>
          <div className="flex items-center gap-3">
             <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500/10 to-blue-500/5 dark:from-blue-500/20 dark:to-blue-500/10 border border-blue-500/10 flex items-center justify-center text-blue-600 dark:text-blue-400">
               <span className="material-icons-outlined">business</span>
             </div>
             {isCompaniesLoading ? (
               <div className="h-8 w-16 bg-gray-200 dark:bg-gray-700 animate-pulse rounded"></div>
             ) : (
               <span className="text-3xl font-bold font-sans text-grafite dark:text-white">
                 {metrics?.totalClients ?? 0}
               </span>
             )}
          </div>
        </div>

        <button
          onClick={() => {
            setPage(0);
            setActiveCardFilter(f => f === 'analyzed' ? '' : 'analyzed');
          }}
          className={`text-left p-6 rounded-2xl border shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] flex flex-col transition-all duration-200 hover:-translate-y-1 cursor-pointer ${
            activeCardFilter === 'analyzed'
              ? 'bg-secondary/5 dark:bg-secondary/10 border-secondary/40 ring-2 ring-secondary/30'
              : 'bg-white dark:bg-surface-dark border-gray-100 dark:border-gray-800'
          }`}
        >
          <span className="text-gray-500 dark:text-gray-400 font-sans text-sm font-medium mb-2 flex items-center gap-1.5">
            Empresas Analisadas
            {activeCardFilter === 'analyzed' && <span className="material-icons-outlined text-[14px] text-secondary">filter_alt</span>}
          </span>
          <div className="flex items-center gap-3">
             <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-secondary/10 to-secondary/5 dark:from-secondary/20 dark:to-secondary/10 border border-secondary/10 flex items-center justify-center text-secondary dark:text-secondary">
               <span className="material-icons-outlined">check_circle</span>
             </div>
             {isMetricsLoading ? (
               <div className="h-8 w-16 bg-gray-200 dark:bg-gray-700 animate-pulse rounded"></div>
             ) : (
               <span className="text-3xl font-bold font-sans text-grafite dark:text-white">
                 {metrics?.analyzedClients ?? 0}
               </span>
             )}
          </div>
        </button>

        <button
          onClick={() => {
            setPage(0);
            setActiveCardFilter(f => f === 'cedente' ? '' : 'cedente');
          }}
          className={`text-left p-6 rounded-2xl border shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] flex flex-col transition-all duration-200 hover:-translate-y-1 cursor-pointer ${
            activeCardFilter === 'cedente'
              ? 'bg-green-50 dark:bg-green-900/10 border-green-400/40 ring-2 ring-green-400/30'
              : 'bg-white dark:bg-surface-dark border-gray-100 dark:border-gray-800'
          }`}
        >
          <span className="text-gray-500 dark:text-gray-400 font-sans text-sm font-medium mb-2 flex items-center gap-1.5">
            Visão Cedente
            <span className="inline-block w-2 h-2 rounded-full bg-green-500" />
            {activeCardFilter === 'cedente' && <span className="material-icons-outlined text-[14px] text-green-600">filter_alt</span>}
          </span>
          <div className="flex items-center gap-3">
             <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-green-500/10 to-green-500/5 dark:from-green-500/20 dark:to-green-500/10 border border-green-500/10 flex items-center justify-center text-green-600 dark:text-green-400">
               <span className="material-icons-outlined">swap_horiz</span>
             </div>
             {isMetricsLoading ? (
               <div className="h-8 w-16 bg-gray-200 dark:bg-gray-700 animate-pulse rounded"></div>
             ) : (
               <div className="flex flex-col">
                 <span className="text-3xl font-bold font-sans text-grafite dark:text-white">
                   {metrics?.cedenteSimCount ?? 0}
                 </span>
                 <span className="text-xs text-gray-400 font-sans">empresas cedentes</span>
               </div>
             )}
          </div>
        </button>

        <button
          onClick={() => {
            setPage(0);
            setActiveCardFilter(f => f === 'pending' ? '' : 'pending');
          }}
          className={`text-left p-6 rounded-2xl border shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] flex flex-col transition-all duration-200 hover:-translate-y-1 cursor-pointer ${
            activeCardFilter === 'pending'
              ? 'bg-amber-50 dark:bg-amber-900/10 border-amber-400/40 ring-2 ring-amber-400/30'
              : 'bg-white dark:bg-surface-dark border-gray-100 dark:border-gray-800'
          }`}
        >
          <span className="text-gray-500 dark:text-gray-400 font-sans text-sm font-medium mb-2 flex items-center gap-2">
             Aguardando Análise
             {activeCardFilter === 'pending' && <span className="material-icons-outlined text-[14px] text-amber-600">filter_alt</span>}
          </span>
          <div className="flex items-center gap-3">
             <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-amber-500/10 to-amber-500/5 dark:from-amber-500/20 dark:to-amber-500/10 border border-amber-500/10 flex items-center justify-center text-amber-600 dark:text-amber-400">
               <span className="material-icons-outlined">hourglass_empty</span>
             </div>
             {isMetricsLoading || isCompaniesLoading ? (
               <div className="h-8 w-16 bg-gray-200 dark:bg-gray-700 animate-pulse rounded"></div>
             ) : (
               <span className="text-3xl font-bold font-sans text-grafite dark:text-white">
                 {Math.max((metrics?.totalClients ?? 0) - (metrics?.analyzedClients ?? 0), 0)}
               </span>
             )}
          </div>
        </button>
      </div>

      {/* Filters */}
      <div className="mb-6 rounded-2xl bg-white p-5 shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] dark:bg-surface-dark border border-gray-100 dark:border-gray-800">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-12">
          <div className="md:col-span-6 relative">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-gray-400">
              <span className="material-icons-outlined">search</span>
            </span>
            <input 
              value={searchQuery}
              onChange={(e) => { setSearchQuery(e.target.value); setPage(0); }}
              className="w-full rounded-xl border border-gray-200 bg-white py-2.5 pl-10 pr-4 text-sm text-grafite placeholder-gray-400 focus:border-primary focus:ring-1 focus:ring-primary dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:placeholder-gray-500 outline-none transition-shadow hover:shadow-sm" 
              placeholder="Buscar por nome, CNPJ ou cidade..." 
              type="text"
            />
          </div>
          <div className="md:col-span-4">
            <select
              value={filteredCedente}
              onChange={(e) => {
                setFilteredCedente(e.target.value);
                setPage(0);
              }}
              className="w-full rounded-xl border border-gray-200 bg-white py-2.5 px-3 text-sm text-grafite focus:border-primary focus:ring-1 focus:ring-primary dark:border-gray-700 dark:bg-gray-800 dark:text-white outline-none transition-shadow hover:shadow-sm"
            >
              <option value="">Visão Cedente — Todas</option>
              <option value="SIM">✓ Cedente (SIM)</option>
              <option value="NAO">✗ Não Cedente (NÃO)</option>
              <option value="PENDENTE">⏳ Pendente</option>
            </select>
          </div>
          <div className="md:col-span-2 flex justify-end gap-2">
            <select 
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
              className="w-full rounded-xl border border-gray-200 bg-white py-2.5 px-3 text-sm text-grafite focus:border-primary focus:ring-1 focus:ring-primary dark:border-gray-700 dark:bg-gray-800 dark:text-white outline-none transition-shadow hover:shadow-sm"
            >
              <option value={10}>10 por pág.</option>
              <option value={50}>50 por pág.</option>
              <option value={100}>100 por pág.</option>
              <option value={200}>200 por pág.</option>
            </select>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-2xl bg-white shadow-[0_2px_10px_rgba(0,0,0,0.02)] dark:shadow-[0_2px_10px_rgba(0,0,0,0.2)] dark:bg-surface-dark border border-gray-100 dark:border-gray-800">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50/50 text-xs font-semibold uppercase text-gray-500 dark:bg-gray-800/50 dark:text-gray-400 font-sans border-b border-gray-100 dark:border-gray-800/60">
              <tr>
                <th 
                  className="px-6 py-4 cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-700/50 transition-colors select-none"
                  onClick={() => setNameSortDir(prev => prev === 'asc' ? 'desc' : 'asc')}
                >
                  <div className="flex items-center gap-1">
                    Cliente / CNPJ
                    {nameSortDir === 'asc' && <span className="material-icons-outlined text-[16px]">arrow_upward</span>}
                    {nameSortDir === 'desc' && <span className="material-icons-outlined text-[16px]">arrow_downward</span>}
                    {!nameSortDir && <span className="material-icons-outlined text-[16px] opacity-20">sort</span>}
                  </div>
                </th>
                <th className="px-6 py-4">Data / UF</th>
                <th className="px-6 py-4">Visão Cedente</th>
                <th className="px-6 py-4">Pendências</th>
                <th className="px-6 py-4 text-right">Relatório</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-700">
              {isCompaniesLoading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    <span className="material-icons-outlined animate-spin text-2xl mb-2">sync</span>
                    <p>Carregando clientes...</p>
                  </td>
                </tr>
              ) : filteredCompanies.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                    Nenhum cliente encontrado.
                  </td>
                </tr>
              ) : (
                filteredCompanies.map((profile) => {
                   const company = profile.companyDetail;
                   const analysis = profile.creditAnalysis;
                   
                   return (
                   <tr key={company?.id || profile.client?.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex flex-col">
                        <div className="flex items-start gap-2">
                          <Link href={`/clients/${company?.documentNumber || profile.client?.documentNumber}`} className="font-sans font-bold text-grafite dark:text-white hover:text-primary dark:hover:text-primary transition-colors">
                            {company?.companyName || profile.client?.name || 'Razão Social não informada'}
                          </Link>
                          <NadaConstaIndicator analysis={analysis} />
                          {!profile.client?.clientCode ? (
                            <span
                              className="inline-flex items-center gap-1 rounded bg-amber-50 px-1.5 py-0.5 text-[10px] font-bold text-amber-700 dark:bg-amber-500/10 dark:text-amber-300"
                              title="Empresa sem código de cliente (ERP) — não cruza na Praça de Pagamento"
                            >
                              <span className="material-icons-outlined text-[12px]">warning</span>
                              sem código
                            </span>
                          ) : null}
                        </div>
                        <span className="text-xs text-gray-500 dark:text-gray-400 font-mono mt-1">
                          {(company?.documentNumber || profile.client?.documentNumber || '').replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5")}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-grafite dark:text-gray-300">
                       <span className="block text-xs text-gray-500 dark:text-gray-400">
                         {profile.client?.createdAt ? new Date(profile.client.createdAt).toLocaleDateString("pt-BR") : '-'}
                       </span>
                       {company?.city ? `${company.city}, ${company.state || ''}` : '-'}
                    </td>
                    <td className="px-6 py-4">
                      <VisaoCedenteBadge visaoCedente={analysis?.visaoCedente} />
                    </td>
                    <td className="px-6 py-4">
                      <PendenciaBadge analysis={analysis} />
                    </td>
                    <td className="px-6 py-4 text-right">
                      <Link
                        href={`/clients/${company?.documentNumber || profile.client?.documentNumber}`}
                        className="rounded-md p-1.5 text-gray-400 hover:bg-accent-blue/20 hover:text-grafite dark:text-gray-500 dark:hover:bg-gray-700 dark:hover:text-white transition-colors"
                        aria-label="Visualizar relatório"
                      >
                        <span className="material-icons-outlined text-lg">visibility</span>
                      </Link>
                    </td>
                  </tr>
                )})
              )}
            </tbody>
          </table>
        </div>
        
        {/* Pagination */ }
        <div className="flex items-center justify-between border-t border-gray-100 bg-white px-6 py-4 dark:border-gray-800 dark:bg-surface-dark">
            <div className="hidden sm:flex sm:flex-1 sm:items-center sm:justify-between">
                <div>
                    <p className="text-sm text-gray-700 dark:text-gray-400">
                        Mostrando <span className="font-medium text-grafite dark:text-white">{filteredCompanies.length > 0 ? page * pageSize + 1 : 0}</span> a <span className="font-medium text-grafite dark:text-white">{page * pageSize + filteredCompanies.length}</span> de <span className="font-medium text-grafite dark:text-white">{pageData?.totalElements ?? metrics?.totalClients ?? 0}</span> resultados
                    </p>
                </div>
                <div className="flex gap-2">
                   <button 
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="px-4 py-2 text-sm font-medium rounded-lg bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700 transition-colors shadow-sm">
                      Anterior
                   </button>
                   <button 
                      onClick={() => setPage(p => p + 1)}
                      disabled={!pageData || page >= pageData.totalPages - 1}
                      className="px-4 py-2 text-sm font-medium rounded-lg bg-white border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:bg-gray-800 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-700 transition-colors shadow-sm">
                      Próxima
                   </button>
                </div>
            </div>
        </div>
      </div>

      {/* New Client Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm px-4">
          <div className="w-full max-w-md bg-white dark:bg-surface-dark rounded-xl shadow-2xl border border-gray-100 dark:border-gray-700 overflow-hidden" 
               role="dialog" aria-modal="true" aria-labelledby="modal-title">
            <div className="p-6 border-b border-gray-100 dark:border-gray-700 flex justify-between items-center">
              <h3 id="modal-title" className="text-lg font-bold font-sans text-grafite dark:text-white">Adicionar Novo Cliente</h3>
              <button onClick={cancelModal} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 transition-colors">
                <span className="material-icons-outlined">close</span>
              </button>
            </div>
            <div className="p-6">
              {modalError && (
                <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 rounded-lg text-sm border border-red-100 dark:border-red-900/30">
                  {modalError}
                </div>
              )}
              <div className="mb-5">
                <label htmlFor="cnpj-input" className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                  CNPJ do Cliente
                </label>
                <input
                  id="cnpj-input"
                  type="text"
                  value={newCnpj}
                  onChange={(e) => setNewCnpj(e.target.value)}
                  placeholder="00.000.000/0000-00"
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-3 text-grafite dark:text-white focus:border-primary focus:ring-1 focus:ring-primary outline-none transition-colors"
                  autoFocus
                />
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-4 border-l-2 border-primary pl-2">
                Busque os dados nas bases abaixo. O cliente será adicionado automaticamente.
              </p>
              <div className="grid grid-cols-2 gap-3">
                <button
                  onClick={() => handleEnrich('cnpja')}
                  disabled={isFetchingCnpja || isFetchingSerasa}
                  style={{ backgroundColor: "#2956E0" }}
                  className="flex flex-col items-center justify-center py-3 px-4 rounded-lg text-white font-sans font-medium text-sm shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(41,86,224,0.3)] hover:-translate-y-[1px] hover:brightness-110 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none disabled:brightness-100"
                >
                  <span className={`material-icons-outlined mb-1 text-white text-[24px] ${isFetchingCnpja ? 'animate-spin' : ''}`}>
                    {isFetchingCnpja ? 'autorenew' : 'domain'}
                  </span>
                  <span>CNPJ Já</span>
                </button>
                <button
                  onClick={() => handleEnrich('serasa')}
                  disabled={isFetchingCnpja || isFetchingSerasa}
                  style={{ backgroundColor: "#E4006F" }}
                  className="flex flex-col items-center justify-center py-3 px-4 rounded-lg text-white font-sans font-medium text-sm shadow-[0_1px_2px_rgba(0,0,0,0.1),_0_2px_4px_rgba(228,0,111,0.3)] hover:-translate-y-[1px] hover:brightness-110 active:scale-[0.98] transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed disabled:transform-none disabled:shadow-none disabled:brightness-100"
                >
                  <span className={`material-icons-outlined mb-1 text-white text-[24px] ${isFetchingSerasa ? 'animate-spin' : ''}`}>
                    {isFetchingSerasa ? 'autorenew' : 'verified_user'}
                  </span>
                  <span>Serasa</span>
                </button>
              </div>
            </div>
            <div className="bg-gray-50 dark:bg-gray-800/50 p-4 border-t border-gray-100 dark:border-gray-700 flex justify-end">
              <button
                onClick={cancelModal}
                className="px-4 py-2 text-sm font-medium text-gray-600 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white transition-colors"
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
