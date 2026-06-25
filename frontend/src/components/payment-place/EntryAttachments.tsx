"use client";

import { useEffect, useRef, useState } from "react";
import Icon from "@/components/ui/Icon";
import { toast } from "sonner";
import {
  PaymentPlaceAttachment,
  fetchAttachmentObjectUrl,
  useDeleteEntryAttachment,
  useEntryAttachments,
  useUploadEntryAttachments,
} from "../../hooks/usePaymentPlace";

const MAX_BYTES = 5 * 1024 * 1024;

function formatBytes(n?: number | null) {
  if (!n || n <= 0) return "";
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

type FileKind = { icon: string; color: string; label: string; isImage: boolean };

function fileKind(contentType?: string | null, fileName?: string | null): FileKind {
  const ct = (contentType ?? "").toLowerCase();
  const name = (fileName ?? "").toLowerCase();
  if (ct.startsWith("image/") || /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name)) return { icon: "image", color: "#2956E0", label: "IMG", isImage: true };
  if (ct.includes("pdf") || name.endsWith(".pdf")) return { icon: "picture_as_pdf", color: "#E4002B", label: "PDF", isImage: false };
  if (ct.includes("sheet") || ct.includes("excel") || ct.includes("csv") || /\.(xlsx?|csv)$/.test(name)) return { icon: "table_chart", color: "#1E7A46", label: "XLS", isImage: false };
  if (ct.includes("word") || /\.(docx?)$/.test(name)) return { icon: "article", color: "#2563EB", label: "DOC", isImage: false };
  if (ct.includes("text") || name.endsWith(".txt")) return { icon: "description", color: "#64748B", label: "TXT", isImage: false };
  if (ct.includes("zip") || /\.(zip|rar|7z)$/.test(name)) return { icon: "folder_zip", color: "#B45309", label: "ZIP", isImage: false };
  return { icon: "insert_drive_file", color: "#94A3B8", label: "FILE", isImage: false };
}

function iconFor(contentType?: string | null, fileName?: string | null) {
  return fileKind(contentType, fileName).icon;
}

// Miniatura: imagem renderiza preview real; outros tipos mostram ícone colorido com a extensão.
function AttachmentThumb({ entryId, attachment }: { entryId: string; attachment: PaymentPlaceAttachment }) {
  const kind = fileKind(attachment.contentType, attachment.fileName);
  const [thumb, setThumb] = useState<string | null>(null);

  useEffect(() => {
    if (!kind.isImage) return;
    let url: string | null = null;
    let cancelled = false;
    fetchAttachmentObjectUrl(entryId, attachment.id)
      .then((u) => { if (cancelled) { URL.revokeObjectURL(u); return; } url = u; setThumb(u); })
      .catch(() => {});
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [entryId, attachment.id, kind.isImage]);

  if (kind.isImage && thumb) {
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={thumb} alt={attachment.fileName ?? "anexo"} className="h-full w-full object-cover" />;
  }
  return (
    <div className="flex h-full w-full flex-col items-center justify-center gap-1" style={{ color: kind.color }}>
      <Icon name={kind.icon} size={34} />
      <span className="text-[10px] font-bold tracking-wide">{kind.label}</span>
    </div>
  );
}

function validateFiles(files: File[]): File[] {
  const ok: File[] = [];
  for (const f of files) {
    if (f.size > MAX_BYTES) {
      toast.error(`"${f.name}" passa de 5 MB`);
      continue;
    }
    ok.push(f);
  }
  return ok;
}

async function openInNewTab(entryId: string, attachmentId: string) {
  const win = window.open("", "_blank");
  try {
    const url = await fetchAttachmentObjectUrl(entryId, attachmentId);
    if (win) win.location.href = url;
    else window.open(url, "_blank");
  } catch {
    win?.close();
    toast.error("Falha ao abrir anexo");
  }
}

/** Painel completo (no detalhe do título): enviar por clique/arrastar/colar, listar, abrir e remover. */
export function EntryAttachmentsPanel({ entryId }: { entryId: string }) {
  const attachmentsQuery = useEntryAttachments(entryId);
  const upload = useUploadEntryAttachments();
  const remove = useDeleteEntryAttachment();
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragOver, setDragOver] = useState(false);

  const items = attachmentsQuery.data ?? [];

  const send = (files: File[]) => {
    const valid = validateFiles(files);
    if (valid.length) upload.mutate({ entryId, files: valid });
  };

  return (
    <div>
      <div
        onClick={() => inputRef.current?.click()}
        onPaste={(e) => {
          const files = Array.from(e.clipboardData?.files ?? []);
          if (files.length) { e.preventDefault(); send(files); }
        }}
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={(e) => { e.preventDefault(); setDragOver(false); send(Array.from(e.dataTransfer.files ?? [])); }}
        tabIndex={0}
        className={`flex cursor-pointer flex-col items-center justify-center gap-1 rounded-xl border-2 border-dashed p-5 text-center transition-colors ${
          dragOver
            ? "border-primary bg-primary/5 dark:border-secondary dark:bg-secondary/10"
            : "border-border-light hover:bg-gray-50 dark:border-border-dark dark:hover:bg-white/5"
        }`}
      >
        <Icon name={upload.isPending ? "hourglass_empty" : "cloud_upload"} size={26} className="text-gray-400" />
        <p className="text-sm font-bold text-grafite dark:text-white">{upload.isPending ? "Enviando..." : "Arraste, cole (Ctrl+V) ou clique para anexar"}</p>
        <p className="text-[11px] text-gray-400">PDF, imagem, TXT… até 5 MB cada</p>
        <input
          ref={inputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => { send(Array.from(e.target.files ?? [])); e.target.value = ""; }}
        />
      </div>

      {items.length > 0 ? (
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((a) => (
            <div key={a.id} className="group relative overflow-hidden rounded-xl border border-border-light bg-white dark:border-border-dark dark:bg-background-dark">
              <button
                type="button"
                onClick={() => openInNewTab(entryId, a.id)}
                className="block w-full text-left"
                title="Abrir anexo"
              >
                <div className="flex h-24 items-center justify-center overflow-hidden bg-gray-50 dark:bg-white/5">
                  <AttachmentThumb entryId={entryId} attachment={a} />
                </div>
                <div className="px-2 py-1.5">
                  <p className="truncate text-xs font-medium text-grafite dark:text-white" title={a.fileName ?? "anexo"}>{a.fileName ?? "anexo"}</p>
                  <p className="text-[10px] text-gray-400">{formatBytes(a.fileSize)}{a.createdByName ? ` · ${a.createdByName.split(" ")[0]}` : ""}</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => { if (window.confirm("Remover este anexo?")) remove.mutate({ entryId, attachmentId: a.id }); }}
                disabled={remove.isPending}
                className="absolute right-1 top-1 flex h-7 w-7 items-center justify-center rounded-lg bg-white/90 text-gray-400 opacity-0 shadow-sm transition-all hover:text-red-600 group-hover:opacity-100 disabled:opacity-50 dark:bg-black/60 dark:hover:text-red-300"
                title="Remover anexo"
                aria-label="Remover anexo"
              >
                <Icon name="delete" size={16} />
              </button>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** Grade de anexos somente-leitura (abre no clique) — para modais/visões de empresa. */
export function EntryAttachmentsReadOnly({ entryId }: { entryId: string }) {
  const attachmentsQuery = useEntryAttachments(entryId);
  const items = attachmentsQuery.data ?? [];
  if (attachmentsQuery.isLoading) {
    return <p className="text-xs text-gray-400">Carregando anexos...</p>;
  }
  if (items.length === 0) {
    return <p className="text-xs text-gray-400">Nenhum anexo neste título.</p>;
  }
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
      {items.map((a) => (
        <button
          key={a.id}
          type="button"
          onClick={() => openInNewTab(entryId, a.id)}
          className="group overflow-hidden rounded-xl border border-border-light bg-white text-left transition-colors hover:border-primary dark:border-border-dark dark:bg-background-dark"
          title="Abrir anexo"
        >
          <div className="flex h-24 items-center justify-center overflow-hidden bg-gray-50 dark:bg-white/5">
            <AttachmentThumb entryId={entryId} attachment={a} />
          </div>
          <div className="px-2 py-1.5">
            <p className="truncate text-xs font-medium text-grafite dark:text-white" title={a.fileName ?? "anexo"}>{a.fileName ?? "anexo"}</p>
            <p className="text-[10px] text-gray-400">{formatBytes(a.fileSize)}</p>
          </div>
        </button>
      ))}
    </div>
  );
}

/** Indicativo de anexo (badge) para as listas. */
export function AttachmentBadge({ count, onClick }: { count: number; onClick?: () => void }) {
  if (!count) return null;
  const content = (
    <>
      <Icon name="attach_file" size={13} />
      {count}
    </>
  );
  if (onClick) {
    return (
      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); onClick(); }}
        title={`${count} anexo(s) — abrir`}
        className="inline-flex items-center gap-0.5 rounded-full bg-primary/10 px-1.5 py-0.5 text-[11px] font-bold text-primary transition-colors hover:bg-primary/20 dark:bg-secondary/15 dark:text-secondary"
      >
        {content}
      </button>
    );
  }
  return (
    <span className="inline-flex items-center gap-0.5 rounded-full bg-primary/10 px-1.5 py-0.5 text-[11px] font-bold text-primary dark:bg-secondary/15 dark:text-secondary" title={`${count} anexo(s)`}>
      {content}
    </span>
  );
}

/** Viewer em popup (inconclusivos/histórico): lista e exibe os anexos. */
export function AttachmentViewerModal({ entryId, title, onClose }: { entryId: string; title?: string; onClose: () => void }) {
  const attachmentsQuery = useEntryAttachments(entryId);
  const items = attachmentsQuery.data ?? [];
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [url, setUrl] = useState<string | null>(null);

  const active: PaymentPlaceAttachment | undefined = items.find((a) => a.id === selectedId) ?? items[0];

  useEffect(() => {
    if (!active) return;
    let revoked: string | null = null;
    let cancelled = false;
    fetchAttachmentObjectUrl(entryId, active.id)
      .then((u) => { if (cancelled) { URL.revokeObjectURL(u); return; } revoked = u; setUrl(u); })
      .catch(() => setUrl(null));
    return () => { cancelled = true; if (revoked) URL.revokeObjectURL(revoked); };
  }, [entryId, active]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-[120] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm" onClick={onClose}>
      <div className="flex h-[85vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl border border-border-light bg-surface-light shadow-2xl dark:border-border-dark dark:bg-surface-dark" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between gap-3 border-b border-border-light p-4 dark:border-border-dark">
          <div className="min-w-0">
            <h2 className="truncate text-sm font-bold text-grafite dark:text-white">Anexos {title ? `· ${title}` : ""}</h2>
            <p className="text-xs text-gray-500 dark:text-gray-400">{items.length} arquivo(s)</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-white/10" aria-label="Fechar">
            <Icon name="close" size={20} />
          </button>
        </div>
        <div className="flex min-h-0 flex-1">
          <div className="w-60 shrink-0 overflow-y-auto border-r border-border-light p-2 dark:border-border-dark">
            {attachmentsQuery.isLoading ? (
              <p className="p-2 text-xs text-gray-400">Carregando...</p>
            ) : items.length === 0 ? (
              <p className="p-2 text-xs text-gray-400">Sem anexos.</p>
            ) : (
              items.map((a) => {
                const sel = a.id === active?.id;
                return (
                  <button
                    key={a.id}
                    type="button"
                    onClick={() => setSelectedId(a.id)}
                    className={`flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left transition-colors ${sel ? "bg-primary/5 dark:bg-secondary/10" : "hover:bg-gray-50 dark:hover:bg-white/5"}`}
                  >
                    <Icon name={iconFor(a.contentType, a.fileName)} size={18} className={`${sel ? "text-primary dark:text-secondary" : "text-gray-400"}`} />
                    <span className="min-w-0 flex-1">
                      <span className={`block truncate text-xs font-bold ${sel ? "text-primary dark:text-secondary" : "text-grafite dark:text-white"}`}>{a.fileName ?? "anexo"}</span>
                      <span className="block text-[10px] text-gray-400">{formatBytes(a.fileSize)}</span>
                    </span>
                  </button>
                );
              })
            )}
          </div>
          <div className="min-w-0 flex-1 bg-gray-100 dark:bg-black/20">
            {active && url ? (
              <iframe src={url} title={active.fileName ?? "anexo"} className="h-full w-full" />
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-gray-400">{active ? "Carregando..." : "Selecione um anexo"}</div>
            )}
          </div>
        </div>
        {active ? (
          <div className="flex items-center justify-end gap-2 border-t border-border-light p-2 dark:border-border-dark">
            <button type="button" onClick={() => openInNewTab(entryId, active.id)} className="inline-flex h-8 items-center gap-1 rounded-lg border border-border-light px-3 text-xs font-bold text-gray-600 transition-colors hover:bg-gray-50 dark:border-border-dark dark:text-gray-300 dark:hover:bg-white/5">
              <Icon name="open_in_new" size={16} />Abrir em nova aba
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
