import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { ListagemNotificacoes } from "../types/notificacao";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const AUTH_BASE_URL = API_BASE_URL.replace(/\/api\/v1\/?$/, "");

export const NOTIFICACOES_KEY = ["notificacoes"] as const;

function headers(json = false): HeadersInit {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return { ...(json ? { "Content-Type": "application/json" } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) };
}

/** URL do canal em tempo real. O ticket é de uso único: cada reconexão pede outro. */
export async function abrirUrlDoCanal(): Promise<string> {
  const res = await fetch(`${API_BASE_URL}/notificacoes/ticket`, { method: "POST", headers: headers() });
  if (!res.ok) throw new Error(`ticket ${res.status}`);
  const { ticket } = (await res.json()) as { ticket: string };
  return `${API_BASE_URL}/notificacoes/stream?ticket=${encodeURIComponent(ticket)}`;
}

/**
 * Últimas notificações e quantas estão sem ler.
 *
 * <p>Com o canal em tempo real ligado, a lista só muda quando chega evento. Sem ele (rede caiu,
 * reconectando), volta a perguntar a cada 30 segundos — o sino nunca fica mudo por mais que isso.</p>
 */
export function useNotificacoes(tempoReal: boolean) {
  return useQuery<ListagemNotificacoes>({
    queryKey: NOTIFICACOES_KEY,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/notificacoes?limite=20`, { headers: headers() });
      if (!res.ok) throw new Error("Falha ao carregar as notificações");
      return res.json();
    },
    staleTime: 30 * 1000,
    refetchInterval: tempoReal ? false : 30 * 1000,
  });
}

/** Marca como lida tirando da contagem na hora, sem esperar o servidor. */
function useMarcar<T>(chamar: (variaveis: T) => Promise<Response>, aplicar: (lista: ListagemNotificacoes, variaveis: T) => ListagemNotificacoes) {
  const queryClient = useQueryClient();
  return useMutation<Response, Error, T, { anterior?: ListagemNotificacoes }>({
    mutationFn: chamar,
    onMutate: async variaveis => {
      await queryClient.cancelQueries({ queryKey: NOTIFICACOES_KEY });
      const anterior = queryClient.getQueryData<ListagemNotificacoes>(NOTIFICACOES_KEY);
      if (anterior) queryClient.setQueryData(NOTIFICACOES_KEY, aplicar(anterior, variaveis));
      return { anterior };
    },
    onError: (_erro, _variaveis, contexto) => {
      if (contexto?.anterior) queryClient.setQueryData(NOTIFICACOES_KEY, contexto.anterior);
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: NOTIFICACOES_KEY }),
  });
}

const agora = () => new Date().toISOString();

export function useMarcarLida() {
  return useMarcar<string>(
    id => fetch(`${API_BASE_URL}/notificacoes/${id}/lida`, { method: "POST", headers: headers() }),
    (lista, id) => {
      const alvo = lista.itens.find(item => item.id === id);
      if (!alvo || alvo.lidaEm) return lista;
      return { naoLidas: Math.max(0, lista.naoLidas - 1), itens: lista.itens.map(item => (item.id === id ? { ...item, lidaEm: agora() } : item)) };
    },
  );
}

export function useMarcarTodasLidas() {
  return useMarcar<void>(
    () => fetch(`${API_BASE_URL}/notificacoes/lidas`, { method: "POST", headers: headers() }),
    lista => ({ naoLidas: 0, itens: lista.itens.map(item => (item.lidaEm ? item : { ...item, lidaEm: agora() })) }),
  );
}

/** Abrir um card resolve as notificações que apontavam para ele. */
export function useMarcarLidasDoLink() {
  return useMarcar<string>(
    link => fetch(`${API_BASE_URL}/notificacoes/lidas`, { method: "POST", headers: headers(true), body: JSON.stringify({ link }) }),
    (lista, link) => {
      const afetadas = lista.itens.filter(item => item.link === link && !item.lidaEm).length;
      if (afetadas === 0) return lista;
      return {
        naoLidas: Math.max(0, lista.naoLidas - afetadas),
        itens: lista.itens.map(item => (item.link === link && !item.lidaEm ? { ...item, lidaEm: agora() } : item)),
      };
    },
  );
}

/** Preferências do sino (som, e-mail). Ficam no perfil e valem em qualquer computador. */
export function useDefinirPreferencia() {
  const queryClient = useQueryClient();
  return useMutation<unknown, Error, { somNotificacao?: boolean; emailLiberacao?: boolean }>({
    mutationFn: async preferencia => {
      const res = await fetch(`${AUTH_BASE_URL}/api/auth/profile`, {
        method: "PATCH",
        headers: headers(true),
        body: JSON.stringify(preferencia),
      });
      if (!res.ok) throw new Error("Falha ao salvar a preferência");
      return res.json();
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["usuarioAtual"] }),
    onError: error => toast.error(error.message),
  });
}
