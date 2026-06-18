import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export interface StandardTerm {
  cnpj: string;
  termText: string;
  updatedAt: string;
}

export function useStandardTerms() {
  const queryClient = useQueryClient();

  const termsQuery = useQuery<StandardTerm[]>({
    queryKey: ['standardTerms'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/standard-terms`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        throw new Error('Erro ao buscar termos padrão');
      }
      return res.json();
    }
  });

  const updateTermMutation = useMutation({
    mutationFn: async (data: { cnpj: string; termText: string }) => {
      const res = await fetch(`${API_BASE_URL}/standard-terms`, {
        method: 'PUT',
        headers: getAuthHeaders(),
        body: JSON.stringify(data),
      });
      if (!res.ok) {
        throw new Error('Erro ao atualizar termo padrão');
      }
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['standardTerms'] });
    }
  });

  return {
    terms: termsQuery.data || [],
    isLoading: termsQuery.isLoading,
    isError: termsQuery.isError,
    updateTerm: updateTermMutation.mutateAsync,
    isUpdating: updateTermMutation.isPending,
  };
}
