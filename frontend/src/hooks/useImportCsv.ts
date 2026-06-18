import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

const getAuthHeaders = () => {
  if (typeof window === 'undefined') return {};
  const token = localStorage.getItem('serasa_token');
  return { ...(token ? { Authorization: `Bearer ${token}` } : {}) };
};

export interface ImportResult {
  totalProcessed: number;
  created: number;
  updated: number;
  errors: number;
}

export function useImportCsv() {
  const queryClient = useQueryClient();

  const mutation = useMutation<ImportResult, Error, File>({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append('file', file);

      const res = await fetch(`${API_BASE_URL}/clients/import/upload`, {
        method: 'POST',
        headers: getAuthHeaders(), // Removendo Content-Type para o browser setar automaticamente o boundary
        body: formData,
      });
      if (!res.ok) {
        const text = await res.text().catch(() => 'Erro desconhecido');
        throw new Error(text || 'Falha na importação do CSV');
      }
      return res.json();
    },
    onMutate: () => {
      toast.loading('A importar clientes...', { id: 'import-csv' });
    },
    onSuccess: (data) => {
      toast.success(
        `Importação concluída: ${data.created} criados, ${data.updated} atualizados` +
          (data.errors > 0 ? `, ${data.errors} erros` : ''),
        { id: 'import-csv', duration: 5000 }
      );
      queryClient.invalidateQueries({ queryKey: ['companyList'] });
      queryClient.invalidateQueries({ queryKey: ['dashboardMetrics'] });
    },
    onError: (err) => {
      toast.error(err.message, { id: 'import-csv' });
    },
  });

  return {
    importCsv: mutation.mutate,
    isImporting: mutation.isPending,
    lastResult: mutation.data,
  };
}
