import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { PersonAnalysisData } from '../types/person-analysis';
import type { PessoaResumo } from '../types/pessoa-resumo';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

/** Busca a análise PF mais recente para um CPF. */
export function usePersonProfile(cpf: string) {
  const queryClient = useQueryClient();
  const cleanCpf = cpf ? cpf.replace(/\D/g, '') : '';

  const profileQuery = useQuery<PersonAnalysisData | null>({
    queryKey: ['personProfile', cleanCpf],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/person/${cleanCpf}/profile`, {
        headers: getAuthHeaders(),
      });
      if (res.status === 404) return null;
      if (!res.ok) throw new Error('Erro ao buscar análise da pessoa física');
      return res.json();
    },
    enabled: !!cleanCpf && cleanCpf.length === 11,
  });

  const enrichSerasaMutation = useMutation({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/person/enrich/serasa/${cleanCpf}`, {
        method: 'POST',
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        let errorMessage = 'Erro ao consultar Serasa PF';
        if (Array.isArray(errorData)) {
          errorMessage = errorData[0]?.message || errorMessage;
        } else if (typeof errorData.message === 'string') {
          try {
            const parsed = JSON.parse(errorData.message);
            errorMessage = Array.isArray(parsed)
              ? parsed[0]?.message || errorMessage
              : parsed.message || errorMessage;
          } catch {
            errorMessage = errorData.message;
          }
        }
        throw new Error(errorMessage);
      }
      return res.json() as Promise<PersonAnalysisData>;
    },
    onSuccess: () => {
      toast.success('Análise de crédito PF gerada com sucesso!');
      queryClient.invalidateQueries({ queryKey: ['personProfile', cleanCpf] });
      queryClient.invalidateQueries({ queryKey: ['pessoasResumo'] });
    },
    onError: (error: Error) => {
      toast.error(error.message || 'Falha ao consultar Serasa PF');
    },
  });

  return {
    profile: profileQuery.data ?? null,
    isLoading: profileQuery.isLoading,
    isError: profileQuery.isError,
    error: profileQuery.error,
    consultSerasa: enrichSerasaMutation.mutate,
    isConsulting: enrichSerasaMutation.isPending,
    consultedData: enrichSerasaMutation.data,
  };
}

/**
 * Última consulta de cada CPF, resumida (página /individuals). Vem tudo de uma vez: a tela
 * filtra, ordena, pagina e conta na memória.
 */
export function usePessoasResumo() {
  return useQuery<PessoaResumo[]>({
    queryKey: ['pessoasResumo'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/person/profiles/resumo`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error('Não foi possível carregar as pessoas físicas.');
      return res.json();
    },
    staleTime: 60 * 1000,
  });
}

/** Mutation para consultar Serasa PF a partir da página da empresa (sócio). */
export function useEnrichPersonSerasa(cpf: string, onSuccessCb?: () => void) {
  const queryClient = useQueryClient();
  const cleanCpf = cpf ? cpf.replace(/\D/g, '') : '';

  return useMutation({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/person/enrich/serasa/${cleanCpf}`, {
        method: 'POST',
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.message || 'Erro ao consultar Serasa PF');
      }
      return res.json() as Promise<PersonAnalysisData>;
    },
    onSuccess: (data) => {
      toast.success(`Análise de ${data.personName || 'Pessoa Física'} salva!`);
      queryClient.invalidateQueries({ queryKey: ['personProfile', cleanCpf] });
      queryClient.invalidateQueries({ queryKey: ['pessoasResumo'] });
      onSuccessCb?.();
    },
    onError: (error: Error) => {
      toast.error(error.message || 'Falha ao consultar Serasa PF');
    },
  });
}
