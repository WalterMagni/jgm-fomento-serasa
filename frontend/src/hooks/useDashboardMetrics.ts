import { useQuery } from '@tanstack/react-query';
import { ClientProfile } from '../types/company-detail';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export interface DashboardMetrics {
  totalClients: number;
  analyzedClients: number;
  avgScore: number;
  highRiskCount: number;
  totalDebt: number;
  cnpjaEnrichedCount: number;
  cedenteSimCount: number;
  pendingAnalysisCount: number;
  allProfiles: ClientProfile[];
}

export function useDashboardMetrics() {
  return useQuery<DashboardMetrics>({
    queryKey: ['dashboardMetrics'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/profiles/metrics`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) throw new Error('Erro ao buscar métricas da dashboard');

      const data = await res.json();

      return {
        totalClients: data.totalClients ?? 0,
        analyzedClients: data.analyzedClients ?? 0,
        avgScore: data.avgScore ?? 0,
        highRiskCount: data.highRiskCount ?? 0,
        totalDebt: data.totalDebt ?? 0,
        cnpjaEnrichedCount: data.cnpjaEnrichedCount ?? 0,
        cedenteSimCount: data.cedenteSimCount ?? 0,
        pendingAnalysisCount: data.pendingAnalysisCount ?? 0,
        allProfiles: [],
      };
    },
    staleTime: 2 * 60 * 1000,
  });
}
