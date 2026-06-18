import { useQuery } from "@tanstack/react-query";
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
  });
}
