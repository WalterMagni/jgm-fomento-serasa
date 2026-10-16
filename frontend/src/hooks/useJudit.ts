import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { JuditDetalheConsultado, JuditProcessosConsultados, JuditSituacao } from "@/types/judit";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/** A mensagem do backend é a que serve: diz se foi limite, empresa desligada ou chave recusada. */
async function pedir(url: string, init: RequestInit, fallback: string): Promise<JuditSituacao> {
  const res = await fetch(url, { ...init, headers: getAuthHeaders() });
  if (!res.ok) {
    const dados = await res.json().catch(() => ({}));
    throw new Error(dados.message || dados.error || fallback);
  }
  return res.json();
}

const chave = (cnpj: string) => ["judit", cnpj] as const;
const chaveLista = (cnpj: string) => ["juditProcessos", cnpj] as const;

/** Só lê o que já foi consultado — não gasta. */
export function useJudit(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<JuditSituacao>({
    queryKey: chave(limpo),
    enabled: limpo.length === 14,
    queryFn: () => pedir(`${API_BASE_URL}/company/${limpo}/judit`, {}, "Não foi possível carregar os processos."),
  });
}

/** Consulta cobrada. O servidor confere a marca Judit e o limite do mês antes de chamar. */
function useConsultaJudit(cnpj: string, caminho: string, sucesso: string) {
  const limpo = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  return useMutation<JuditSituacao, Error, void>({
    mutationFn: () => pedir(`${API_BASE_URL}/company/${limpo}/judit${caminho}`, { method: "POST" }, "Falha na consulta à Judit."),
    onSuccess: dados => {
      queryClient.setQueryData(chave(limpo), dados);
      queryClient.invalidateQueries({ queryKey: chaveLista(limpo) });
      // O consumo do mês mudou para todas as empresas abertas.
      queryClient.invalidateQueries({ queryKey: ["judit"] });
      toast.success(sucesso);
    },
    onError: error => toast.error(error.message),
  });
}

export function useConsultarResumoJudit(cnpj: string) {
  return useConsultaJudit(cnpj, "/resumo", "Resumo processual atualizado");
}

export function useConsultarProcessosJudit(cnpj: string) {
  return useConsultaJudit(cnpj, "/processos", "Lista de processos atualizada");
}

/** A lista inteira da última busca, para a página de processos. Não cobra; nulo se nunca buscou. */
export function useJuditProcessos(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<JuditProcessosConsultados | null>({
    queryKey: chaveLista(limpo),
    enabled: limpo.length === 14,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/judit/processos`, { headers: getAuthHeaders() });
      if (res.status === 204) return null;
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível carregar os processos.");
      }
      return res.json();
    },
  });
}

/**
 * Baixa a planilha dos processos. Passa pelo fetch autenticado (um link direto voltaria 401).
 * `cnjs` vazio exporta todos; com filtro, só os que a tela mostra. Não cobra: lê o que já foi pago.
 */
export async function exportarProcessosJudit(cnpj: string, cnjs: string[]) {
  const limpo = cnpj.replace(/\D/g, "");
  const res = await fetch(`${API_BASE_URL}/company/${limpo}/judit/exportar`, {
    method: "POST",
    headers: getAuthHeaders(),
    body: JSON.stringify({ cnjs }),
  });
  if (!res.ok) {
    const dados = await res.json().catch(() => ({}));
    toast.error(dados.message || dados.error || "Falha ao exportar.");
    return;
  }
  const disposicao = res.headers.get("content-disposition") ?? "";
  const nome = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposicao)?.[1];
  const url = URL.createObjectURL(await res.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = decodeURIComponent(nome ?? `processos-judit-${limpo}.xlsx`);
  link.click();
  URL.revokeObjectURL(url);
  toast.success("Planilha baixada");
}

const chaveDetalhe = (cnpj: string, cnj: string) => ["juditDetalhe", cnpj, cnj] as const;

/**
 * O último detalhe do processo. Não cobra; nulo se nunca foi detalhado. Enquanto PENDENTE, relê a
 * cada 5 s — o sino também avisa, mas quem está com a gaveta aberta vê chegar sem clicar.
 */
export function useJuditDetalhe(cnpj: string, cnj: string | null) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<JuditDetalheConsultado | null>({
    queryKey: chaveDetalhe(limpo, cnj ?? ""),
    enabled: limpo.length === 14 && Boolean(cnj),
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/judit/processos/${encodeURIComponent(cnj!)}/detalhe`, {
        headers: getAuthHeaders(),
      });
      if (res.status === 204) return null;
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível carregar o detalhe.");
      }
      return res.json();
    },
    refetchInterval: query => (query.state.data?.situacao === "PENDENTE" ? 5000 : false),
  });
}

function useAcaoDetalhe(cnpj: string, sufixo: string, sucesso: string) {
  const limpo = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  return useMutation<JuditDetalheConsultado, Error, { cnj: string; nomeEmpresa?: string | null }>({
    mutationFn: async ({ cnj, nomeEmpresa }) => {
      const res = await fetch(
        `${API_BASE_URL}/company/${limpo}/judit/processos/${encodeURIComponent(cnj)}/detalhe${sufixo}`,
        { method: "POST", headers: getAuthHeaders(), body: JSON.stringify({ nomeEmpresa: nomeEmpresa ?? null }) },
      );
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Falha na consulta à Judit.");
      }
      return res.json();
    },
    onSuccess: (dados, { cnj }) => {
      queryClient.setQueryData(chaveDetalhe(limpo, cnj), dados);
      queryClient.invalidateQueries({ queryKey: chaveLista(limpo) });
      queryClient.invalidateQueries({ queryKey: ["judit"] });
      toast.success(sucesso);
    },
    onError: error => toast.error(error.message),
  });
}

/** Pede o detalhe à Judit. Cobra. Volta pendente; o sino avisa quando ficar pronto. */
export function useDetalharProcesso(cnpj: string) {
  return useAcaoDetalhe(cnpj, "", "Pedido enviado à Judit — você recebe um aviso quando ficar pronto");
}

/** Retoma o acompanhamento de um detalhe que ficou pendente. Não cobra. */
export function useVerificarDetalhe(cnpj: string) {
  return useAcaoDetalhe(cnpj, "/verificar", "Verificando com a Judit");
}
