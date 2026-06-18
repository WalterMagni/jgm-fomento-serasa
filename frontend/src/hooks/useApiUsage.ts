import { useQuery } from '@tanstack/react-query';
import { ApiUsageLog, BillingSettings } from '../types/api-usage';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export function useApiUsage() {
  const query = useQuery<ApiUsageLog[]>({
    queryKey: ['apiUsageLogs'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/billing/usage`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        if (res.status === 404) return []; // Fallback empty if backend endpoint is not ready yet
        throw new Error('Erro ao buscar log de consumo da API');
      }
      return res.json();
    }
  });

  return {
    logs: query.data || [],
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
  };
}

export function useBillingSettings() {
  const query = useQuery<BillingSettings>({
    queryKey: ['billingSettings'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/billing/settings`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        throw new Error('Erro ao buscar configurações de billing');
      }
      return res.json();
    },
  });

  return {
    settings: query.data ?? null,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
