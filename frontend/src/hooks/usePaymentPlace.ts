import { useMutation, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CompanyBranch, PaymentPlaceBatch, PaymentPlaceBatchDetail, PaymentPlaceBatchIndicators, PaymentPlaceEntry, PaymentPlacePattern, PaymentPlacePatternsPage } from "../types/payment-place";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

export type PaymentPlaceAttachment = {
  id: string;
  fileName?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
  createdByName?: string | null;
  createdAt?: string | null;
};

export function useEntryAttachments(entryId?: string | null, enabled = true) {
  return useQuery<PaymentPlaceAttachment[]>({
    queryKey: ["entryAttachments", entryId ?? ""],
    enabled: Boolean(entryId) && enabled,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/anexos`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Falha ao carregar anexos");
      return res.json();
    },
  });
}

export function useUploadEntryAttachments() {
  const queryClient = useQueryClient();
  return useMutation<PaymentPlaceAttachment[], Error, { entryId: string; files: File[] }>({
    mutationFn: async ({ entryId, files }) => {
      const fd = new FormData();
      files.forEach((f) => fd.append("files", f));
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/anexos`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: fd,
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao enviar anexo"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["entryAttachments", vars.entryId] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceInconclusivos"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceHistory"] });
      toast.success("Anexo(s) enviado(s)");
    },
    onError: (error) => toast.error(error.message),
  });
}

export function useDeleteEntryAttachment() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, { entryId: string; attachmentId: string }>({
    mutationFn: async ({ entryId, attachmentId }) => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/anexos/${attachmentId}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error("Falha ao apagar anexo");
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["entryAttachments", vars.entryId] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceInconclusivos"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceHistory"] });
      toast.success("Anexo removido");
    },
    onError: (error) => toast.error(error.message),
  });
}

// Busca o anexo (com auth) e devolve um object URL para visualização/abertura.
export async function fetchAttachmentObjectUrl(entryId: string, attachmentId: string): Promise<string> {
  const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/anexos/${attachmentId}`, {
    headers: getAuthHeaders(),
  });
  if (!res.ok) throw new Error("Falha ao abrir anexo");
  const blob = await res.blob();
  return URL.createObjectURL(blob);
}

// Observação persistente de cedente/sacado (por documento) — vale para todos os títulos da parte.
export function usePartyNote(partyType: "CEDENTE" | "SACADO", document?: string | null) {
  const hasDoc = Boolean(document && document.replace(/\D/g, ""));
  return useQuery<{ note: string | null }>({
    queryKey: ["partyNote", partyType, document ?? ""],
    enabled: hasDoc,
    queryFn: async () => {
      const qs = new URLSearchParams({ partyType, document: document ?? "" });
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/observacao-parte?${qs}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Falha ao carregar observação");
      return res.json();
    },
  });
}

export function useSavePartyNote() {
  const queryClient = useQueryClient();
  return useMutation<{ note: string | null }, Error, { partyType: "CEDENTE" | "SACADO"; document: string; note: string }>({
    mutationFn: async ({ partyType, document, note }) => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/observacao-parte`, {
        method: "PUT",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ partyType, document, note }),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao salvar observação"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["partyNote", vars.partyType, vars.document] });
      toast.success("Observação salva");
    },
    onError: (error) => toast.error(error.message),
  });
}

// Padrões aprendidos (par cedente×sacado) — tela /praca-pagamento/padroes.
export function usePaymentPlacePatterns(params: { q?: string; page?: number; size?: number }) {
  const { q = "", page = 0, size = 20 } = params;
  return useQuery<PaymentPlacePatternsPage>({
    queryKey: ["paymentPlacePatterns", q, page, size],
    queryFn: async () => {
      const qs = new URLSearchParams({ q, page: String(page), size: String(size) });
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/padroes?${qs}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Erro ao carregar padrões aprendidos");
      return res.json();
    },
  });
}

export function useBulkReopenPaymentPlace() {
  const queryClient = useQueryClient();
  return useMutation<PaymentPlaceEntry[], Error, string[]>({
    mutationFn: async (entryIds) => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/reaberturas`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ entryIds }),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao reabrir decisões"));
      return res.json();
    },
    onSuccess: (updated) => {
      toast.success(`${updated.length} decisão(ões) reaberta(s)`);
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlacePatterns"] });
    },
    onError: (error) => toast.error(error.message),
  });
}

export function useRecomputePatterns() {
  const queryClient = useQueryClient();
  return useMutation<{ recomputedPairs: number }, Error, void>({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/padroes/recalcular`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao recalcular padrões"));
      return res.json();
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ["paymentPlacePatterns"] });
      toast.success(`${data.recomputedPairs} pares recalculados`);
    },
    onError: (error) => toast.error(error.message),
  });
}

export function useTogglePatternLock() {
  const queryClient = useQueryClient();
  return useMutation<PaymentPlacePattern, Error, { patternId: string; locked: boolean; decision?: "SACADO" | "CEDENTE" }>({
    mutationFn: async ({ patternId, locked, decision }) => {
      const res = await fetch(`${API_BASE_URL}/praca-pagamento/padroes/${patternId}/travar`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ locked, decision }),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao travar padrão"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["paymentPlacePatterns"] });
      toast.success(vars.locked ? "Padrão travado" : "Padrão destravado");
    },
    onError: (error) => toast.error(error.message),
  });
}

export function useCompanyBranches(cnpj?: string, enabled = false) {
  return useQuery<CompanyBranch[]>({
    queryKey: ["companyBranches", cnpj],
    enabled: Boolean(cnpj) && enabled,
    staleTime: 1000 * 60 * 30,
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/filiais/${cnpj}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (response.status === 503) {
        throw new Error("Consulta de filiais indisponível (BigQuery não configurado)");
      }
      if (!response.ok) {
        throw new Error("Erro ao carregar filiais");
      }
      return response.json();
    },
  });
}

// Extrai uma mensagem legível de uma resposta de erro (JSON {message|error} ou texto).
async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  const text = await response.text().catch(() => "");
  if (!text) return fallback;
  try {
    const json = JSON.parse(text);
    return json.message || json.error || fallback;
  } catch {
    return text.length > 200 ? fallback : text;
  }
}

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

// Busca os detalhes de vários lotes em paralelo e mescla os lançamentos.
export function usePaymentPlaceBatchDetails(batchIds: string[]) {
  const results = useQueries({
    queries: batchIds.map((id) => ({
      queryKey: ["paymentPlaceBatch", id],
      enabled: Boolean(id),
      queryFn: async () => {
        const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${id}`, {
          headers: getAuthHeaders("application/json"),
        });
        if (!response.ok) {
          throw new Error("Erro ao carregar lançamentos do lote");
        }
        return response.json() as Promise<PaymentPlaceBatchDetail>;
      },
    })),
  });
  const details = results.map((r) => r.data).filter(Boolean) as PaymentPlaceBatchDetail[];
  return {
    details,
    isLoading: results.some((r) => r.isLoading),
    isFetching: results.some((r) => r.isFetching),
  };
}

// Indicadores agregados de todos os lotes ativos (soma contagens, recalcula %).
export function usePaymentPlaceIndicatorsAll(batchIds: string[]) {
  const results = useQueries({
    queries: batchIds.map((id) => ({
      queryKey: ["paymentPlaceIndicators", id],
      enabled: Boolean(id),
      queryFn: async () => {
        const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${id}/indicadores`, {
          headers: getAuthHeaders("application/json"),
        });
        if (!response.ok) {
          throw new Error("Erro ao carregar indicadores da praça de pagamento");
        }
        return response.json() as Promise<PaymentPlaceBatchIndicators>;
      },
    })),
  });
  const list = results.map((r) => r.data).filter(Boolean) as PaymentPlaceBatchIndicators[];
  const isLoading = results.some((r) => r.isLoading);
  if (list.length === 0) return { data: null as null | Omit<PaymentPlaceBatchIndicators, "batchId" | "fileName" | "topRecurringBankAgencies" | "topDivergentBankAgencies">, isLoading };
  const sum = (f: keyof PaymentPlaceBatchIndicators) => list.reduce((acc, i) => acc + (Number(i[f]) || 0), 0);
  const pct = (n: number, d: number) => (d ? (n / d) * 100 : 0);
  const totalEntries = sum("totalEntries");
  const locatedAgencyCount = sum("locatedAgencyCount");
  const lowReliabilityCount = sum("lowReliabilityCount");
  const comparableDecisionCount = sum("comparableDecisionCount");
  const agreementCount = sum("agreementCount");
  const disagreementCount = sum("disagreementCount");
  return {
    data: {
      totalEntries,
      locatedAgencyCount,
      locatedAgencyPct: pct(locatedAgencyCount, totalEntries),
      lowReliabilityCount,
      lowReliabilityPct: pct(lowReliabilityCount, totalEntries),
      comparableDecisionCount,
      agreementCount,
      agreementPct: pct(agreementCount, comparableDecisionCount),
      disagreementCount,
      disagreementPct: pct(disagreementCount, comparableDecisionCount),
    },
    isLoading,
  };
}

export function usePaymentPlaceIndicators(batchId?: string) {
  return useQuery<PaymentPlaceBatchIndicators>({
    queryKey: ["paymentPlaceIndicators", batchId],
    enabled: Boolean(batchId),
    queryFn: async () => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lotes/${batchId}/indicadores`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        throw new Error("Erro ao carregar indicadores da praça de pagamento");
      }
      return response.json();
    },
  });
}

export function useImportPaymentPlacePdf() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceBatchDetail, Error, File | { file: File; referenceDate?: string }>({
    mutationFn: async (input) => {
      const file = input instanceof File ? input : input.file;
      const referenceDate = input instanceof File ? undefined : input.referenceDate;
      const formData = new FormData();
      formData.append("file", file);
      if (referenceDate) formData.append("referenceDate", referenceDate);
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/importar`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: formData,
      });
      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Falha ao importar relatório bancário"));
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
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", data.batch.id] });
      // Endereço da agência (Bacen) é resolvido em background — refetch para exibir.
      [3000, 8000, 15000].forEach((delay) =>
        setTimeout(() => {
          queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch", data.batch.id] });
          queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", data.batch.id] });
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
      queryClient.removeQueries({ queryKey: ["paymentPlaceIndicators", batchId] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useEnrichAgencyBacen() {
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
      queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", updatedEntry.batchId], (current) => {
        if (!current) return current;
        return {
          ...current,
          // Preserva attachmentCount (o endpoint de mutação não devolve esse campo).
          entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? { ...updatedEntry, attachmentCount: entry.attachmentCount ?? updatedEntry.attachmentCount } : entry)),
        };
      });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", updatedEntry.batchId] });
    },
    onError: (error) => {
      toast.error(error.message, { id: "bacen-agency" });
    },
  });
}

export function useEnrichPayerCnpj() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, string>({
    mutationFn: async (entryId) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/cnpj-sacado`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
      });
      if (!response.ok) {
        const text = await response.text().catch(() => "");
        throw new Error(text || "Falha ao consultar o CNPJ do sacado");
      }
      return response.json();
    },
    onMutate: () => {
      toast.loading("Consultando CNPJ do sacado...", { id: "cnpj-sacado" });
    },
    onSuccess: (updatedEntry) => {
      const found = Boolean(updatedEntry.payerAddress);
      toast[found ? "success" : "info"](
        found ? "Endereço do sacado atualizado e distâncias recalculadas" : "CNPJ do sacado consultado",
        { id: "cnpj-sacado" },
      );
      queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", updatedEntry.batchId], (current) => {
        if (!current) return current;
        return {
          ...current,
          // Preserva attachmentCount (o endpoint de mutação não devolve esse campo).
          entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? { ...updatedEntry, attachmentCount: entry.attachmentCount ?? updatedEntry.attachmentCount } : entry)),
        };
      });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", updatedEntry.batchId] });
    },
    onError: (error) => {
      toast.error(error.message, { id: "cnpj-sacado" });
    },
  });
}

export class CompanyNotInPortfolioError extends Error {
  cnpj: string;
  constructor(cnpj: string) {
    super(`CNPJ ${cnpj} não está na carteira`);
    this.cnpj = cnpj;
    this.name = "CompanyNotInPortfolioError";
  }
}

export function useLinkCedenteCnpj() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, { entryId: string; cnpj: string; create?: boolean }>({
    mutationFn: async ({ entryId, cnpj, create }) => {
      const response = await fetch(`${API_BASE_URL}/praca-pagamento/lancamentos/${entryId}/cnpj-cedente`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ cnpj, create: Boolean(create) }),
      });
      if (response.status === 409) {
        const data = await response.json().catch(() => ({}));
        throw new CompanyNotInPortfolioError(data.message || cnpj);
      }
      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || "Falha ao vincular CNPJ do cedente");
      }
      return response.json();
    },
    onSuccess: () => {
      toast.success("Cedente vinculado — todos os títulos do código foram atualizados");
      // Vários lançamentos do mesmo código mudaram → revalida listagens inteiras.
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatch"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceBatches"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceCompany"] });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators"] });
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
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators"] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useAnalyzeWithAi() {
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
      queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", updatedEntry.batchId], (current) => {
        if (!current) return current;
        return {
          ...current,
          // Preserva attachmentCount (o endpoint de mutação não devolve esse campo).
          entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? { ...updatedEntry, attachmentCount: entry.attachmentCount ?? updatedEntry.attachmentCount } : entry)),
        };
      });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", updatedEntry.batchId] });
    },
    onError: (error) => {
      toast.error(error.message, { id: "ai-analysis" });
    },
  });
}

export function useBulkDecidePaymentPlace() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry[], Error, { entryId: string; decision: "SACADO" | "CEDENTE" | "INCONCLUSIVO" }[]>({
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
      const byBatch = new Map<string, PaymentPlaceEntry[]>();
      updated.forEach((e) => {
        const arr = byBatch.get(e.batchId) ?? [];
        arr.push(e);
        byBatch.set(e.batchId, arr);
      });
      byBatch.forEach((list, bId) => {
        queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", bId], (current) => {
          if (!current) return current;
          const byId = new Map(list.map((e) => [e.id, e]));
          return {
            ...current,
            entries: current.entries.map((entry) => {
              const u = byId.get(entry.id);
              return u ? { ...u, attachmentCount: entry.attachmentCount ?? u.attachmentCount } : entry;
            }),
          };
        });
        queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", bId] });
      });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}

export function useDecidePaymentPlaceEntry() {
  const queryClient = useQueryClient();

  return useMutation<PaymentPlaceEntry, Error, { entryId: string; decision: "SACADO" | "CEDENTE" | "INCONCLUSIVO"; notes?: string }>({
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
      queryClient.setQueryData<PaymentPlaceBatchDetail>(["paymentPlaceBatch", updatedEntry.batchId], (current) => {
        if (!current) return current;
        return {
          ...current,
          // Preserva attachmentCount (o endpoint de mutação não devolve esse campo).
          entries: current.entries.map((entry) => (entry.id === updatedEntry.id ? { ...updatedEntry, attachmentCount: entry.attachmentCount ?? updatedEntry.attachmentCount } : entry)),
        };
      });
      queryClient.invalidateQueries({ queryKey: ["paymentPlaceIndicators", updatedEntry.batchId] });
    },
    onError: (error) => {
      toast.error(error.message);
    },
  });
}
