import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { LimiteSacado } from "@/types/limite-sacado";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/** Limite da empresa como sacado e o histórico de trocas. */
export function useLimiteSacado(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<LimiteSacado>({
    queryKey: ["limiteSacado", limpo],
    enabled: limpo.length === 14,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/limite-sacado`, { headers: getAuthHeaders() });
      if (!res.ok) throw new Error("Não foi possível carregar o limite.");
      return res.json();
    },
  });
}

/** Define, troca ou retira (`null`) o limite. A liberação é recarregada para o sacado mostrar o novo. */
export function useDefinirLimiteSacado(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (valor: number | null): Promise<LimiteSacado> => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/limite-sacado`, {
        method: "PUT",
        headers: getAuthHeaders(),
        body: JSON.stringify({ valor }),
      });
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível salvar o limite.");
      }
      return res.json();
    },
    onSuccess: dados => {
      queryClient.setQueryData(["limiteSacado", limpo], dados);
      for (const chave of ["liberacao", "liberacaoDetalhe"]) {
        queryClient.invalidateQueries({ queryKey: [chave] });
      }
    },
  });
}
