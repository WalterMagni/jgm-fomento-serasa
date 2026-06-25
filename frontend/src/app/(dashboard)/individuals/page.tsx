"use client";

import React, { useState } from "react";
import Icon from "@/components/ui/Icon";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEnrichPersonSerasa, usePersonProfiles } from "../../../hooks/usePersonProfile";
import { formatCpf, totalNegativesFromPF } from "../../../types/person-analysis";

function formatDate(raw: string | undefined) {
  if (!raw) return "-";
  const d = new Date(raw);
  return isNaN(d.getTime()) ? raw : d.toLocaleDateString("pt-BR");
}

export default function IndividualsPage() {
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);
  const [searchTimeout, setSearchTimeout] = useState<ReturnType<typeof setTimeout> | null>(null);
  const [quickCpf, setQuickCpf] = useState("");

  const { data, isLoading, isError } = usePersonProfiles(page, debouncedSearch);
  const cleanQuickCpf = quickCpf.replace(/\D/g, "").slice(0, 11);
  const { mutate: consultPerson, isPending: isConsultingPerson } = useEnrichPersonSerasa(cleanQuickCpf, () => {
    router.push(`/individuals/${cleanQuickCpf}`);
  });

  function formatCpfInput(value: string) {
    const digits = value.replace(/\D/g, "").slice(0, 11);
    if (digits.length <= 3) return digits;
    if (digits.length <= 6) return digits.replace(/^(\d{3})(\d+)/, "$1.$2");
    if (digits.length <= 9) return digits.replace(/^(\d{3})(\d{3})(\d+)/, "$1.$2.$3");
    return digits.replace(/^(\d{3})(\d{3})(\d{3})(\d{0,2}).*/, "$1.$2.$3-$4");
  }

  function handleSearchChange(value: string) {
    setSearch(value);
    if (searchTimeout) clearTimeout(searchTimeout);
    const t = setTimeout(() => {
      setDebouncedSearch(value);
      setPage(0);
    }, 350);
    setSearchTimeout(t);
  }

  const content = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;
  const canConsultCpf = cleanQuickCpf.length === 11;

  return (
    <div className="max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-8">
        <div>
          <h1 className="text-2xl font-heading font-bold text-grafite dark:text-white">
            Pessoas Físicas
          </h1>
          <p className="text-sm text-gray-500 font-sans mt-0.5">
            {totalElements > 0
              ? `${totalElements} CPF${totalElements !== 1 ? "s" : ""} consultado${totalElements !== 1 ? "s" : ""}`
              : "Nenhuma consulta realizada ainda"}
          </p>
        </div>
      </div>

      {/* Quick consult */}
      <div className="mb-6 rounded-2xl border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark p-5 shadow-sm">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
          <div>
            <h2 className="text-base font-heading font-bold text-grafite dark:text-white">
              Consulta avulsa de Pessoa Física
            </h2>
            <p className="text-sm text-gray-500 font-sans mt-1">
              Consulte um CPF diretamente, sem precisar entrar pela página de uma empresa.
            </p>
          </div>
          <div className="text-xs font-sans font-bold text-[#E4006F] bg-[#E4006F]/10 border border-[#E4006F]/20 rounded-full px-3 py-1 self-start">
            Custo estimado Serasa PF: R$ 15,52
          </div>
        </div>
        <div className="mt-4 flex flex-col sm:flex-row gap-3">
          <input
            type="text"
            inputMode="numeric"
            placeholder="Digite o CPF para consultar"
            value={quickCpf}
            onChange={(e) => setQuickCpf(formatCpfInput(e.target.value))}
            className="flex-1 px-4 py-3 rounded-xl border border-border-light dark:border-border-dark bg-white dark:bg-gray-900 text-grafite dark:text-white font-sans text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[#E4006F]/25 focus:border-[#E4006F] transition-all"
          />
          <button
            onClick={() => consultPerson()}
            disabled={!canConsultCpf || isConsultingPerson}
            className="inline-flex items-center justify-center gap-2 px-5 py-3 rounded-xl font-sans font-bold text-sm text-white disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            style={{ background: "#E4006F" }}
          >
            <Icon name={isConsultingPerson ? "hourglass_empty" : "person_search"} className="text-base" />
            {isConsultingPerson ? "Consultando..." : "Consultar PF"}
          </button>
        </div>
      </div>

      {/* Search */}
      <div className="relative mb-6">
        <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xl pointer-events-none" />
        <input
          type="text"
          placeholder="Buscar por CPF ou nome..."
          value={search}
          onChange={(e) => handleSearchChange(e.target.value)}
          className="w-full pl-10 pr-4 py-3 rounded-xl border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark text-grafite dark:text-white font-sans text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-all"
        />
      </div>

      {/* Table */}
      <div className="bg-surface-light dark:bg-surface-dark rounded-2xl shadow-sm border border-border-light dark:border-border-dark overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Icon name="refresh" className="text-4xl text-gray-300 dark:text-gray-600 animate-spin" />
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center py-20 gap-3">
            <Icon name="error_outline" className="text-4xl text-red-400" />
            <p className="text-sm text-gray-500 font-sans">Erro ao carregar dados. Tente novamente.</p>
          </div>
        ) : content.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 gap-4">
            <Icon name="person_search" className="text-5xl text-gray-300 dark:text-gray-600" />
            <div className="text-center">
              <p className="font-sans font-semibold text-gray-500 dark:text-gray-400">
                {debouncedSearch ? "Nenhum resultado encontrado" : "Nenhuma pessoa física consultada"}
              </p>
              <p className="text-xs text-gray-400 mt-1 font-sans">
                {debouncedSearch
                  ? "Tente outro CPF ou nome"
                  : "Use a consulta avulsa acima ou consulte a partir da página de uma empresa"}
              </p>
            </div>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-border-light dark:border-border-dark bg-gray-50 dark:bg-gray-900/30">
                <th className="text-left px-6 py-4 text-xs font-sans font-bold text-gray-400 uppercase tracking-wide">Nome</th>
                <th className="text-left px-6 py-4 text-xs font-sans font-bold text-gray-400 uppercase tracking-wide">CPF</th>
                <th className="text-center px-6 py-4 text-xs font-sans font-bold text-gray-400 uppercase tracking-wide">Restrições</th>
                <th className="text-left px-6 py-4 text-xs font-sans font-bold text-gray-400 uppercase tracking-wide hidden md:table-cell">Consultado em</th>
                <th className="px-6 py-4" />
              </tr>
            </thead>
            <tbody className="divide-y divide-border-light dark:divide-border-dark">
              {content.map((person) => {
                const negCount = totalNegativesFromPF(person);
                const hasNeg = negCount > 0;
                return (
                  <tr
                    key={person.id}
                    className="hover:bg-gray-50 dark:hover:bg-gray-800/30 transition-colors"
                  >
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className={`w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm font-sans flex-shrink-0 ${
                          hasNeg
                            ? "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
                            : "bg-primary/10 text-primary"
                        }`}>
                          {(person.personName ?? "??").substring(0, 2).toUpperCase()}
                        </div>
                        <span className="font-sans font-medium text-grafite dark:text-white text-sm truncate max-w-[200px]">
                          {person.personName ?? "—"}
                        </span>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span className="font-mono text-sm text-gray-600 dark:text-gray-300">
                        {formatCpf(person.cpf)}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-center">
                      {hasNeg ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-sans font-bold bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400 border border-red-200 dark:border-red-800">
                          <Icon name="warning" size={12} />
                          {negCount}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-sans font-bold bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400 border border-green-200 dark:border-green-800">
                          <Icon name="check_circle" size={12} />
                          Limpo
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 hidden md:table-cell">
                      <span className="text-sm text-gray-500 font-sans">{formatDate(person.consultaEm)}</span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <Link
                        href={`/individuals/${person.cpf}`}
                        className="inline-flex items-center gap-1 text-sm font-sans font-medium text-primary hover:text-primary/80 transition-colors"
                      >
                        Ver
                        <Icon name="chevron_right" className="text-base" />
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-border-light dark:border-border-dark">
            <span className="text-xs text-gray-400 font-sans">
              Página {page + 1} de {totalPages}
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="p-2 rounded-lg border border-border-light dark:border-border-dark text-gray-500 dark:text-gray-400 disabled:opacity-40 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                <Icon name="chevron_left" className="text-base" />
              </button>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-2 rounded-lg border border-border-light dark:border-border-dark text-gray-500 dark:text-gray-400 disabled:opacity-40 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
              >
                <Icon name="chevron_right" className="text-base" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
