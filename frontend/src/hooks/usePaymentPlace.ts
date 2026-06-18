import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { PaymentPlaceBatch, PaymentPlaceBatchDetail, PaymentPlaceEntry } from "../types/payment-place";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders(contentType?: string) {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return {
    ...(contentType ? { "Content-Type": contentType } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

export function usePaymentPlaceBatches() {
  return useQuery<PaymentPlaceBatch[]>({
    queryKey: ["paymentPlaceBatches"],
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        throw new Error("Erro ao carregar lotes de praça de pagamento");
      }
      return response.json();
    },
  });
}

export function usePaymentPlaceBatch(batchId?: string) {
  return useQuery<PaymentPlaceBatchDetail>({
    queryKey: ["paymentPlaceBatch", batchId],
    enabled: Boolean(batchId),
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${batchId}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        throw new Error("Erro ao carregar lançamentos do lote");
      }
      return response.json();
    },
  });
}

export function useImportPaymentPlacePdf() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceBatchDetail, Error, File>({
    mutationFn: async (file) => {
      const formData = new FormData();
      formData.append("file", file);
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/importar`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: formData,
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao importar relatório bancário");
      }
      return response.json();
    },
    onMutate: () => {
      toast.loading("Importando relatório bancário...", { id: "payment-place-import" });
    },
    onSuccess: (data) => {
      toast.success(`${data.batch.totalEntries} lançamentos importados`, {
        id: "payment-place-import",
        duration: 5000,
      });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
      queryClient.setQueryData(["paymentPlaceBatch", data.batch.id], data);
      // Endereço da agência (Bacen) é resolvido em background — refetch para exibir.
      [3000, 8000, 15000].forEach((delay) =>
        setTimeout(() => {
          queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch", data.batch.id] });
        }, delay),
      );
    },
    onError: (error) => {
      toast.error(error.message, { id: "payment-place-import" });
    },
  });
}

export function useDeletePaymentPlaceBatch() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, string>({
    mutationFn: async (batchId) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${batchId}`, {
        method: "DELETE",
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao apagar lote");
      }
    },
    onSuccess: (_, batchId) => {
      toast.success("Lote apagado");
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
      queryClient.removeQueries({ queryKey: ["paymentPlaceBatch", batchId] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useEnrichAgencyBacen(batchId?: string) {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, string>({
    mutationFn: async (entryId) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/agencia-bacen`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao buscar endereço da agência no Bacen");
      }
      return response.json();
    },
    onMutate: () => {
      toast.loading("Consultando agência no Bacen...", { id: "bacen-agency" });
    },
    onSuccess: (updatedEntry) => {
      const found = Boolean(updatedEntry.agencyAddressResolved);
      toast[found ? "success" : "info"](
        found ? "Endereço da agência atualizado" : "Agência não localizada no cadastro Bacen",
        { id: "bacen-agency" },
      );
      if (batchId) {
        queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", batchId], (current) => {
          if (!current) return current;
          return {
            ...current,
            entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? updatedEntry : entry)),
          };
        });
      }
    },
    onError: (error) => {
      toast.error(error.message, { id: "bacen-agency" });
    },
  });
}

export function useArchivePaymentPlaceBatch() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceBatch, Error, { batchId: string; archived: boolean }>({
    mutationFn: async ({ batchId, archived }) => {
      const action = archived ? "arquivar" : "desarquivar";
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${batchId}/${action}`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao arquivar lote");
      }
      return response.json();
    },
    onSuccess: (_data, { archived }) => {
      toast.success(archived ? "Lote arquivado" : "Lote restaurado");
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useAnalyzeWithAi(batchId?: string) {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, string>({
    mutationFn: async (entryId) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/analise-ia`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        let msg = "Falha ao analisar com IA";
        try {
          const j = await response.json();
          msg = j.error || msg;
        } catch {
          /* ignore */
        }
        throw new Error(msg);
      }
      return response.json();
    },
    onMutate: () => {
      toast.loading("Analisando com Gemini...", { id: "ai-analysis" });
    },
    onSuccess: (updatedEntry) => {
      toast.success("Análise da IA concluída", { id: "ai-analysis" });
      if (batchId) {
        queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", batchId], (current) => {
          if (!current) return current;
          return {
            ...current,
            entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? updatedEntry : entry)),
          };
        });
      }
    },
    onError: (error) => {
      toast.error(error.message, { id: "ai-analysis" });
    },
  });
}

export function useBulkDecidePaymentPlace(batchId?: string) {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry[], Error, { entryId: string; decision: "SACADO" | "CEDENTE" }[]>({
    mutationFn: async (decisions) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/decisoes`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ decisions }),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao aplicar decisões em massa");
      }
      return response.json();
    },
    onSuccess: (updated) => {
      toast.success(`${updated.length} decisões aplicadas`);
      if (batchId) {
        queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", batchId], (current) => {
          if (!current) return current;
          const byId = new Map(updated.map((e) => [e.id, e]));
          return {
            ...current,
            entries: current.entries.map((entry) => byId.get(entry.id) ?? entry),
          };
        });
      }
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useDecidePaymentPlaceEntry(batchId?: string) {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, { entryId: string; decision: "SACADO" | "CEDENTE"; notes?: string }>({
    mutationFn: async ({ entryId, decision, notes }) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/decisao`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ decision, notes }),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao salvar decisão");
      }
      return response.json();
    },
    onSuccess: (updatedEntry) => {
      toast.success("Decisão salva");
      if (batchId) {
        queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", batchId], (current) => {
          if (!current) return current;
          return {
            ...current,
            entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? updatedEntry : entry)),
          };
        });
      }
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}
