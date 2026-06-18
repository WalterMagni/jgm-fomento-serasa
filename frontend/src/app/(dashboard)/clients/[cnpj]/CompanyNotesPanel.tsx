"use client";

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";
import { useCompanyNotes } from "../../../../hooks/useCompanyNotes";
import type { CompanyNote, CompanyNoteAttachment } from "../../../../types/company-note";

function formatNoteDate(value: string) {
  const date = new Date(value);
  if (isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

function getInitials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? "")
    .join("");
}

function isImageFile(file: File | CompanyNoteAttachment) {
  const mimeType = file instanceof File ? file.type : (file.contentType || "");
  return mimeType.startsWith("image/");
}

function attachmentLabel(attachment: CompanyNoteAttachment) {
  return attachment.fileName || "Baixar anexo";
}

function fileKey(file: File) {
  return `${file.name}-${file.size}-${file.lastModified}`;
}

function extractPastedFiles(items: DataTransferItemList | DataTransferItem[]) {
  return Array.from(items)
    .filter((item) => item.type.startsWith("image/"))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null)
    .map((file, index) => {
      const extension = file.type.split("/")[1] || "png";
      return new File([file], `imagem-colada-${Date.now()}-${index + 1}.${extension}`, { type: file.type });
    });
}

export function CompanyNotesPanel({ cnpj }: { cnpj: string }) {
  const {
    notes,
    isLoading,
    createNote,
    isCreating,
    deleteNote,
    isDeleting,
    updateNote,
    isUpdating,
    addAttachments,
    isAddingAttachments,
    deleteAttachment,
    isDeletingAttachment,
    attachmentUrl,
  } = useCompanyNotes(cnpj);
  const composerRef = useRef<HTMLDivElement | null>(null);
  const [draft, setDraft] = useState("");
  const [replyTarget, setReplyTarget] = useState<CompanyNote | null>(null);
  const [editingNoteId, setEditingNoteId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState("");
  const [editFiles, setEditFiles] = useState<File[]>([]);
  const [isEditDragActive, setIsEditDragActive] = useState(false);
  const [selectedAuthor, setSelectedAuthor] = useState<string>("all");
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [isDragActive, setIsDragActive] = useState(false);
  const [isDownloadingAttachment, setIsDownloadingAttachment] = useState<string | null>(null);
  const [selectedFilePreviewUrls, setSelectedFilePreviewUrls] = useState<Record<string, string>>({});
  const [editFilePreviewUrls, setEditFilePreviewUrls] = useState<Record<string, string>>({});
  const [attachmentPreviewUrls, setAttachmentPreviewUrls] = useState<Record<string, string>>({});
  const [expandedAttachmentNotes, setExpandedAttachmentNotes] = useState<Record<string, boolean>>({});

  const authors = Array.from(new Set(notes.map((note) => note.authorEmail))).sort();
  const filteredNotes = selectedAuthor === "all"
    ? notes
    : notes.filter((note) => note.authorEmail === selectedAuthor);

  const normalizedNotes = useMemo(
    () => filteredNotes.map((note) => ({
      ...note,
      attachments: note.attachments && note.attachments.length > 0
        ? note.attachments
        : note.hasAttachment
          ? [{
              id: null,
              fileName: note.attachmentFileName,
              contentType: note.attachmentContentType,
              fileSize: note.attachmentSize,
            }]
          : [],
    })),
    [filteredNotes],
  );

  const sortedSelectedFiles = useMemo(
    () => [...selectedFiles].sort((a, b) => {
      const aIsImage = isImageFile(a);
      const bIsImage = isImageFile(b);
      if (aIsImage !== bIsImage) return aIsImage ? -1 : 1;
      return a.name.localeCompare(b.name, "pt-BR");
    }),
    [selectedFiles],
  );

  const sortedEditFiles = useMemo(
    () => [...editFiles].sort((a, b) => {
      const aIsImage = isImageFile(a);
      const bIsImage = isImageFile(b);
      if (aIsImage !== bIsImage) return aIsImage ? -1 : 1;
      return a.name.localeCompare(b.name, "pt-BR");
    }),
    [editFiles],
  );

  const selectedImagesCount = useMemo(
    () => selectedFiles.filter((file) => isImageFile(file)).length,
    [selectedFiles],
  );

  const selectedOtherFilesCount = selectedFiles.length - selectedImagesCount;
  const editImagesCount = useMemo(
    () => editFiles.filter((file) => isImageFile(file)).length,
    [editFiles],
  );
  const editOtherFilesCount = editFiles.length - editImagesCount;

  const getVisibleAttachments = (noteId: string, attachments: CompanyNoteAttachment[]) =>
    expandedAttachmentNotes[noteId] ? attachments : attachments.slice(0, 1);

  const handleSubmit = async () => {
    const content = draft.trim();
    if (!content) return;
    await createNote({
      content,
      parentNoteId: replyTarget?.id ?? null,
      files: selectedFiles,
    });
    setDraft("");
    setReplyTarget(null);
    setSelectedFiles([]);
  };

  const handleDelete = async (noteId: string) => {
    await deleteNote(noteId);
  };

  const startEdit = (note: CompanyNote) => {
    setEditingNoteId(note.id);
    setEditDraft(note.content);
    setEditFiles([]);
    setIsEditDragActive(false);
  };

  const cancelEdit = () => {
    setEditingNoteId(null);
    setEditDraft("");
    setEditFiles([]);
    setIsEditDragActive(false);
  };

  const handleUpdate = async (noteId: string) => {
    const content = editDraft.trim();
    if (!content) return;
    await updateNote({ noteId, content });
    if (editFiles.length > 0) {
      await addAttachments({ noteId, files: editFiles });
    }
    cancelEdit();
  };

  const handleDeleteAttachment = async (noteId: string, attachment: CompanyNoteAttachment) => {
    await deleteAttachment({ noteId, attachmentId: attachment.id ?? null });
  };

  useEffect(() => {
    const nextPreviewUrls: Record<string, string> = {};
    selectedFiles.forEach((file) => {
      if (file.type.startsWith("image/")) {
        nextPreviewUrls[`${file.name}-${file.size}-${file.lastModified}`] = URL.createObjectURL(file);
      }
    });

    setSelectedFilePreviewUrls((current) => {
      Object.values(current).forEach((url) => URL.revokeObjectURL(url));
      return nextPreviewUrls;
    });

    return () => {
      Object.values(nextPreviewUrls).forEach((url) => URL.revokeObjectURL(url));
    };
  }, [selectedFiles]);

  useEffect(() => {
    const nextPreviewUrls: Record<string, string> = {};
    editFiles.forEach((file) => {
      if (file.type.startsWith("image/")) {
        nextPreviewUrls[fileKey(file)] = URL.createObjectURL(file);
      }
    });

    setEditFilePreviewUrls((current) => {
      Object.values(current).forEach((url) => URL.revokeObjectURL(url));
      return nextPreviewUrls;
    });

    return () => {
      Object.values(nextPreviewUrls).forEach((url) => URL.revokeObjectURL(url));
    };
  }, [editFiles]);

  useEffect(() => {
    let isMounted = true;
    const objectUrls: string[] = [];
    const imageAttachments = normalizedNotes.flatMap((note) =>
      (note.attachments ?? [])
        .filter((attachment) => attachment.id && attachment.contentType?.startsWith("image/"))
        .map((attachment) => ({ noteId: note.id, attachmentId: attachment.id!, attachment })),
    );

    if (imageAttachments.length === 0) {
      setAttachmentPreviewUrls({});
      return;
    }

    async function loadPreviews() {
      const token = localStorage.getItem("serasa_token");
      const entries = await Promise.all(imageAttachments.map(async ({ noteId, attachmentId }) => {
        try {
          const response = await fetch(attachmentUrl(noteId, attachmentId), {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          });
          if (!response.ok) return null;
          const blob = await response.blob();
          const url = URL.createObjectURL(blob);
          objectUrls.push(url);
          return [`${noteId}:${attachmentId}`, url] as const;
        } catch {
          return null;
        }
      }));

      if (!isMounted) return;
      const validEntries = entries.filter((entry): entry is readonly [`${string}:${string}`, string] => entry !== null);
      setAttachmentPreviewUrls(Object.fromEntries(validEntries));
    }

    void loadPreviews();

    return () => {
      isMounted = false;
      objectUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [normalizedNotes, attachmentUrl]);

  const appendFileList = useCallback((
    incomingFiles: File[],
    updateFiles: React.Dispatch<React.SetStateAction<File[]>>,
  ) => {
    const accepted = incomingFiles.filter((file) => {
      if (file.size > 5 * 1024 * 1024) {
        toast.error(`${file.name}: cada anexo deve ter no máximo 5 MB.`);
        return false;
      }
      return true;
    });

    if (accepted.length === 0) return;

    updateFiles((current) => {
      const existingKeys = new Set(current.map(fileKey));
      const deduplicated = accepted.filter((file) => !existingKeys.has(fileKey(file)));
      return [...current, ...deduplicated];
    });
  }, []);

  const appendFiles = useCallback(
    (incomingFiles: File[]) => appendFileList(incomingFiles, setSelectedFiles),
    [appendFileList],
  );
  const appendEditFiles = useCallback(
    (incomingFiles: File[]) => appendFileList(incomingFiles, setEditFiles),
    [appendFileList],
  );

  const removeSelectedFile = (target: File) => {
    setSelectedFiles((current) => current.filter((file) => file !== target));
  };

  const removeEditFile = (target: File) => {
    setEditFiles((current) => current.filter((file) => file !== target));
  };

  const clearSelectedFiles = () => {
    setSelectedFiles([]);
  };

  const clearEditFiles = () => {
    setEditFiles([]);
  };

  const handlePaste = (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pastedFiles = extractPastedFiles(event.clipboardData.items);

    if (pastedFiles.length === 0) return;

    event.preventDefault();
    appendFiles(pastedFiles);
    toast.success(`${pastedFiles.length} imagem(ns) colada(s) como anexo.`);
  };

  const handleEditPaste = (event: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pastedFiles = extractPastedFiles(event.clipboardData.items);

    if (pastedFiles.length === 0) return;

    event.preventDefault();
    appendEditFiles(pastedFiles);
    toast.success(`${pastedFiles.length} imagem(ns) colada(s) como anexo.`);
  };

  useEffect(() => {
    const root = composerRef.current;
    if (!root) return;

    const handleNativePaste = (event: ClipboardEvent) => {
      if (!root.contains(document.activeElement)) return;
      if (event.target instanceof HTMLTextAreaElement || event.defaultPrevented) return;
      const pastedFiles = extractPastedFiles(event.clipboardData?.items ?? []);

      if (pastedFiles.length === 0) return;
      event.preventDefault();
      appendFiles(pastedFiles);
      toast.success(`${pastedFiles.length} imagem(ns) colada(s) como anexo.`);
    };

    root.addEventListener("paste", handleNativePaste);
    return () => root.removeEventListener("paste", handleNativePaste);
  }, [appendFiles]);

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragActive(false);
    const droppedFiles = Array.from(event.dataTransfer.files);
    if (droppedFiles.length === 0) return;
    appendFiles(droppedFiles);
  };

  const handleTextAreaDrop = (event: React.DragEvent<HTMLTextAreaElement>) => {
    event.preventDefault();
    setIsDragActive(false);
    const droppedFiles = Array.from(event.dataTransfer.files);
    if (droppedFiles.length === 0) return;
    appendFiles(droppedFiles);
  };

  const handleEditDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsEditDragActive(false);
    const droppedFiles = Array.from(event.dataTransfer.files);
    if (droppedFiles.length === 0) return;
    appendEditFiles(droppedFiles);
  };

  const handleEditTextAreaDrop = (event: React.DragEvent<HTMLTextAreaElement>) => {
    event.preventDefault();
    setIsEditDragActive(false);
    const droppedFiles = Array.from(event.dataTransfer.files);
    if (droppedFiles.length === 0) return;
    appendEditFiles(droppedFiles);
  };

  const toggleAttachments = (noteId: string) => {
    setExpandedAttachmentNotes((current) => ({
      ...current,
      [noteId]: !current[noteId],
    }));
  };

  const handleDownloadAttachment = async (note: CompanyNote, attachment: CompanyNoteAttachment) => {
    const attachmentKey = `${note.id}:${attachment.id ?? "legacy"}`;
    setIsDownloadingAttachment(attachmentKey);
    try {
      const token = localStorage.getItem("serasa_token");
      const response = await fetch(attachmentUrl(note.id, attachment.id ?? null), {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });

      if (!response.ok) {
        throw new Error("Não foi possível baixar o anexo.");
      }

      const blob = await response.blob();
      const blobUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = blobUrl;
      link.download = attachment.fileName || "anexo";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(blobUrl);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao baixar o anexo.");
    } finally {
      setIsDownloadingAttachment(null);
    }
  };

  return (
    <section className="mt-8 rounded-[28px] border border-[#e9d8df] bg-[linear-gradient(180deg,rgba(255,255,255,0.98)_0%,rgba(250,245,247,0.92)_100%)] p-6 shadow-[0_18px_60px_-34px_rgba(97,32,53,0.35)]">
      <div className="flex flex-col gap-6 xl:grid xl:grid-cols-[360px_minmax(0,1fr)]">
        <div ref={composerRef} className="rounded-[24px] border border-[#ead8e0] bg-white/90 p-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[#612035] text-white shadow-lg shadow-[#612035]/15">
              <span className="material-icons-outlined text-[22px]">edit_note</span>
            </div>
            <div>
              <h2 className="text-[15px] font-sans font-bold uppercase tracking-[0.14em] text-[#612035]">
                Mural da Empresa
              </h2>
              <p className="mt-1 text-sm font-serif text-[#7f6c74]">
                Registre decisões, contexto comercial e observações do time.
              </p>
            </div>
          </div>

          <div className="mt-5 rounded-[22px] border border-[#f0e5e9] bg-[#fcfafb] p-4">
            <label className="mb-2 block text-[11px] font-sans font-bold uppercase tracking-[0.18em] text-[#9b7f8b]">
              Nova anotação
            </label>
            {replyTarget && (
              <div className="mb-3 rounded-[18px] border border-[#e5d2da] bg-[#f8f1f4] px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#8f7080]">
                      Citando {replyTarget.authorName}
                    </p>
                    <p className="mt-1 line-clamp-3 whitespace-pre-wrap font-serif text-sm leading-6 text-[#705b64]">
                      {replyTarget.content}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setReplyTarget(null)}
                    className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-[#e6d6dd] bg-white text-[#8f7080] transition hover:bg-[#f4ecef]"
                  >
                    <span className="material-icons-outlined text-[16px]">close</span>
                  </button>
                </div>
              </div>
            )}
            <textarea
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onPaste={handlePaste}
              onDragEnter={(event) => {
                event.preventDefault();
                setIsDragActive(true);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragActive(true);
              }}
              onDrop={handleTextAreaDrop}
              placeholder="Ex.: cliente pediu revisão do limite após apresentação de novos contratos."
              rows={6}
              maxLength={5000}
              className="min-h-[156px] w-full resize-y rounded-[18px] border border-[#eadde3] bg-white px-4 py-3 font-serif text-[15px] leading-6 text-grafite outline-none transition focus:border-[#b86f8d] focus:ring-4 focus:ring-[#612035]/10"
            />
            <div
              className={`mt-3 rounded-[18px] border border-dashed px-4 py-3 transition ${
                isDragActive ? "border-[#b86f8d] bg-[#fbf2f6]" : "border-[#e3d2da] bg-white/80"
              }`}
              onDragEnter={(event) => {
                event.preventDefault();
                setIsDragActive(true);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                setIsDragActive(true);
              }}
              onDragLeave={(event) => {
                event.preventDefault();
                if (event.currentTarget.contains(event.relatedTarget as Node)) return;
                setIsDragActive(false);
              }}
              onDrop={handleDrop}
            >
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#9b7f8b]">
                    Anexos opcionais
                  </p>
                  <p className="mt-1 text-xs font-serif text-[#8e7a83]">
                    Imagens, PDFs ou arquivos de apoio com até 5 MB cada.
                  </p>
                  <p className="mt-1 text-[11px] font-serif text-[#a08992]">
                    Você pode colar com Ctrl+V ou arrastar múltiplos arquivos para esta área.
                  </p>
                </div>
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-[#e3d2da] bg-white px-3 py-2 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#612035] transition hover:bg-[#f7eff2]">
                  <span className="material-icons-outlined text-[16px]">attach_file</span>
                  Escolher arquivos
                  <input
                    type="file"
                    multiple
                    className="hidden"
                    onChange={(event) => appendFiles(Array.from(event.target.files ?? []))}
                  />
                </label>
              </div>
              {selectedFiles.length > 0 && (
                <div className="mt-3 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-3 rounded-[14px] border border-[#eadde3] bg-[#fcfafb] px-3 py-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="rounded-full border border-[#e4d2da] bg-white px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#7a5565]">
                        {selectedFiles.length} anexo{selectedFiles.length !== 1 ? "s" : ""}
                      </span>
                      {selectedImagesCount > 0 && (
                        <span className="rounded-full border border-[#d7dff3] bg-[#f4f7ff] px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#5367a0]">
                          {selectedImagesCount} imagem{selectedImagesCount !== 1 ? "ns" : ""}
                        </span>
                      )}
                      {selectedOtherFilesCount > 0 && (
                        <span className="rounded-full border border-[#e7dfd1] bg-[#fbf7ef] px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#8a6c43]">
                          {selectedOtherFilesCount} arquivo{selectedOtherFilesCount !== 1 ? "s" : ""}
                        </span>
                      )}
                    </div>
                    <button
                      type="button"
                      onClick={clearSelectedFiles}
                      className="inline-flex items-center gap-1 rounded-full border border-[#e6d6dd] bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#8f7080] transition hover:bg-[#f4ecef]"
                    >
                      <span className="material-icons-outlined text-[14px]">layers_clear</span>
                      Remover todos
                    </button>
                  </div>
                  <div className="grid grid-cols-1 gap-3">
                  {sortedSelectedFiles.map((file) => {
                    const key = `${file.name}-${file.size}-${file.lastModified}`;
                    return (
                      <div key={key} className="rounded-[14px] border border-[#eadde3] bg-[#fcfafb] px-3 py-2">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-sans font-semibold text-grafite">{file.name}</p>
                            <p className="text-xs font-serif text-[#8e7a83]">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                          </div>
                          <button
                            type="button"
                            onClick={() => removeSelectedFile(file)}
                            className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-[#e6d6dd] bg-white text-[#8f7080] transition hover:bg-[#f4ecef]"
                          >
                            <span className="material-icons-outlined text-[16px]">close</span>
                          </button>
                        </div>
                        {isImageFile(file) && selectedFilePreviewUrls[key] && (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img
                            src={selectedFilePreviewUrls[key]}
                            alt={file.name}
                            className="mt-2 max-h-40 rounded-xl border border-[#eadde3] object-contain"
                          />
                        )}
                      </div>
                    );
                  })}
                  </div>
                </div>
              )}
            </div>
            <div className="mt-3 flex items-center justify-between gap-3">
              <p className="text-xs font-serif text-[#8e7a83]">
                Cada anotação registra autor, data e hora.
              </p>
              <span className="text-[11px] font-sans font-bold tracking-[0.16em] text-[#b08d9a]">
                {draft.length}/5000
              </span>
            </div>
            <button
              type="button"
              onClick={handleSubmit}
              disabled={!draft.trim() || isCreating}
              className="mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-2xl bg-[#612035] px-4 font-sans text-sm font-semibold text-white shadow-lg shadow-[#612035]/20 transition hover:bg-[#4f1b2b] disabled:cursor-not-allowed disabled:bg-[#c7aeb8] disabled:shadow-none"
            >
              <span className="material-icons-outlined text-[18px]">
                {isCreating ? "hourglass_top" : "add_comment"}
              </span>
              {isCreating ? "Salvando..." : "Salvar anotação"}
            </button>
          </div>
        </div>

        <div className="rounded-[24px] border border-[#ead8e0] bg-white/88 p-5 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 className="text-[15px] font-sans font-bold uppercase tracking-[0.14em] text-[#612035]">
                Histórico da Equipe
              </h3>
              <p className="mt-1 text-sm font-serif text-[#7f6c74]">
                Leituras e decisões ficam registradas em ordem cronológica.
              </p>
            </div>
            <div className="rounded-full border border-[#ecdfe4] bg-[#fbf7f8] px-3 py-1 text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#8b6f7a]">
              {notes.length} anotação{notes.length === 1 ? "" : "ões"}
            </div>
          </div>
          <div className="mt-4 flex flex-col gap-3 rounded-[18px] border border-[#efe2e7] bg-[#fcfafb] px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <p className="text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#9b7f8b]">
                Filtrar por autor
              </p>
              <p className="mt-1 text-xs font-serif text-[#8e7a83]">
                Mostre todas as interações ou foque em uma pessoa específica.
              </p>
            </div>
            <select
              value={selectedAuthor}
              onChange={(event) => setSelectedAuthor(event.target.value)}
              className="h-11 rounded-2xl border border-[#e5d4db] bg-white px-4 font-sans text-sm font-semibold text-[#612035] outline-none transition focus:border-[#b86f8d] focus:ring-4 focus:ring-[#612035]/10"
            >
              <option value="all">Todos os autores</option>
              {authors.map((authorEmail) => {
                const note = notes.find((item) => item.authorEmail === authorEmail);
                return (
                  <option key={authorEmail} value={authorEmail}>
                    {note?.authorName ?? authorEmail}
                  </option>
                );
              })}
            </select>
          </div>

          {isLoading ? (
            <div className="flex min-h-[220px] items-center justify-center">
              <p className="flex items-center gap-2 font-sans text-sm text-[#8b6f7a]">
                <span className="material-icons-outlined animate-spin text-[18px]">sync</span>
                Carregando anotações...
              </p>
            </div>
          ) : normalizedNotes.length === 0 ? (
            <div className="mt-6 flex min-h-[220px] flex-col items-center justify-center rounded-[22px] border border-dashed border-[#ead8e0] bg-[#fcfafb] px-6 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#f6eef1] text-[#8b5f70]">
                <span className="material-icons-outlined text-[26px]">history_edu</span>
              </div>
              <h4 className="mt-4 font-sans text-base font-semibold text-grafite">
                Ainda não há anotações para esta empresa
              </h4>
              <p className="mt-2 max-w-md font-serif text-sm leading-6 text-[#8a767f]">
                Use este espaço para registrar contexto comercial, alertas internos e decisões tomadas pela equipe.
              </p>
            </div>
          ) : (
            <div className="mt-6 max-h-[980px] space-y-4 overflow-y-auto pr-2">
              {normalizedNotes.map((note, index) => (
                <div key={note.id} className="relative pl-16">
                  {index !== normalizedNotes.length - 1 && (
                    <div className="absolute left-[23px] top-12 h-[calc(100%+12px)] w-px bg-gradient-to-b from-[#caa5b5] via-[#e8d7de] to-transparent" />
                  )}

                  <div className="absolute left-0 top-1 flex h-12 w-12 items-center justify-center rounded-2xl border border-[#ead8e0] bg-[#f8f1f4] font-sans text-sm font-bold tracking-[0.08em] text-[#612035]">
                    {getInitials(note.authorName)}
                  </div>

                  <article className="rounded-[22px] border border-[#ecdee4] bg-[linear-gradient(180deg,rgba(255,255,255,0.98)_0%,rgba(252,248,249,0.9)_100%)] p-4 shadow-[0_14px_40px_-34px_rgba(97,32,53,0.45)]">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <h4 className="font-sans text-sm font-semibold uppercase tracking-[0.12em] text-[#612035]">
                            {note.authorName}
                          </h4>
                          {index === 0 && selectedAuthor === "all" && (
                            <span className="rounded-full bg-green-100 px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.16em] text-green-700">
                              Mais recente
                            </span>
                          )}
                          <span className="rounded-full bg-[#f5eef1] px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.16em] text-[#8f7080]">
                            {formatNoteDate(note.createdAt)}
                          </span>
                        </div>
                        <p className="mt-1 text-xs font-serif text-[#8c7881]">{note.authorEmail}</p>
                      </div>

                      <div className="flex flex-wrap items-center gap-2">
                        <button
                          type="button"
                          onClick={() => setReplyTarget(note)}
                          className="inline-flex items-center gap-1 self-start rounded-full border border-[#dfd0d8] bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#7a5565] transition hover:bg-[#f7eff2]"
                        >
                          <span className="material-icons-outlined text-[14px]">reply</span>
                          Citar
                        </button>
                        {note.canDelete && (
                          <>
                            <button
                              type="button"
                              onClick={() => startEdit(note)}
                              disabled={editingNoteId === note.id}
                              className="inline-flex items-center gap-1 self-start rounded-full border border-[#dfd0d8] bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#7a5565] transition hover:bg-[#f7eff2] disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              <span className="material-icons-outlined text-[14px]">edit</span>
                              Editar
                            </button>
                            <button
                              type="button"
                              onClick={() => handleDelete(note.id)}
                              disabled={isDeleting}
                              className="inline-flex items-center gap-1 self-start rounded-full border border-red-200 bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                              <span className="material-icons-outlined text-[14px]">delete</span>
                              Apagar
                            </button>
                          </>
                        )}
                      </div>
                    </div>

                    {note.repliedToContent && (
                      <div className="mt-4 rounded-[18px] border border-[#d9d8f3] bg-[#f4f2ff] px-4 py-3">
                        <p className="text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#6a63a8]">
                          Citando {note.repliedToAuthorName}
                        </p>
                        <p className="mt-1 line-clamp-3 whitespace-pre-wrap font-serif text-sm leading-6 text-[#625d86]">
                          {note.repliedToContent}
                        </p>
                      </div>
                    )}

                    <div className="mt-4 rounded-[18px] border border-[#f0e5e9] bg-white/90 px-4 py-3">
                      {editingNoteId === note.id ? (
                        <div>
                          <textarea
                            value={editDraft}
                            onChange={(event) => setEditDraft(event.target.value)}
                            onPaste={handleEditPaste}
                            onDragEnter={(event) => {
                              event.preventDefault();
                              setIsEditDragActive(true);
                            }}
                            onDragOver={(event) => {
                              event.preventDefault();
                              setIsEditDragActive(true);
                            }}
                            onDrop={handleEditTextAreaDrop}
                            rows={5}
                            maxLength={5000}
                            className="min-h-[132px] w-full resize-y rounded-[16px] border border-[#eadde3] bg-white px-4 py-3 font-serif text-[15px] leading-6 text-grafite outline-none transition focus:border-[#b86f8d] focus:ring-4 focus:ring-[#612035]/10"
                          />
                          <div
                            className={`mt-3 rounded-[16px] border border-dashed px-4 py-3 transition ${
                              isEditDragActive ? "border-[#b86f8d] bg-[#fbf2f6]" : "border-[#e3d2da] bg-[#fcfafb]"
                            }`}
                            onDragEnter={(event) => {
                              event.preventDefault();
                              setIsEditDragActive(true);
                            }}
                            onDragOver={(event) => {
                              event.preventDefault();
                              setIsEditDragActive(true);
                            }}
                            onDragLeave={(event) => {
                              event.preventDefault();
                              if (event.currentTarget.contains(event.relatedTarget as Node)) return;
                              setIsEditDragActive(false);
                            }}
                            onDrop={handleEditDrop}
                          >
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                              <div>
                                <p className="text-[11px] font-sans font-bold uppercase tracking-[0.16em] text-[#9b7f8b]">
                                  Novos anexos
                                </p>
                                <p className="mt-1 text-xs font-serif text-[#8e7a83]">
                                  Adicione imagens, PDFs ou arquivos de apoio com até 5 MB cada.
                                </p>
                              </div>
                              <label className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-[#e3d2da] bg-white px-3 py-2 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#612035] transition hover:bg-[#f7eff2]">
                                <span className="material-icons-outlined text-[16px]">attach_file</span>
                                Escolher arquivos
                                <input
                                  type="file"
                                  multiple
                                  className="hidden"
                                  onChange={(event) => appendEditFiles(Array.from(event.target.files ?? []))}
                                />
                              </label>
                            </div>
                            {editFiles.length > 0 && (
                              <div className="mt-3 space-y-3">
                                <div className="flex flex-wrap items-center justify-between gap-3 rounded-[14px] border border-[#eadde3] bg-white px-3 py-2">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <span className="rounded-full border border-[#e4d2da] bg-white px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#7a5565]">
                                      {editFiles.length} novo{editFiles.length !== 1 ? "s" : ""} anexo{editFiles.length !== 1 ? "s" : ""}
                                    </span>
                                    {editImagesCount > 0 && (
                                      <span className="rounded-full border border-[#d7dff3] bg-[#f4f7ff] px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#5367a0]">
                                        {editImagesCount} imagem{editImagesCount !== 1 ? "ns" : ""}
                                      </span>
                                    )}
                                    {editOtherFilesCount > 0 && (
                                      <span className="rounded-full border border-[#e7dfd1] bg-[#fbf7ef] px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#8a6c43]">
                                        {editOtherFilesCount} arquivo{editOtherFilesCount !== 1 ? "s" : ""}
                                      </span>
                                    )}
                                  </div>
                                  <button
                                    type="button"
                                    onClick={clearEditFiles}
                                    className="inline-flex items-center gap-1 rounded-full border border-[#e6d6dd] bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#8f7080] transition hover:bg-[#f4ecef]"
                                  >
                                    <span className="material-icons-outlined text-[14px]">layers_clear</span>
                                    Remover todos
                                  </button>
                                </div>
                                <div className="grid grid-cols-1 gap-3">
                                  {sortedEditFiles.map((file) => {
                                    const key = fileKey(file);
                                    return (
                                      <div key={key} className="rounded-[14px] border border-[#eadde3] bg-white px-3 py-2">
                                        <div className="flex items-start justify-between gap-3">
                                          <div className="min-w-0">
                                            <p className="truncate text-sm font-sans font-semibold text-grafite">{file.name}</p>
                                            <p className="text-xs font-serif text-[#8e7a83]">{(file.size / 1024 / 1024).toFixed(2)} MB</p>
                                          </div>
                                          <button
                                            type="button"
                                            onClick={() => removeEditFile(file)}
                                            className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-[#e6d6dd] bg-white text-[#8f7080] transition hover:bg-[#f4ecef]"
                                          >
                                            <span className="material-icons-outlined text-[16px]">close</span>
                                          </button>
                                        </div>
                                        {isImageFile(file) && editFilePreviewUrls[key] && (
                                          // eslint-disable-next-line @next/next/no-img-element
                                          <img
                                            src={editFilePreviewUrls[key]}
                                            alt={file.name}
                                            className="mt-2 max-h-40 rounded-xl border border-[#eadde3] object-contain"
                                          />
                                        )}
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                            )}
                          </div>
                          <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                            <span className="text-[11px] font-sans font-bold tracking-[0.16em] text-[#b08d9a]">
                              {editDraft.length}/5000
                            </span>
                            <div className="flex flex-wrap items-center gap-2">
                              <button
                                type="button"
                                onClick={cancelEdit}
                                className="inline-flex items-center gap-1 rounded-full border border-[#dfd0d8] bg-white px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#7a5565] transition hover:bg-[#f7eff2]"
                              >
                                Cancelar
                              </button>
                              <button
                                type="button"
                                onClick={() => handleUpdate(note.id)}
                                disabled={!editDraft.trim() || isUpdating || isAddingAttachments}
                                className="inline-flex items-center gap-1 rounded-full border border-[#612035] bg-[#612035] px-3 py-1.5 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-white transition hover:bg-[#4f1b2b] disabled:cursor-not-allowed disabled:opacity-60"
                              >
                                <span className={`material-icons-outlined text-[14px] ${isUpdating || isAddingAttachments ? "animate-spin" : ""}`}>
                                  {isUpdating || isAddingAttachments ? "sync" : "save"}
                                </span>
                                {isUpdating || isAddingAttachments ? "Salvando..." : "Salvar edição"}
                              </button>
                            </div>
                          </div>
                        </div>
                      ) : (
                        <p className="whitespace-pre-wrap font-serif text-[15px] leading-7 text-grafite">
                          {note.content}
                        </p>
                      )}
                      {(note.attachments ?? []).length > 0 && (
                        <div className="mt-4 space-y-3">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-full border border-[#e4d2da] bg-white px-2.5 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#7a5565]">
                              {(note.attachments ?? []).length} anexo{(note.attachments ?? []).length !== 1 ? "s" : ""}
                            </span>
                            {(note.attachments ?? []).length > 1 && (
                              <button
                                type="button"
                                onClick={() => toggleAttachments(note.id)}
                                className="inline-flex items-center gap-1 rounded-full border border-[#dfd0d8] bg-white px-3 py-1 text-[10px] font-sans font-bold uppercase tracking-[0.14em] text-[#7a5565] transition hover:bg-[#f7eff2]"
                              >
                                <span className="material-icons-outlined text-[13px]">
                                  {expandedAttachmentNotes[note.id] ? "expand_less" : "expand_more"}
                                </span>
                                {expandedAttachmentNotes[note.id] ? "Recolher anexos" : "Expandir anexos"}
                              </button>
                            )}
                          </div>
                          {getVisibleAttachments(note.id, note.attachments ?? []).map((attachment) => {
                            const downloadKey = `${note.id}:${attachment.id ?? "legacy"}`;
                            const previewKey = attachment.id ? `${note.id}:${attachment.id}` : "";
                            return (
                              <div key={downloadKey}>
                                {isImageFile(attachment) && attachment.id && attachmentPreviewUrls[previewKey] && (
                                  <button
                                    type="button"
                                    onClick={() => handleDownloadAttachment(note, attachment)}
                                    className="block"
                                  >
                                    {/* eslint-disable-next-line @next/next/no-img-element */}
                                    <img
                                      src={attachmentPreviewUrls[previewKey]}
                                      alt={attachmentLabel(attachment)}
                                      className="max-h-[320px] rounded-[16px] border border-[#eadde3] object-contain"
                                    />
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => handleDownloadAttachment(note, attachment)}
                                  disabled={isDownloadingAttachment === downloadKey}
                                  className="mt-3 inline-flex items-center gap-2 rounded-full border border-[#d8d4ec] bg-[#f8f6ff] px-3 py-2 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-[#5d4ea1] transition hover:bg-[#f0ecff] disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                  <span className={`material-icons-outlined text-[16px] ${isDownloadingAttachment === downloadKey ? "animate-spin" : ""}`}>
                                    {isDownloadingAttachment === downloadKey ? "sync" : "attach_file"}
                                  </span>
                                  {isDownloadingAttachment === downloadKey ? "Baixando..." : attachmentLabel(attachment)}
                                </button>
                                {note.canDelete && (
                                  <button
                                    type="button"
                                    onClick={() => handleDeleteAttachment(note.id, attachment)}
                                    disabled={isDeletingAttachment}
                                    className="ml-2 mt-3 inline-flex items-center gap-2 rounded-full border border-red-200 bg-white px-3 py-2 text-[11px] font-sans font-semibold uppercase tracking-[0.14em] text-red-600 transition hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                                  >
                                    <span className="material-icons-outlined text-[16px]">delete</span>
                                    Remover anexo
                                  </button>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </article>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
