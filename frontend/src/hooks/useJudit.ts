import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type {
  JuditDetalheConsultado,
  JuditPainelConsumo,
  JuditPoloConsulta,
  JuditProcessosConsultados,
  JuditSituacao,
} from "@/types/judit";

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

const pendente = (situacao: JuditSituacao | undefined) =>
  situacao?.onDemandReu?.situacao === "PENDENTE" || situacao?.onDemandAutor?.situacao === "PENDENTE";

/**
 * Só lê o que já foi consultado — não gasta. Com um On Demand pendente, relê a cada 15 s: o sino
 * também avisa, mas quem está com a Visão 360º aberta vê o card mudar sozinho.
 */
export function useJudit(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<JuditSituacao>({
    queryKey: chave(limpo),
    enabled: limpo.length === 14,
    queryFn: () => pedir(`${API_BASE_URL}/company/${limpo}/judit`, {}, "Não foi possível carregar os processos."),
    refetchInterval: query => (pendente(query.state.data) ? 15000 : false),
  });
}

/** Ação que devolve a situação nova do card. As que cobram passam pelas travas no servidor (marca e limite). */
function useAcaoJudit<V>(cnpj: string, caminho: string, sucesso: string) {
  const limpo = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  return useMutation<JuditSituacao, Error, V>({
    mutationFn: corpo =>
      pedir(
        `${API_BASE_URL}/company/${limpo}/judit${caminho}`,
        { method: "POST", body: JSON.stringify(corpo ?? {}) },
        "Falha na consulta à Judit.",
      ),
    onSuccess: dados => {
      queryClient.setQueryData(chave(limpo), dados);
      queryClient.invalidateQueries({ queryKey: chaveLista(limpo) });
      // O consumo do mês mudou para todas as empresas abertas e para o painel.
      queryClient.invalidateQueries({ queryKey: ["judit"] });
      queryClient.invalidateQueries({ queryKey: ["juditConsumo"] });
      toast.success(sucesso);
    },
    onError: error => toast.error(error.message),
  });
}

/** Pré-consulta (resumo do Data Lake): síncrona, cobra o preço fixo. */
export function useConsultarResumoJudit(cnpj: string) {
  return useAcaoJudit<{ nomeEmpresa?: string | null }>(cnpj, "/resumo", "Pré-consulta pronta");
}

/** Pede o On Demand de um polo. Cobra a estimativa; volta pendente e o sino avisa quando ficar pronto. */
export function useConsultarOnDemand(cnpj: string) {
  return useAcaoJudit<{ polo: JuditPoloConsulta; nomeEmpresa?: string | null }>(
    cnpj,
    "/on-demand",
    "Pedido enviado à Judit — você recebe um aviso no sino quando ficar pronto",
  );
}

/** Retoma o acompanhamento de um On Demand pendente. Não cobra. */
export function useVerificarOnDemand(cnpj: string) {
  return useAcaoJudit<{ polo: JuditPoloConsulta }>(cnpj, "/on-demand/verificar", "Verificando com a Judit");
}

/** Aba Consumo Judit: franquia do mês, consultas do período e liberações. Não cobra. */
export function useJuditConsumo(dias: number) {
  return useQuery<JuditPainelConsumo>({
    queryKey: ["juditConsumo", dias],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/judit/consumo?dias=${dias}`, { headers: getAuthHeaders() });
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível carregar o consumo da Judit.");
      }
      return res.json();
    },
  });
}

/** Soma um valor ao limite do mês corrente. Só o admin; o servidor confere. */
export function useLiberarJudit() {
  const queryClient = useQueryClient();
  return useMutation<JuditPainelConsumo, Error, { valor: number; motivo: string }>({
    mutationFn: async corpo => {
      const res = await fetch(`${API_BASE_URL}/judit/liberacoes`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: JSON.stringify(corpo),
      });
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível liberar o valor.");
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["juditConsumo"] });
      queryClient.invalidateQueries({ queryKey: ["judit"] });
      toast.success("Valor liberado no limite deste mês");
    },
    onError: error => toast.error(error.message),
  });
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
