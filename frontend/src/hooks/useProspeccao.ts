import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  CanalContato,
  DocumentoTipo,
  EstagioProspeccao,
  MotivoRecusa,
  Prospeccao,
  ProspeccaoArquivo,
  ProspeccaoDetalhe,
  ProspeccaoResumo,
  StatusDocumento,
} from "../types/prospeccao";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

const LISTA_KEY = ["prospeccao"] as const;
const RESUMO_KEY = ["prospeccaoResumo"] as const;

function getAuthHeaders(contentType?: string) {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return {
    ...(contentType ? { "Content-Type": contentType } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/**
 * A mensagem do backend é o que a tela mostra — em 422 ela lista os documentos que faltam,
 * e em 409 diz quem já assumiu o card. Cair num texto genérico perderia a informação útil.
 */
async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  const text = await response.text().catch(() => "");
  if (!text) return fallback;
  try {
    const json = JSON.parse(text);
    return json.message || json.error || fallback;
  } catch {
    return text.length > 300 ? fallback : text;
  }
}

export function useProspeccoes(filtros?: { estagio?: EstagioProspeccao; comercialId?: string; apenasAtrasados?: boolean }) {
  const params = new URLSearchParams();
  if (filtros?.estagio) params.set("estagio", filtros.estagio);
  if (filtros?.comercialId) params.set("comercialId", filtros.comercialId);
  if (filtros?.apenasAtrasados) params.set("apenasAtrasados", "true");
  const query = params.toString();

  return useQuery<Prospeccao[]>({
    queryKey: [...LISTA_KEY, query],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao${query ? `?${query}` : ""}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao carregar a esteira"));
      return res.json();
    },
    staleTime: 30 * 1000,
  });
}

export function useProspeccaoResumo() {
  return useQuery<ProspeccaoResumo>({
    queryKey: RESUMO_KEY,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/resumo`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Falha ao carregar o resumo");
      return res.json();
    },
    staleTime: 30 * 1000,
  });
}

/**
 * Mapa de transições válidas, vindo do backend.
 *
 * <p>Evita reimplementar a máquina de estados aqui: o kanban usa isso para saber em quais colunas
 * um card pode ser soltado, e duas cópias da regra divergiriam.</p>
 */
export function useEstagios() {
  return useQuery<Record<string, EstagioProspeccao[]>>({
    queryKey: ["prospeccaoEstagios"],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/estagios`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Falha ao carregar os estágios");
      return res.json();
    },
    staleTime: Infinity,
  });
}

export function useProspeccaoDetalhe(id?: string | null) {
  return useQuery<ProspeccaoDetalhe>({
    queryKey: ["prospeccaoDetalhe", id ?? ""],
    enabled: Boolean(id),
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${id}`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao carregar o card"));
      return res.json();
    },
  });
}

/** Invalida lista, resumo e o card aberto: o kanban mostra contagem e progresso juntos. */
function useInvalidar() {
  const queryClient = useQueryClient();
  return (id?: string) => {
    queryClient.invalidateQueries({ queryKey: LISTA_KEY });
    queryClient.invalidateQueries({ queryKey: RESUMO_KEY });
    if (id) queryClient.invalidateQueries({ queryKey: ["prospeccaoDetalhe", id] });
  };
}

export function useCriarProspeccao() {
  const invalidar = useInvalidar();
  return useMutation<Prospeccao, Error, { cnpj: string; razaoSocial: string; comercialId?: string; comercialNome?: string }>({
    mutationFn: async body => {
      const res = await fetch(`${API_BASE_URL}/prospeccao`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao abrir prospecção"));
      return res.json();
    },
    onSuccess: () => {
      invalidar();
      toast.success("Prospecção aberta");
    },
    onError: error => toast.error(error.message),
  });
}

export function useAssumirProspeccao() {
  const invalidar = useInvalidar();
  return useMutation<Prospeccao, Error, string>({
    mutationFn: async id => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${id}/assumir`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
      });
      // 409 aqui significa que outra analista pegou primeiro — a mensagem diz quem.
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao assumir a análise"));
      return res.json();
    },
    onSuccess: card => {
      invalidar(card.id);
      toast.success("Análise assumida");
    },
    onError: error => toast.error(error.message),
  });
}

export function useTransicionarProspeccao() {
  const invalidar = useInvalidar();
  return useMutation<
    Prospeccao,
    Error,
    { id: string; estagio: EstagioProspeccao; motivoRecusa?: MotivoRecusa; observacao?: string }
  >({
    mutationFn: async ({ id, ...body }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${id}/estagio`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Não foi possível mover o card"));
      return res.json();
    },
    onSuccess: card => invalidar(card.id),
    // O card volta para a coluna de origem e o motivo aparece — em 422 ele lista o que falta.
    onError: error => toast.error(error.message, { duration: 6000 }),
  });
}

export function useReabrirProspeccao() {
  const invalidar = useInvalidar();
  return useMutation<Prospeccao, Error, { id: string; texto: string }>({
    mutationFn: async ({ id, texto }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${id}/reabrir`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ texto }),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao reabrir"));
      return res.json();
    },
    onSuccess: card => {
      invalidar(card.id);
      toast.success("Card reaberto em triagem");
    },
    onError: error => toast.error(error.message),
  });
}

export function useRegistrarEvento() {
  const invalidar = useInvalidar();
  return useMutation<unknown, Error, { id: string; canal?: CanalContato | null; texto: string }>({
    mutationFn: async ({ id, canal, texto }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${id}/eventos`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify({ canal: canal ?? null, texto }),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao registrar"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      invalidar(vars.id);
      toast.success(vars.canal ? "Cobrança registrada" : "Nota registrada");
    },
    onError: error => toast.error(error.message),
  });
}

export function useAtualizarDocumento() {
  const invalidar = useInvalidar();
  return useMutation<
    unknown,
    Error,
    { cardId: string; documentoId: string; status: StatusDocumento; observacao?: string; motivo?: string }
  >({
    mutationFn: async ({ documentoId, ...body }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/documentos/${documentoId}`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao atualizar o documento"));
      return res.json();
    },
    onSuccess: (_data, vars) => invalidar(vars.cardId),
    onError: error => toast.error(error.message),
  });
}

export function useDefinirSocio() {
  const invalidar = useInvalidar();
  return useMutation<unknown, Error, { cardId: string; socioNome: string; ativo: boolean; motivo?: string }>({
    mutationFn: async ({ cardId, ...body }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/${cardId}/socios`, {
        method: "PATCH",
        headers: getAuthHeaders("application/json"),
        body: JSON.stringify(body),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao atualizar o sócio"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      invalidar(vars.cardId);
      toast.success(vars.ativo ? "Sócio reativado" : "Sócio fora do checklist");
    },
    onError: error => toast.error(error.message),
  });
}

export function useEnviarArquivo() {
  const invalidar = useInvalidar();
  return useMutation<ProspeccaoArquivo, Error, { cardId: string; documentoId: string; file: File }>({
    mutationFn: async ({ cardId, documentoId, file }) => {
      const fd = new FormData();
      fd.append("file", file);
      const res = await fetch(`${API_BASE_URL}/prospeccao/${cardId}/documentos/${documentoId}/arquivo`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: fd,
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao enviar o arquivo"));
      return res.json();
    },
    onSuccess: (_data, vars) => {
      invalidar(vars.cardId);
      toast.success("Arquivo enviado");
    },
    onError: error => toast.error(error.message, { duration: 6000 }),
  });
}

export function useRemoverArquivo() {
  const invalidar = useInvalidar();
  return useMutation<void, Error, { cardId: string; arquivoId: string }>({
    mutationFn: async ({ arquivoId }) => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/arquivos/${arquivoId}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha ao remover o arquivo"));
    },
    onSuccess: (_data, vars) => {
      invalidar(vars.cardId);
      toast.success("Arquivo removido da esteira");
    },
    onError: error => toast.error(error.message),
  });
}

/** Download autenticado: o caminho no compartilhamento nunca chega ao navegador. */
export async function baixarArquivo(arquivo: ProspeccaoArquivo) {
  const res = await fetch(`${API_BASE_URL}/prospeccao/arquivos/${arquivo.id}`, {
    headers: getAuthHeaders(),
  });
  if (!res.ok) {
    toast.error(await extractErrorMessage(res, "Falha ao baixar o arquivo"));
    return;
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = arquivo.nomeOriginal;
  link.click();
  URL.revokeObjectURL(url);
}

export function useDocumentoTipos() {
  return useQuery<DocumentoTipo[]>({
    queryKey: ["documentoTipos"],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/documento-tipos`, {
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error("Falha ao carregar o catálogo");
      return res.json();
    },
    staleTime: 10 * 60 * 1000,
  });
}

export function useBackfillVisaoCedente() {
  const invalidar = useInvalidar();
  return useMutation<{ cardsCriados: number }, Error, void>({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/prospeccao/backfill-visao-cedente`, {
        method: "POST",
        headers: getAuthHeaders("application/json"),
      });
      if (!res.ok) throw new Error(await extractErrorMessage(res, "Falha no backfill"));
      return res.json();
    },
    onSuccess: data => {
      invalidar();
      toast.success(
        data.cardsCriados > 0
          ? `${data.cardsCriados} card(s) trazido(s) para a esteira`
          : "Nenhuma análise nova para trazer",
      );
    },
    onError: error => toast.error(error.message),
  });
}
