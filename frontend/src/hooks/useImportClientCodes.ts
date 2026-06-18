import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

const getAuthHeaders = () => {
  if (typeof window === "undefined") return {};
  const token = localStorage.getItem("serasa_token");
  return { ...(token ? { Authorization: `Bearer ${token}` } : {}) };
};

export interface CodeImportResult {
  totalProcessed: number;
  updated: number;
  notFound: number;
  withoutCode: number;
  errors: number;
}

export function useImportClientCodes() {
  const queryClient = useQueryClient();

  const mutation = useMutation<CodeImportResult, Error, File>({
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);
      const res = await fetch(`${API_BASE_URL}/clients/import/codigos`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: formData,
      });
      if (!res.ok) {
        const text = await res.text().catch(() => "Erro desconhecido");
        throw new Error(text || "Falha na importação dos códigos");
      }
      return res.json();
    },
    onMutate: () => {
      toast.loading("Importando códigos...", { id: "import-codes" });
    },
    onSuccess: (data) => {
      toast.success(
        `Códigos: ${data.updated} atualizados, ${data.notFound} não encontrados, ${data.withoutCode} sem código` +
          (data.errors > 0 ? `, ${data.errors} erros` : ""),
        { id: "import-codes", duration: 6000 },
      );
      queryClient.invalidateQueries({ queryKey: ["companyList"] });
    },
    onError: (err) => {
      toast.error(err.message, { id: "import-codes" });
    },
  });

  return { importCodes: mutation.mutate, isImporting: mutation.isPending, lastResult: mutation.data };
}
