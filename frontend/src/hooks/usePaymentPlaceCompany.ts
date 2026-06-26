import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { PaymentPlaceEntry } from "../types/payment-place";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export type PaymentPlaceCompanySummary = {
  documentNumber: string;
  sacadoCount: number;
  sacadoValue: number;
  cedenteCount: number;
  cedenteValue: number;
  totalCount: number;
  totalValue: number;
  entries: PaymentPlaceEntry[];
  page: number;
  size: number;
  totalPages: number;
  totalFilteredElements: number;
};

export type PaymentPlaceCompanyParams = {
  from?: string;
  to?: string;
  decisao?: string;
  page?: number;
  size?: number;
};

export function usePaymentPlaceCompany(cnpj?: string, params: PaymentPlaceCompanyParams = {}) {
  const cleanCnpj = (cnpj || "").replace(/\D/g, "");
  const { from, to, decisao, page = 0, size = 10 } = params;
  return useQuery<PaymentPlaceCompanySummary | null>({
    queryKey: ["paymentPlaceCompany", cleanCnpj, from ?? "", to ?? "", decisao ?? "", page, size],
    enabled: cleanCnpj.length === 14,
    queryFn: async () => {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const qs = new URLSearchParams();
      if (from) qs.set("from", from);
      if (to) qs.set("to", to);
      if (decisao) qs.set("decisao", decisao);
      qs.set("page", String(page));
      qs.set("size", String(size));
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/empresa/${cleanCnpj}?${qs}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) return null;
      return res.json();
    },
    placeholderData: (prev) => prev,
    // Sempre rebusca ao entrar na página da empresa, para refletir decisões/reaberturas
    // feitas na Praça de Pagamento sem precisar de F5.
    refetchOnMount: "always",
  });
}

/**
 * Desfaz a decisão de um lançamento ("apagar" a análise na página do cliente):
 * o lançamento volta para PENDENTE na Praça de Pagamento (reaberto e destacado).
 */
export function useReopenPaymentPlaceEntry() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, string>({
    mutationFn: async (entryId) => {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/decisao`, {
        method: "DELETE",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || "Falha ao remover a análise do lançamento");
      }
      return res.json();
    },
    onSuccess: () => {
      toast.success("Análise removida — lançamento devolvido à Praça de Pagamento");
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceCompany"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceInconclusivos"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlacePatterns"] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export type PaymentPlaceInconclusivePage = {
  entries: PaymentPlaceEntry[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
};

export function usePaymentPlaceInconclusivos(params: { from?: string; to?: string; page?: number; size?: number } = {}) {
  const { from, to, page = 0, size = 20 } = params;
  return useQuery<PaymentPlaceInconclusivePage>({
    queryKey: ["paymentPlaceInconclusivos", from ?? "", to ?? "", page, size],
    queryFn: async () => {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const qs = new URLSearchParams();
      if (from) qs.set("from", from);
      if (to) qs.set("to", to);
      qs.set("page", String(page));
      qs.set("size", String(size));
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/inconclusivos?${qs}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) throw new Error("Falha ao carregar inconclusivos");
      return res.json();
    },
    placeholderData: (prev) => prev,
    refetchOnMount: "always",
  });
}

// Histórico/biblioteca: busca lançamentos de todos os lotes por texto + data de importação.
export function usePaymentPlaceHistory(params: { q?: string; from?: string; to?: string; page?: number; size?: number; enabled?: boolean } = {}) {
  const { q, from, to, page = 0, size = 20, enabled = true } = params;
  return useQuery<PaymentPlaceInconclusivePage>({
    enabled,
    queryKey: ["paymentPlaceHistory", q ?? "", from ?? "", to ?? "", page, size],
    queryFn: async () => {
      const token = typeof window !== "undefined" ? localStorage.getItem("serasa_token") : null;
      const qs = new URLSearchParams();
      if (q) qs.set("q", q);
      if (from) qs.set("from", from);
      if (to) qs.set("to", to);
      qs.set("page", String(page));
      qs.set("size", String(size));
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/historico?${qs}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) throw new Error("Falha ao carregar histórico");
      return res.json();
    },
    placeholderData: (prev) => prev,
  });
}
