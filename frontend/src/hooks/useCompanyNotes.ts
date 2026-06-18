import { useCallback } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import type { CompanyNote } from "../types/company-note";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

function getAuthHeaders() {
  const token = localStorage.getItem("serasa_token");
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function extractErrorMessage(res: Response, fallback: string) {
  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    const data = await res.json().catch(() => ({}));
    return data.message || data.error || fallback;
  }
  const text = await res.text().catch(() => "");
  return text || fallback;
}

export function useCompanyNotes(cnpj: string) {
  const queryClient = useQueryClient();
  const cleanCnpj = cnpj ? cnpj.replace(/\D/g, "") : "";
  const legacyAttachmentUrl = useCallback(
    (noteId: string) => `${API_BASE_URL}/company/${cleanCnpj}/notes/${noteId}/attachment`,
    [cleanCnpj],
  );
  const attachmentUrl = useCallback(
    (noteId: string, attachmentId?: string | null) =>
      attachmentId
        ? `${API_BASE_URL}/company/${cleanCnpj}/notes/${noteId}/attachments/${attachmentId}`
        : legacyAttachmentUrl(noteId),
    [cleanCnpj, legacyAttachmentUrl],
  );

  const query = useQuery<CompanyNote[]>({
    queryKey: ["companyNotes", cleanCnpj],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/notes`, {
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao carregar anotações"));
      }

      return res.json();
    },
    enabled: !!cleanCnpj,
  });

  const createNote = useMutation({
    mutationFn: async ({ content, parentNoteId, files }: { content: string; parentNoteId?: string | null; files?: File[] }) => {
      const formData = new FormData();
      formData.set("content", content);
      if (parentNoteId) {
        formData.set("parentNoteId", parentNoteId);
      }
      files?.forEach((file) => formData.append("files", file));

      const token = localStorage.getItem("serasa_token");
      const res = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/notes`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao salvar anotação"));
      }

      return res.json() as Promise<CompanyNote>;
    },
    onSuccess: () => {
      toast.success("Anotação salva com sucesso");
      queryClient.invalidateQueries({ queryKey: ["companyNotes", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao salvar anotação");
    },
  });

  const deleteNote = useMutation({
    mutationFn: async (noteId: string) => {
      const res = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/notes/${noteId}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao apagar anotação"));
      }
    },
    onSuccess: () => {
      toast.success("Anotação removida");
      queryClient.invalidateQueries({ queryKey: ["companyNotes", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao apagar anotação");
    },
  });

  const updateNote = useMutation({
    mutationFn: async ({ noteId, content }: { noteId: string; content: string }) => {
      const res = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/notes/${noteId}`, {
        method: "PATCH",
        headers: getAuthHeaders(),
        body: JSON.stringify({ content }),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao editar anotação"));
      }

      return res.json() as Promise<CompanyNote>;
    },
    onSuccess: () => {
      toast.success("Anotação atualizada");
      queryClient.invalidateQueries({ queryKey: ["companyNotes", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao editar anotação");
    },
  });

  const addAttachments = useMutation({
    mutationFn: async ({ noteId, files }: { noteId: string; files: File[] }) => {
      const formData = new FormData();
      files.forEach((file) => formData.append("files", file));

      const token = localStorage.getItem("serasa_token");
      const res = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/notes/${noteId}/attachments`, {
        method: "POST",
        headers: token ? { Authorization: `Bearer ${token}` } : {},
        body: formData,
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao anexar arquivos"));
      }

      return res.json() as Promise<CompanyNote>;
    },
    onSuccess: () => {
      toast.success("Anexo(s) adicionado(s)");
      queryClient.invalidateQueries({ queryKey: ["companyNotes", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao anexar arquivos");
    },
  });

  const deleteAttachment = useMutation({
    mutationFn: async ({ noteId, attachmentId }: { noteId: string; attachmentId?: string | null }) => {
      const res = await fetch(attachmentUrl(noteId, attachmentId ?? null), {
        method: "DELETE",
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao apagar anexo"));
      }
    },
    onSuccess: () => {
      toast.success("Anexo removido");
      queryClient.invalidateQueries({ queryKey: ["companyNotes", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao apagar anexo");
    },
  });

  return {
    notes: query.data ?? [],
    isLoading: query.isLoading,
    isError: query.isError,
    createNote: createNote.mutateAsync,
    isCreating: createNote.isPending,
    deleteNote: deleteNote.mutateAsync,
    isDeleting: deleteNote.isPending,
    updateNote: updateNote.mutateAsync,
    isUpdating: updateNote.isPending,
    addAttachments: addAttachments.mutateAsync,
    isAddingAttachments: addAttachments.isPending,
    deleteAttachment: deleteAttachment.mutateAsync,
    isDeletingAttachment: deleteAttachment.isPending,
    attachmentUrl,
  };
}
