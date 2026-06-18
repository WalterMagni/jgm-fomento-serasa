import { useQuery } from '@tanstack/react-query';
import { PageResponse, ClientProfile } from '../types/company-detail';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

export function useCompanyList(page = 0, size = 10, search = '', visaoCedente = '', analysisStatus = '') {
  const listQuery = useQuery<PageResponse<ClientProfile>>({
    queryKey: ['companyList', page, size, search, visaoCedente, analysisStatus],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: String(page),
        size: String(size),
      });
      if (search.trim()) {
        params.set('search', search.trim());
      }
      if (visaoCedente.trim()) {
        params.set('visaoCedente', visaoCedente.trim());
      }
      if (analysisStatus.trim()) {
        params.set('analysisStatus', analysisStatus.trim());
      }
      const res = await fetch(`${API_BASE_URL}/company/profiles?${params.toString()}`, {
        headers: getAuthHeaders(),
      });
      if (!res.ok) {
        throw new Error('Erro ao buscar lista de empresas');
      }
      return res.json();
    }
  });

  return {
    companies: listQuery.data?.content || [],
    pageData: listQuery.data,
    isLoading: listQuery.isLoading,
    isError: listQuery.isError,
    refetch: listQuery.refetch,
  };
}
