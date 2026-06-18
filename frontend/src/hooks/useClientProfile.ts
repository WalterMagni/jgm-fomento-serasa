import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { ClientProfile } from '../types/company-detail';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export function useClientProfile(cnpj: string, analysisId?: number | null) {
  const queryClient = useQueryClient();
  const cleanCnpj = cnpj ? cnpj.replace(/\D/g, '') : '';

  const profileQuery = useQuery<ClientProfile | null>({
    queryKey: ['clientProfile', cleanCnpj, analysisId ?? null],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (analysisId != null) params.set('analysisId', String(analysisId));
      const url = `${API_BASE_URL}/company/${cleanCnpj}/profile${params.toString() ? `?${params}` : ''}`;
      const res = await fetch(url, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        if (res.status === 404) return null;
        throw new Error('Erro ao buscar perfil do cliente');
      }
      return res.json();
    },
    enabled: !!cnpj,
  });

  const enrichCnpjaMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/enrich/cnpja/${cleanCnpj}`, {
        method: 'POST',
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.error || errorData.message || 'Erro ao atualizar via CNPJ Já');
      }
      return res.json();
    },
    onSuccess: () => {
      toast.success('Dados da empresa atualizados com sucesso (CNPJ Já)!');
      queryClient.invalidateQueries({ queryKey: ['clientProfile', cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || 'Falha ao enriquecer dados com CNPJ Já');
    },
  });

  const enrichSerasaMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/enrich/serasa/${cleanCnpj}`, {
        method: 'POST',
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        // Serasa sometimes returns array of errors under errorData.message
        let errorMessage = 'Erro ao consultar Serasa';
        if (Array.isArray(errorData)) {
          errorMessage = errorData[0]?.message || errorMessage;
        } else if (typeof errorData.message === 'string') {
          try {
            const parsed = JSON.parse(errorData.message);
            if (Array.isArray(parsed)) errorMessage = parsed[0]?.message || errorMessage;
            else errorMessage = parsed.message || errorMessage;
          } catch {
            errorMessage = errorData.message;
          }
        }
        throw new Error(errorMessage);
      }
      return res.json();
    },
    onSuccess: () => {
      toast.success('Análise de crédito gerada com sucesso (Serasa)!');
      queryClient.invalidateQueries({ queryKey: ['clientProfile', cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || 'Falha ao gerar análise de crédito');
    },
  });

  return {
    profile: profileQuery.data,
    isLoading: profileQuery.isLoading,
    isError: profileQuery.isError,
    error: profileQuery.error,
    refreshCnpja: enrichCnpjaMutation.mutate,
    isRefreshingCnpja: enrichCnpjaMutation.isPending,
    refreshSerasa: enrichSerasaMutation.mutate,
    isRefreshingSerasa: enrichSerasaMutation.isPending,
  };
}
