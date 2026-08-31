"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export type SharedShareholder = {
  name: string;
  /** CPF completo, quando conhecido. Nulo quando só temos a máscara da Receita. */
  document: string | null;
  documentMask: string;
  qualification: string | null;
  entryDate: string | null;
};

export type RelatedCompany = {
  cnpjRaiz: string;
  /** CNPJ da empresa cadastrada no portal; nulo quando ela não tem perfil. */
  cnpj: string | null;
  /** CNPJ da matriz segundo a Receita — existe mesmo sem perfil no portal. */
  receitaCnpj: string | null;
  companyName: string | null;
  companyStatus: string | null;
  companyStatusLabel: string | null;
  irregular: boolean;
  inSystem: boolean;
  alreadyLinked: boolean;
  sharedShareholders: SharedShareholder[];
};

function getAuthHeaders() {
  const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/**
 * Máscara da Receita a partir do CPF completo: três asteriscos, dígitos 4..9, dois asteriscos.
 * Espelha `CpfMask.fromCpf` no backend — é o que permite casar um CPF vindo do QSA da Serasa
 * com um sócio que a base gratuita só conhece pela máscara.
 */
export function cpfToReceitaMask(cpf: string | null | undefined): string | null {
  const digits = (cpf ?? "").replace(/\D/g, "");
  if (digits.length !== 11) return null;
  return `***${digits.slice(3, 9)}**`;
}

/**
 * Grupo econômico da empresa (empresas ligadas por sócio em comum), com um índice por pessoa.
 *
 * <p>Uma única chamada serve tanto o chip "+N empresas" no QSA quanto a lista dentro do modal
 * do sócio — evita uma requisição por pessoa.</p>
 */
export function useEconomicGroup(cnpj: string) {
  const query = useQuery<RelatedCompany[]>({
    queryKey: ["companyEconomicGroup", cnpj],
    enabled: Boolean(cnpj),
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/grupo-economico`, { headers: getAuthHeaders() });
      // 503 = base da Receita fora do ar. Fonte auxiliar: silencia em vez de quebrar a página.
      if (res.status === 503) return [];
      if (!res.ok) throw new Error("Erro ao carregar grupo econômico");
      return res.json();
    },
  });

  const related = useMemo(() => query.data ?? [], [query.data]);

  /**
   * Empresas em que a pessoa figura, casando pelo CPF completo ou pela máscara — o vínculo
   * pode ter vindo da base gratuita, onde o CPF completo não existe.
   */
  const companiesOf = useMemo(() => {
    const byKey = new Map<string, RelatedCompany[]>();
    const push = (key: string | null, company: RelatedCompany) => {
      if (!key) return;
      const list = byKey.get(key) ?? [];
      if (!list.includes(company)) list.push(company);
      byKey.set(key, list);
    };
    for (const company of related) {
      for (const person of company.sharedShareholders) {
        push(person.document ? person.document.replace(/\D/g, "") : null, company);
        push(person.documentMask, company);
      }
    }
    return (cpf: string | null | undefined): RelatedCompany[] => {
      const digits = (cpf ?? "").replace(/\D/g, "");
      if (!digits) return [];
      return byKey.get(digits) ?? byKey.get(cpfToReceitaMask(digits) ?? "") ?? [];
    };
  }, [related]);

  return { related, companiesOf, isLoading: query.isLoading };
}
