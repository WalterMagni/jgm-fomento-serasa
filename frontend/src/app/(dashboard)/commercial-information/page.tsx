"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

type CommercialRecord = {
  id: string;
  cnpj?: string | null;
  empresa?: string | null;
  data?: string | null;
  tipo?: string | null;
  parceiro?: string | null;
  clienteDesde?: string | null;
  ultimaOperacaoData?: string | null;
  ultimaOperacaoValor?: string | null;
  limite?: string | null;
  riscoDuplicata?: string | null;
  riscoCheque?: string | null;
  riscoComissaria?: string | null;
  vencidosData?: string | null;
  vencidosValorMonetario?: string | null;
  vencidosValor?: string | null;
  vop?: string | null;
  pontual?: string | null;
  atraso?: string | null;
  recompra?: string | null;
  cartorio?: string | null;
  observacao?: string | null;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function clean(value: string | null | undefined) {
  return value && value.trim() ? value : "-";
}

function normalizeSearch(value: string) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase();
}

function formatCnpj(cnpj: string | null | undefined) {
  if (!cnpj) return "-";
  return cnpj.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function parseBrDate(value: string | null | undefined) {
  if (!value) return 0;
  const match = value.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!match) return 0;
  return new Date(Number(match[3]), Number(match[2]) - 1, Number(match[1])).getTime();
}

function parseFilterDate(value: string) {
  if (!value) return 0;
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day).getTime();
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function exportXls(records: CommercialRecord[]) {
  const headers = [
    "Cedente",
    "CNPJ",
    "Data",
    "Tipo",
    "Parceiro",
    "Desde",
    "Ultima operacao data",
    "Ultima operacao valor",
    "Risco duplicata",
    "Risco cheque",
    "Risco comissaria",
    "Ultimo vencimento duplicata",
    "Valor vencido",
    "Limite",
    "VOP",
    "L1 pontual",
    "L2 atraso",
    "L3 cartorio",
    "L4 recompra",
    "Observacao",
  ];

  const rows = records.map((record) => [
    clean(record.empresa),
    formatCnpj(record.cnpj),
    clean(record.data),
    clean(record.tipo),
    clean(record.parceiro),
    clean(record.clienteDesde),
    clean(record.ultimaOperacaoData),
    clean(record.ultimaOperacaoValor),
    clean(record.riscoDuplicata),
    clean(record.riscoCheque),
    clean(record.riscoComissaria),
    clean(record.vencidosValor),
    clean(record.vencidosValorMonetario),
    clean(record.limite),
    clean(record.vop),
    clean(record.pontual),
    clean(record.atraso),
    clean(record.cartorio),
    clean(record.recompra),
    clean(record.observacao),
  ]);

  const table = `
    <html>
      <head><meta charset="UTF-8" /></head>
      <body>
        <table border="1">
          <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr></thead>
          <tbody>${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("")}</tbody>
        </table>
      </body>
    </html>
  `;

  const blob = new Blob([table], { type: "application/vnd.ms-excel;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `informacoes-comerciais-carteira-${new Date().toISOString().slice(0, 10)}.xls`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function useCommercialInformation() {
  return useQuery<CommercialRecord[]>({
    queryKey: ["allCommercialInformation"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/company/commercial-information`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error("Erro ao carregar informações comerciais");
      }

      return response.json();
    },
    staleTime: 60 * 1000,
  });
}

export default function CommercialInformationPage() {
  const { data = [], isLoading, isError, error } = useCommercialInformation();
  const [companyFilter, setCompanyFilter] = useState("");
  const [partnerFilter, setPartnerFilter] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  const partners = useMemo(
    () => Array.from(new Set(data.map((record) => record.parceiro).filter(Boolean) as string[])).sort(),
    [data],
  );

  const types = useMemo(
    () => Array.from(new Set(data.map((record) => record.tipo).filter(Boolean) as string[])).sort(),
    [data],
  );

  const filteredRecords = useMemo(() => {
    const companyQuery = normalizeSearch(companyFilter);
    const from = parseFilterDate(dateFrom);
    const to = parseFilterDate(dateTo);

    return data.filter((record) => {
      const companyText = normalizeSearch(`${record.empresa ?? ""} ${record.cnpj ?? ""}`);
      const recordDate = parseBrDate(record.data);
      const matchesCompany = !companyQuery || companyText.includes(companyQuery);
      const matchesPartner = !partnerFilter || record.parceiro === partnerFilter;
      const matchesType = !typeFilter || record.tipo === typeFilter;
      const matchesFrom = !from || (recordDate && recordDate >= from);
      const matchesTo = !to || (recordDate && recordDate <= to);
      return matchesCompany && matchesPartner && matchesType && matchesFrom && matchesTo;
    });
  }, [companyFilter, data, dateFrom, dateTo, partnerFilter, typeFilter]);

  const totalCompanies = useMemo(
    () => new Set(filteredRecords.map((record) => record.cnpj).filter(Boolean)).size,
    [filteredRecords],
  );

  const clearFilters = () => {
    setCompanyFilter("");
    setPartnerFilter("");
    setTypeFilter("");
    setDateFrom("");
    setDateTo("");
  };

  return (
    <div className="mx-auto max-w-[1800px] space-y-5">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <nav className="mb-2 flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <Link href="/" className="transition-colors hover:text-primary">Carteira</Link>
            <span>/</span>
            <span className="font-medium text-grafite dark:text-white">Informações Comerciais</span>
          </nav>
          <h1 className="font-sans text-3xl font-bold text-grafite dark:text-white">Informações Comerciais</h1>
          <p className="mt-1 max-w-2xl text-sm text-gray-500 dark:text-gray-400">
            Visão consolidada das fichas comerciais cadastradas nas páginas das empresas.
          </p>
        </div>
        <button
          type="button"
          onClick={() => exportXls(data)}
          disabled={data.length === 0}
          className="inline-flex h-10 items-center justify-center rounded-xl bg-primary px-4 text-sm font-bold text-white shadow-sm transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
        >
          Exportar XLS
        </button>
      </header>

      <section className="grid grid-cols-1 gap-3 md:grid-cols-3">
        <div className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Registros</p>
          <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{filteredRecords.length}</p>
        </div>
        <div className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Empresas</p>
          <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{totalCompanies}</p>
        </div>
        <div className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
          <p className="text-xs font-bold uppercase tracking-wide text-gray-400">Parceiros</p>
          <p className="mt-2 text-2xl font-bold text-grafite dark:text-white">{partners.length}</p>
        </div>
      </section>

      <section className="rounded-xl border border-border-light bg-surface-light p-4 shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(240px,1.4fr)_minmax(180px,1fr)_minmax(160px,0.8fr)_150px_150px_auto]">
          <label className="block">
            <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Empresa ou CNPJ</span>
            <input
              value={companyFilter}
              onChange={(event) => setCompanyFilter(event.target.value)}
              placeholder="Buscar cedente"
              className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition-colors placeholder:text-gray-300 focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Parceiro</span>
            <select
              value={partnerFilter}
              onChange={(event) => setPartnerFilter(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition-colors focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
            >
              <option value="">Todos</option>
              {partners.map((partner) => (
                <option key={partner} value={partner}>{partner}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Tipo</span>
            <select
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition-colors focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
            >
              <option value="">Todos</option>
              {types.map((type) => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase tracking-wide text-gray-400">De</span>
            <input
              type="date"
              value={dateFrom}
              onChange={(event) => setDateFrom(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition-colors focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
            />
          </label>
          <label className="block">
            <span className="text-xs font-bold uppercase tracking-wide text-gray-400">Até</span>
            <input
              type="date"
              value={dateTo}
              onChange={(event) => setDateTo(event.target.value)}
              className="mt-1 h-10 w-full rounded-lg border border-border-light bg-white px-3 text-sm text-grafite outline-none transition-colors focus:border-primary dark:border-border-dark dark:bg-gray-900 dark:text-white"
            />
          </label>
          <div className="flex items-end">
            <button
              type="button"
              onClick={clearFilters}
              className="h-10 whitespace-nowrap rounded-lg border border-border-light px-3 text-xs font-bold text-gray-500 transition-colors hover:bg-gray-50 hover:text-primary dark:border-border-dark dark:text-gray-300 dark:hover:bg-gray-800"
            >
              Limpar filtros
            </button>
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1600px] border-collapse text-left text-xs">
            <thead className="bg-[#f4ece7] dark:bg-gray-900">
              <tr className="border-b border-border-light dark:border-border-dark">
                {[
                  "Cedente",
                  "CNPJ",
                  "Data",
                  "Tipo",
                  "Parceiro",
                  "Desde",
                  "Última operação",
                  "Valor",
                  "Risco dupl.",
                  "Risco cheque",
                  "Risco comiss.",
                  "Últ. venc.",
                  "Valor venc.",
                  "Limite",
                  "VOP",
                  "Pontual",
                ].map((header) => (
                  <th key={header} className="border-r border-border-light px-3 py-2 font-bold uppercase tracking-wide text-[#26365c] last:border-r-0 dark:border-border-dark dark:text-gray-300">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border-light dark:divide-border-dark">
              {isLoading && (
                <tr>
                  <td colSpan={16} className="px-4 py-10 text-center text-sm text-gray-500">Carregando informações comerciais...</td>
                </tr>
              )}
              {isError && (
                <tr>
                  <td colSpan={16} className="px-4 py-10 text-center text-sm text-red-600">
                    {(error as Error)?.message || "Erro ao carregar informações comerciais"}
                  </td>
                </tr>
              )}
              {!isLoading && !isError && filteredRecords.length === 0 && (
                <tr>
                  <td colSpan={16} className="px-4 py-10 text-center text-sm text-gray-500">
                    Nenhuma informação comercial encontrada.
                  </td>
                </tr>
              )}
              {!isLoading && !isError && filteredRecords.map((record) => (
                <tr key={record.id} className="transition-colors hover:bg-background-light dark:hover:bg-background-dark">
                  <td className="border-r border-border-light px-3 py-2 font-bold text-[#26365c] dark:border-border-dark dark:text-white">
                    {record.cnpj ? (
                      <Link href={`/clients/${record.cnpj}`} className="underline-offset-2 hover:text-primary hover:underline">
                        {clean(record.empresa)}
                      </Link>
                    ) : clean(record.empresa)}
                  </td>
                  <td className="border-r border-border-light px-3 py-2 font-serif text-gray-600 dark:border-border-dark dark:text-gray-300">{formatCnpj(record.cnpj)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.data)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.tipo)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-bold text-[#26365c] dark:border-border-dark dark:text-gray-100">{clean(record.parceiro)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.clienteDesde)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.ultimaOperacaoData)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.ultimaOperacaoValor)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.riscoDuplicata)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.riscoCheque)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.riscoComissaria)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif text-red-600 dark:border-border-dark">{clean(record.vencidosValor)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif text-red-600 dark:border-border-dark">{clean(record.vencidosValorMonetario)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.limite)}</td>
                  <td className="border-r border-border-light px-3 py-2 font-serif dark:border-border-dark">{clean(record.vop)}</td>
                  <td className="px-3 py-2 font-serif">{clean(record.pontual)}{record.pontual ? "%" : ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
