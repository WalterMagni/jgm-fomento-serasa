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

export function usePersonNotes(cpf: string) {
  const queryClient = useQueryClient();
  const cleanCpf = cpf ? cpf.replace(/\D/g, "") : "";
  const legacyAttachmentUrl = useCallback(
    (noteId: string) => `${API_BASE_URL}/person/${cleanCpf}/notes/${noteId}/attachment`,
    [cleanCpf],
  );
  const attachmentUrl = useCallback(
    (noteId: string, attachmentId?: string | null) =>
      attachmentId
        ? `${API_BASE_URL}/person/${cleanCpf}/notes/${noteId}/attachments/${attachmentId}`
        : legacyAttachmentUrl(noteId),
    [cleanCpf, legacyAttachmentUrl],
  );

  const query = useQuery<CompanyNote[]>({
    queryKey: ["personNotes", cleanCpf],
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/person/${cleanCpf}/notes`, {
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao carregar anotações"));
      }

      return res.json();
    },
    enabled: !!cleanCpf,
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
      const res = await fetch(`${API_BASE_URL}/person/${cleanCpf}/notes`, {
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
      queryClient.invalidateQueries({ queryKey: ["personNotes", cleanCpf] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao salvar anotação");
    },
  });

  const deleteNote = useMutation({
    mutationFn: async (noteId: string) => {
      const res = await fetch(`${API_BASE_URL}/person/${cleanCpf}/notes/${noteId}`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });

      if (!res.ok) {
        throw new Error(await extractErrorMessage(res, "Erro ao apagar anotação"));
      }
    },
    onSuccess: () => {
      toast.success("Anotação removida");
      queryClient.invalidateQueries({ queryKey: ["personNotes", cleanCpf] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao apagar anotação");
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
    attachmentUrl,
  };
}
