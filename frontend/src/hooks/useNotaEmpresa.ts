import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { NotaAtual, NotaEmpresa } from "@/types/nota-empresa";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/** Nota da equipe e o histórico de trocas de uma empresa. */
export function useNotaEmpresa(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  return useQuery<NotaAtual>({
    queryKey: ["notaEmpresa", limpo],
    enabled: limpo.length === 14,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/nota`, { headers: getAuthHeaders() });
      if (!res.ok) throw new Error("Não foi possível carregar a nota.");
      return res.json();
    },
  });
}

/**
 * Dá, troca ou retira (`null`) a nota. A carteira e as esteiras são recarregadas para a coluna, o
 * filtro e o selo dos cards baterem.
 */
export function useDarNota(cnpj: string) {
  const limpo = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (nota: NotaEmpresa | null): Promise<NotaAtual> => {
      const res = await fetch(`${API_BASE_URL}/company/${limpo}/nota`, {
        method: "PUT",
        headers: getAuthHeaders(),
        body: JSON.stringify({ nota }),
      });
      if (!res.ok) {
        const dados = await res.json().catch(() => ({}));
        throw new Error(dados.message || dados.error || "Não foi possível salvar a nota.");
      }
      return res.json();
    },
    onSuccess: dados => {
      queryClient.setQueryData(["notaEmpresa", limpo], dados);
      for (const chave of ["companyList", "prospeccao", "prospeccaoDetalhe", "liberacao", "liberacaoDetalhe"]) {
        queryClient.invalidateQueries({ queryKey: [chave] });
      }
    },
  });
}
