"use client";

import { useState } from "react";
import Icon from "@/components/ui/Icon";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { useCompanyBranches } from "../../../../hooks/usePaymentPlace";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const PREVIEW_LIMIT = 5;

function getAuthHeaders() {
  const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function formatCnpj(cnpj: string) {
  const d = (cnpj || "").replace(/\D/g, "").padStart(14, "0");
  return `${d.slice(0, 2)}.${d.slice(2, 5)}.${d.slice(5, 8)}/${d.slice(8, 12)}-${d.slice(12, 14)}`;
}

export function CompanyBranchesPanel({ cnpj }: { cnpj: string }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [expanded, setExpanded] = useState(false);
  const [enabled, setEnabled] = useState(false);

  const { data, isFetching, isError, error } = useCompanyBranches(cnpj, enabled);

  const createProfile = useMutation({
    mutationFn: async (branchCnpj: string) => {
      const res = await fetch(`${API_BASE_URL}/company/enrich/cnpja/${branchCnpj.replace(/\D/g, "")}`, {
        method: "POST",
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error("Falha ao criar perfil da filial");
      return branchCnpj;
    },
    onSuccess: (branchCnpj) => {
      toast.success("Perfil da filial criado");
      queryClient.invalidateQueries({ queryKey: ["companyBranches", cnpj] });
      router.push(`/clients/${branchCnpj.replace(/\D/g, "")}`);
    },
    onError: (e) => toast.error((e as Error).message),
  });

  const branches = data ?? [];
  // Matriz primeiro, depois filiais.
  const sorted = [...branches].sort((a, b) => Number(b.matriz) - Number(a.matriz));
  const shown = expanded ? sorted : sorted.slice(0, PREVIEW_LIMIT);

  return (
    <div className="mb-6 rounded-xl border border-border-light bg-surface-light shadow-sm dark:border-border-dark dark:bg-surface-dark print:hidden">
      <div className="flex flex-wrap items-center gap-2 border-b border-border-light p-5 dark:border-border-dark">
        <Icon name="account_tree" className="text-primary" />
        <h2 className="font-sans text-base font-bold text-primary">Filiais</h2>
        <span className="text-[11px] text-gray-400">· estabelecimentos ativos da raiz do CNPJ (Receita Federal)</span>
        {branches.length > 0 ? (
          <span className="ml-auto text-xs text-gray-400">{branches.length} ativos</span>
        ) : null}
      </div>

      <div className="p-5">
        {!enabled ? (
          <button
            type="button"
            onClick={() => setEnabled(true)}
            className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-white transition hover:opacity-90"
          >
            <Icon name="travel_explore" size={18} />
            Buscar filiais
          </button>
        ) : isFetching ? (
          <p className="text-sm text-gray-500">Buscando filiais…</p>
        ) : isError ? (
          <p className="text-sm text-gray-500">{(error as Error)?.message ?? "Erro ao buscar filiais"}</p>
        ) : branches.length === 0 ? (
          <p className="text-sm text-gray-500">Nenhuma filial ativa encontrada.</p>
        ) : (
          <div className="space-y-2">
            {shown.map((b) => (
              <div
                key={b.cnpj}
                className="flex flex-wrap items-center gap-3 rounded-lg border border-border-light p-3 dark:border-border-dark"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-sm font-semibold text-grafite dark:text-gray-200">
                      {formatCnpj(b.cnpj)}
                    </span>
                    {b.matriz ? (
                      <span className="rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-bold uppercase text-primary">Matriz</span>
                    ) : (
                      <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-bold uppercase text-gray-500 dark:bg-white/10 dark:text-gray-300">Filial</span>
                    )}
                    {b.nomeFantasia ? (
                      <span className="truncate text-xs text-gray-500">· {b.nomeFantasia}</span>
                    ) : null}
                  </div>
                  <p className="mt-0.5 truncate text-xs text-gray-500">{b.address ?? `${b.municipio ?? "—"}/${b.uf ?? ""}`}</p>
                </div>
                {b.inSystem ? (
                  <button
                    type="button"
                    onClick={() => router.push(`/clients/${b.cnpj.replace(/\D/g, "")}`)}
                    className="rounded-md border border-border-light px-2.5 py-1 text-xs font-semibold text-grafite transition hover:bg-gray-50 dark:border-border-dark dark:text-gray-200 dark:hover:bg-white/5"
                  >
                    Ver perfil
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => createProfile.mutate(b.cnpj)}
                    disabled={createProfile.isPending}
                    className="rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-white transition hover:opacity-90 disabled:opacity-50"
                  >
                    {createProfile.isPending && createProfile.variables === b.cnpj ? "Criando…" : "Criar perfil"}
                  </button>
                )}
              </div>
            ))}

            {sorted.length > PREVIEW_LIMIT ? (
              <button
                type="button"
                onClick={() => setExpanded((v) => !v)}
                className="mt-1 text-xs font-semibold text-primary hover:underline"
              >
                {expanded ? "Ver menos" : `Ver todas (${sorted.length})`}
              </button>
            ) : null}
          </div>
        )}
      </div>
    </div>
  );
}
