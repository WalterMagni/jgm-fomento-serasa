import { useQuery } from '@tanstack/react-query';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export interface DashboardSummary {
  totalClients: number;
  highRiskClients: number;
  totalDebt: number;
  serasaQueriesMonth: number;
}

export function useDashboardData() {
  const summaryQuery = useQuery<DashboardSummary>({
    queryKey: ['dashboardSummary'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/dashboard/summary`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        throw new Error('Erro ao buscar resumo da dashboard');
      }
      return res.json();
    }
  });

  return {
    summary: summaryQuery.data,
    isLoading: summaryQuery.isLoading,
    isError: summaryQuery.isError,
  };
}
