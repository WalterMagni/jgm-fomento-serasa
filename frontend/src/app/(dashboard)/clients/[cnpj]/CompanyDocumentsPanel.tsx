"use client";

import React, { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

type DocumentItem = {
  name: string;
  path: string;
  type: "folder" | "file";
  extension?: string;
  size?: number | null;
  modifiedAt?: string | null;
};

type CompanyDocumentsState = {
  mapped: boolean;
  rootPath?: string | null;
  currentPath?: string | null;
  parentPath?: string | null;
  mappedByName?: string | null;
  mappedAt?: string | null;
  items: DocumentItem[];
};

type DirectoryItem = {
  name: string;
  path: string;
  modifiedAt?: string | null;
};

type DirectoryBrowserState = {
  configured: boolean;
  basePath?: string | null;
  currentPath?: string | null;
  parentPath?: string | null;
  directories: DirectoryItem[];
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const TEST_PATH_PLACEHOLDER = "/Users/waltermagni/Desktop/Projeto Serasa/Gerenciador de Empresas/METALURGICA DJ- Andréia";

function getJsonHeaders(): Record<string, string> {
  const token = localStorage.getItem("serasa_token");
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

function getAuthHeaders(): Record<string, string> {
  const token = localStorage.getItem("serasa_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
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

function formatBytes(value?: number | null) {
  if (value == null) return "-";
  if (value < 1024) return `${value} B`;
  const units = ["KB", "MB", "GB", "TB"];
  let size = value / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toLocaleString("pt-BR", { maximumFractionDigits: 1 })} ${units[unitIndex]}`;
}

function formatDateTime(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

function fileIconMeta(item: DocumentItem) {
  if (item.type === "folder") return { icon: "folder", color: "text-amber-500", bg: "bg-amber-50" };
  const extension = (item.extension || "").toLowerCase();
  if (extension === "pdf") return { icon: "picture_as_pdf", color: "text-red-600", bg: "bg-red-50" };
  if (["xls", "xlsx", "csv"].includes(extension)) return { icon: "table_chart", color: "text-emerald-700", bg: "bg-emerald-50" };
  if (["doc", "docx"].includes(extension)) return { icon: "article", color: "text-blue-700", bg: "bg-blue-50" };
  if (["ppt", "pptx"].includes(extension)) return { icon: "slideshow", color: "text-orange-600", bg: "bg-orange-50" };
  if (["png", "jpg", "jpeg", "webp", "gif"].includes(extension)) return { icon: "image", color: "text-fuchsia-600", bg: "bg-fuchsia-50" };
  if (["zip", "rar", "7z"].includes(extension)) return { icon: "folder_zip", color: "text-yellow-700", bg: "bg-yellow-50" };
  if (["txt", "rtf"].includes(extension)) return { icon: "notes", color: "text-slate-600", bg: "bg-slate-50" };
  return { icon: "description", color: "text-gray-600", bg: "bg-gray-50" };
}

export function CompanyDocumentsPanel({ cnpj }: { cnpj: string }) {
  const cleanCnpj = cnpj.replace(/\D/g, "");
  const queryClient = useQueryClient();
  const [currentPath, setCurrentPath] = useState("");
  const [rootPathDraft, setRootPathDraft] = useState("");
  const [showRootEditor, setShowRootEditor] = useState(false);
  const [openingPath, setOpeningPath] = useState<string | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerPath, setPickerPath] = useState("");
  const [actionMenuPath, setActionMenuPath] = useState<string | null>(null);
  const [isDragActive, setIsDragActive] = useState(false);

  const documentsQuery = useQuery<CompanyDocumentsState>({
    queryKey: ["companyDocuments", cleanCnpj, currentPath],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (currentPath) params.set("path", currentPath);
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents?${params.toString()}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao carregar documentos"));
      }

      const data = await response.json();
      return { ...data, items: Array.isArray(data.items) ? data.items : [] };
    },
    enabled: !!cleanCnpj,
  });

  const documents = documentsQuery.data;
  const folders = useMemo(
    () => (documents?.items ?? []).filter((item) => item.type === "folder"),
    [documents?.items],
  );

  const folderPickerQuery = useQuery<DirectoryBrowserState>({
    queryKey: ["companyDocumentFolderPicker", cleanCnpj, pickerPath],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (pickerPath) params.set("path", pickerPath);
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/folder-picker?${params.toString()}`, {
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao carregar pastas"));
      }

      const data = await response.json();
      return { ...data, directories: Array.isArray(data.directories) ? data.directories : [] };
    },
    enabled: pickerOpen && !!cleanCnpj,
  });

  const mapRoot = useMutation({
    mutationFn: async (rootPath: string) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/root`, {
        method: "POST",
        headers: getJsonHeaders(),
        body: JSON.stringify({ rootPath }),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao mapear pasta"));
      }

      return response.json();
    },
    onSuccess: () => {
      toast.success("Pasta da empresa mapeada");
      setCurrentPath("");
      setRootPathDraft("");
      setShowRootEditor(false);
      setPickerOpen(false);
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao mapear pasta");
    },
  });

  const createDefaultFolder = useMutation({
    mutationFn: async () => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/default-folder`, {
        method: "POST",
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao criar pasta padrão"));
      }

      return response.json();
    },
    onSuccess: () => {
      toast.success("Pasta padrão criada e mapeada");
      setCurrentPath("");
      setShowRootEditor(false);
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao criar pasta padrão");
    },
  });

  const removeMapping = useMutation({
    mutationFn: async () => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/root`, {
        method: "DELETE",
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao remover mapeamento"));
      }
    },
    onSuccess: () => {
      toast.success("Mapeamento removido");
      setCurrentPath("");
      setRootPathDraft("");
      setShowRootEditor(false);
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao remover mapeamento");
    },
  });

  const handleRemoveMapping = () => {
    if (!window.confirm("Remover o mapeamento desta pasta? Os arquivos não serão apagados.")) {
      return;
    }
    removeMapping.mutate();
  };

  const renameItem = useMutation({
    mutationFn: async ({ item, newName }: { item: DocumentItem; newName: string }) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/item/rename`, {
        method: "PATCH",
        headers: getJsonHeaders(),
        body: JSON.stringify({ path: item.path, newName }),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao renomear item"));
      }
    },
    onSuccess: () => {
      toast.success("Item renomeado");
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao renomear item");
    },
  });

  const duplicateItem = useMutation({
    mutationFn: async (item: DocumentItem) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/item/duplicate`, {
        method: "POST",
        headers: getJsonHeaders(),
        body: JSON.stringify({ path: item.path }),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao duplicar item"));
      }
    },
    onSuccess: () => {
      toast.success("Item duplicado");
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao duplicar item");
    },
  });

  const openInExplorer = useMutation({
    mutationFn: async (item: DocumentItem) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/item/explorer`, {
        method: "POST",
        headers: getJsonHeaders(),
        body: JSON.stringify({ path: item.path }),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao abrir no explorador"));
      }
    },
    onSuccess: () => {
      toast.success("Item aberto no explorador de arquivos");
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao abrir no explorador");
    },
  });

  const uploadFiles = useMutation({
    mutationFn: async (files: File[]) => {
      const params = new URLSearchParams();
      if (documents?.currentPath) params.set("path", documents.currentPath);
      const formData = new FormData();
      files.forEach((file) => formData.append("files", file));

      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/upload?${params.toString()}`, {
        method: "POST",
        headers: getAuthHeaders(),
        body: formData,
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao enviar arquivo"));
      }
    },
    onSuccess: (_, files) => {
      toast.success(files.length === 1 ? "Arquivo enviado" : "Arquivos enviados");
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao enviar arquivo");
    },
  });

  const createFolder = useMutation({
    mutationFn: async (folderName: string) => {
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/folder`, {
        method: "POST",
        headers: getJsonHeaders(),
        body: JSON.stringify({ path: documents?.currentPath || "", newName: folderName }),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao criar pasta"));
      }
    },
    onSuccess: () => {
      toast.success("Pasta criada");
      queryClient.invalidateQueries({ queryKey: ["companyDocuments", cleanCnpj] });
    },
    onError: (error: Error) => {
      toast.error(error.message || "Erro ao criar pasta");
    },
  });

  const handleCopyPath = async (item: DocumentItem) => {
    setActionMenuPath(null);
    const fullPath = [documents?.rootPath?.replace(/\/$/, ""), item.path].filter(Boolean).join("/");
    try {
      await navigator.clipboard.writeText(fullPath);
      toast.success("Caminho do arquivo copiado");
    } catch {
      toast.error("Não foi possível copiar o caminho");
    }
  };

  const handleRenameFile = (item: DocumentItem) => {
    setActionMenuPath(null);
    const newName = window.prompt(item.type === "folder" ? "Novo nome da pasta" : "Novo nome do arquivo", item.name);
    if (!newName || newName.trim() === item.name) {
      return;
    }
    renameItem.mutate({ item, newName: newName.trim() });
  };

  const handleDuplicateFile = (item: DocumentItem) => {
    setActionMenuPath(null);
    duplicateItem.mutate(item);
  };

  const handleOpenExplorer = (item: DocumentItem) => {
    setActionMenuPath(null);
    openInExplorer.mutate(item);
  };

  const handleCreateFolder = () => {
    const folderName = window.prompt("Nome da nova pasta");
    if (!folderName || !folderName.trim()) {
      return;
    }
    createFolder.mutate(folderName.trim());
  };

  const handleUploadFiles = (files: File[]) => {
    if (!documents?.mapped || files.length === 0) return;
    uploadFiles.mutate(files);
  };

  const handleDrop = (event: React.DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragActive(false);
    handleUploadFiles(Array.from(event.dataTransfer.files));
  };

  const handlePaste = (event: React.ClipboardEvent<HTMLDivElement>) => {
    const files = Array.from(event.clipboardData.files);
    if (files.length === 0) return;
    event.preventDefault();
    handleUploadFiles(files);
  };

  const breadcrumb = useMemo(() => {
    const path = documents?.currentPath || "";
    if (!path) return [];
    return path.split("/").filter(Boolean).map((part, index, parts) => ({
      label: part,
      path: parts.slice(0, index + 1).join("/"),
    }));
  }, [documents?.currentPath]);

  const handleOpenFile = async (item: DocumentItem) => {
    try {
      setOpeningPath(item.path);
      const params = new URLSearchParams({ path: item.path });
      const response = await fetch(`${API_BASE_URL}/company/${cleanCnpj}/documents/file/open?${params.toString()}`, {
        method: "POST",
        headers: getAuthHeaders(),
      });

      if (!response.ok) {
        throw new Error(await extractErrorMessage(response, "Erro ao abrir arquivo"));
      }

      toast.success("Arquivo enviado para o aplicativo padrão");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Erro ao abrir arquivo");
    } finally {
      setOpeningPath(null);
    }
  };

  const renderRootEditor = !documents?.mapped || showRootEditor;

  return (
    <section className="mt-8 mb-6 rounded-xl border border-border-light dark:border-border-dark bg-surface-light dark:bg-surface-dark p-6 shadow-sm">
      <div className="mb-5 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="mb-1 flex items-center gap-2">
            <span className="material-icons-outlined text-primary">folder_open</span>
            <h2 className="font-serif text-xl font-bold text-primary">Documentos da Empresa</h2>
          </div>
          <p className="text-sm font-sans text-gray-500">
            Navegação restrita à pasta mapeada desta empresa.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <span
            className={`inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-sans font-bold ${
              documents?.mapped
                ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                : "border-red-200 bg-red-50 text-red-700"
            }`}
          >
            <span className="material-icons-outlined text-[16px]">
              {documents?.mapped ? "check_circle" : "error_outline"}
            </span>
            {documents?.mapped ? "Pasta mapeada" : "Pasta não mapeada"}
          </span>
          {documents?.mapped && (
            <>
              <button
                type="button"
                onClick={() => setShowRootEditor((current) => !current)}
                className="inline-flex h-9 items-center gap-2 rounded-lg border border-border-light px-3 text-xs font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50"
              >
                <span className="material-icons-outlined text-[16px]">folder_open</span>
                Alterar pasta
              </button>
              <button
                type="button"
                onClick={handleRemoveMapping}
                disabled={removeMapping.isPending}
                className="inline-flex h-9 items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 text-xs font-sans font-bold text-red-700 transition-colors hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <span className="material-icons-outlined text-[16px]">link_off</span>
                {removeMapping.isPending ? "Removendo..." : "Remover mapeamento"}
              </button>
            </>
          )}
        </div>
      </div>

      {renderRootEditor && (
        <div className="mb-5 rounded-lg border border-dashed border-red-200 bg-red-50/40 p-4 dark:border-red-900/50 dark:bg-red-950/10">
          <label className="mb-2 block text-xs font-sans font-bold uppercase tracking-wide text-gray-500">
            Caminho da pasta da empresa
          </label>
          <div className="flex flex-col gap-3 lg:flex-row">
            <input
              value={rootPathDraft}
              onChange={(event) => setRootPathDraft(event.target.value)}
              placeholder={documents?.rootPath || TEST_PATH_PLACEHOLDER}
              className="h-11 flex-1 rounded-lg border border-border-light bg-white px-3 font-mono text-sm text-grafite outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/10"
            />
            <button
              type="button"
              onClick={() => mapRoot.mutate(rootPathDraft.trim())}
              disabled={!rootPathDraft.trim() || mapRoot.isPending}
              className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-sans font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <span className="material-icons-outlined text-[18px]">link</span>
              {mapRoot.isPending ? "Mapeando..." : "Mapear pasta"}
            </button>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => {
                setPickerPath("");
                setPickerOpen(true);
              }}
              className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-border-light bg-white px-3 text-xs font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50"
            >
              <span className="material-icons-outlined text-[16px]">folder_open</span>
              Abrir explorador
            </button>
            <button
              type="button"
              onClick={() => createDefaultFolder.mutate()}
              disabled={createDefaultFolder.isPending}
              className="inline-flex h-9 items-center justify-center gap-2 rounded-lg border border-primary/20 bg-primary/10 px-3 text-xs font-sans font-bold text-primary transition-colors hover:bg-primary/15 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <span className="material-icons-outlined text-[16px]">create_new_folder</span>
              {createDefaultFolder.isPending ? "Criando..." : "Criar pasta padrão"}
            </button>
          </div>
          <p className="mt-2 text-xs font-sans text-red-700/80">
            O sistema só aceita pastas dentro do caminho base configurado em Sistema.
          </p>
        </div>
      )}

      {documents?.mapped && (
        <div className="mb-4 rounded-lg border border-border-light bg-gray-50/60 p-3 dark:bg-gray-900/20">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0">
              <p className="truncate font-mono text-xs text-gray-600">{documents.rootPath}</p>
              {documents.mappedByName && (
                <p className="mt-1 text-[11px] font-sans text-gray-400">
                  Mapeado por {documents.mappedByName} em {formatDateTime(documents.mappedAt)}
                </p>
              )}
            </div>
            <button
              type="button"
              onClick={() => documentsQuery.refetch()}
              className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg border border-border-light bg-white px-3 text-xs font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50"
            >
              <span className="material-icons-outlined text-[15px]">refresh</span>
              Atualizar
            </button>
          </div>
        </div>
      )}

      {documents?.mapped && (
        <div className="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
          <aside className="rounded-lg border border-border-light bg-white p-3 dark:bg-gray-900/20">
            <div className="mb-3 flex items-center gap-2 text-xs font-sans font-bold uppercase tracking-wide text-gray-400">
              <span className="material-icons-outlined text-[16px]">folder_open</span>
              Pastas
            </div>
            <button
              type="button"
              onClick={() => setCurrentPath("")}
              className={`mb-1 flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm font-sans transition-colors ${
                !documents.currentPath ? "bg-primary/10 font-bold text-primary" : "text-gray-600 hover:bg-gray-50"
              }`}
            >
              <span className="material-icons-outlined text-[17px]">home</span>
              Raiz
            </button>
            {folders.slice(0, 12).map((folder) => (
              <button
                key={folder.path}
                type="button"
                onClick={() => setCurrentPath(folder.path)}
                className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm font-sans text-gray-600 transition-colors hover:bg-gray-50"
              >
                <span className="material-icons-outlined text-[17px] text-amber-500">folder</span>
                <span className="truncate">{folder.name}</span>
              </button>
            ))}
            {folders.length === 0 && (
              <p className="px-2 py-3 text-xs font-sans text-gray-400">Sem subpastas nesta pasta.</p>
            )}
          </aside>

          <div
            className={`relative rounded-lg border bg-white outline-none transition-colors dark:bg-gray-900/20 ${
              isDragActive ? "border-primary ring-2 ring-primary/15" : "border-border-light"
            }`}
            tabIndex={0}
            onDragOver={(event) => {
              event.preventDefault();
              setIsDragActive(true);
            }}
            onDragLeave={() => setIsDragActive(false)}
            onDrop={handleDrop}
            onPaste={handlePaste}
          >
            {isDragActive && (
              <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center bg-primary/10 backdrop-blur-[1px]">
                <div className="rounded-xl border border-primary/25 bg-white px-5 py-4 text-center shadow-lg">
                  <span className="material-icons-outlined text-3xl text-primary">upload_file</span>
                  <p className="mt-1 text-sm font-sans font-bold text-primary">Solte para enviar nesta pasta</p>
                </div>
              </div>
            )}
            <div className="flex flex-col gap-3 border-b border-border-light bg-gray-50 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
              <nav className="flex min-w-0 flex-wrap items-center gap-1 text-sm font-sans">
                <button type="button" onClick={() => setCurrentPath("")} className="font-bold text-primary hover:underline">
                  Raiz
                </button>
                {breadcrumb.map((crumb) => (
                  <React.Fragment key={crumb.path}>
                    <span className="text-gray-300">/</span>
                    <button
                      type="button"
                      onClick={() => setCurrentPath(crumb.path)}
                      className="max-w-[220px] truncate text-gray-600 hover:text-primary hover:underline"
                    >
                      {crumb.label}
                    </button>
                  </React.Fragment>
                ))}
              </nav>
              {documents.parentPath != null && (
                <button
                  type="button"
                  onClick={() => setCurrentPath(documents.parentPath || "")}
                  className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg border border-border-light bg-white px-3 text-xs font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50"
                >
                  <span className="material-icons-outlined text-[15px]">arrow_upward</span>
                  Voltar
                </button>
              )}
              <div className="flex flex-wrap items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={handleCreateFolder}
                  disabled={createFolder.isPending}
                  className="inline-flex h-8 items-center justify-center gap-1.5 rounded-lg border border-border-light bg-white px-3 text-xs font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <span className="material-icons-outlined text-[15px]">create_new_folder</span>
                  {createFolder.isPending ? "Criando..." : "Nova pasta"}
                </button>
                <p className="text-[11px] font-sans text-gray-400">
                  Arraste ou cole arquivos aqui
                </p>
              </div>
            </div>

            <div className="overflow-x-auto overflow-y-visible">
              <table className="w-full min-w-[760px] text-left">
                <thead>
                  <tr className="border-b border-border-light bg-gray-50/80 text-[11px] font-sans font-bold uppercase tracking-wide text-gray-400">
                    <th className="px-4 py-3">Nome</th>
                    <th className="px-4 py-3">Modificado</th>
                    <th className="px-4 py-3">Tamanho</th>
                    <th className="px-4 py-3 text-right">Ações</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-light">
                  {documentsQuery.isLoading && (
                    <tr>
                      <td colSpan={4} className="px-4 py-10 text-center text-sm font-sans text-gray-500">
                        Carregando documentos...
                      </td>
                    </tr>
                  )}
                  {!documentsQuery.isLoading && documents.items.length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-4 py-10 text-center text-sm font-sans text-gray-500">
                        Nenhum arquivo nesta pasta.
                      </td>
                    </tr>
                  )}
                  {!documentsQuery.isLoading && documents.items.map((item, index) => {
                    const icon = fileIconMeta(item);
                    const actionPending = openingPath === item.path
                      || renameItem.isPending
                      || duplicateItem.isPending
                      || openInExplorer.isPending
                      || uploadFiles.isPending
                      || createFolder.isPending;
                    const openUp = index >= Math.max(0, documents.items.length - 2);

                    return (
                      <tr
                        key={item.path}
                        className="cursor-pointer transition-colors hover:bg-primary/5"
                        onClick={() => item.type === "folder" ? setCurrentPath(item.path) : handleOpenFile(item)}
                      >
                        <td className="px-4 py-3">
                          <div className="flex min-w-0 items-center gap-3">
                            <span className={`material-icons-outlined inline-flex h-9 w-9 items-center justify-center rounded-lg text-[22px] ${icon.bg} ${icon.color}`}>
                              {icon.icon}
                            </span>
                            <div className="min-w-0">
                              <p className="truncate text-sm font-sans font-bold text-grafite">{item.name}</p>
                              {item.type === "file" && item.extension && (
                                <p className="text-[11px] font-sans uppercase text-gray-400">{item.extension}</p>
                              )}
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3 text-sm font-sans text-gray-500">{formatDateTime(item.modifiedAt)}</td>
                        <td className="px-4 py-3 text-sm font-sans text-gray-500">{formatBytes(item.size)}</td>
                        <td className="px-4 py-3">
                          <div className="relative flex justify-end" onClick={(event) => event.stopPropagation()}>
                            <button
                              type="button"
                              onClick={() => setActionMenuPath((current) => current === item.path ? null : item.path)}
                              disabled={actionPending}
                              className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border-light text-gray-500 transition-colors hover:bg-gray-50 hover:text-primary disabled:opacity-50"
                              title="Opções"
                            >
                              <span className="material-icons-outlined text-[18px]">more_vert</span>
                            </button>
                            {actionMenuPath === item.path && (
                              <div className={`absolute right-0 z-30 w-56 overflow-hidden rounded-lg border border-border-light bg-white py-1 text-left shadow-xl ${openUp ? "bottom-9" : "top-9"}`}>
                                {item.type === "file" && (
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setActionMenuPath(null);
                                      handleOpenFile(item);
                                    }}
                                    className="flex w-full items-center gap-2 px-3 py-2 text-sm font-sans text-gray-700 transition-colors hover:bg-gray-50"
                                  >
                                    <span className="material-icons-outlined text-[16px]">open_in_new</span>
                                    Abrir
                                  </button>
                                )}
                                <button
                                  type="button"
                                  onClick={() => handleOpenExplorer(item)}
                                  className="flex w-full items-center gap-2 px-3 py-2 text-sm font-sans text-gray-700 transition-colors hover:bg-gray-50"
                                >
                                  <span className="material-icons-outlined text-[16px]">folder_open</span>
                                  Abrir no explorador
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleCopyPath(item)}
                                  className="flex w-full items-center gap-2 px-3 py-2 text-sm font-sans text-gray-700 transition-colors hover:bg-gray-50"
                                >
                                  <span className="material-icons-outlined text-[16px]">content_copy</span>
                                  Copiar caminho
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleRenameFile(item)}
                                  className="flex w-full items-center gap-2 px-3 py-2 text-sm font-sans text-gray-700 transition-colors hover:bg-gray-50"
                                >
                                  <span className="material-icons-outlined text-[16px]">drive_file_rename_outline</span>
                                  Renomear
                                </button>
                                <button
                                  type="button"
                                  onClick={() => handleDuplicateFile(item)}
                                  className="flex w-full items-center gap-2 px-3 py-2 text-sm font-sans text-gray-700 transition-colors hover:bg-gray-50"
                                >
                                  <span className="material-icons-outlined text-[16px]">file_copy</span>
                                  Duplicar
                                </button>
                              </div>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {pickerOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={() => setPickerOpen(false)}>
          <div className="absolute inset-0 bg-black/45 backdrop-blur-sm" />
          <div
            className="relative flex max-h-[82vh] w-full max-w-3xl flex-col overflow-hidden rounded-xl border border-border-light bg-white shadow-2xl dark:bg-gray-900"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-start justify-between gap-4 border-b border-border-light px-5 py-4">
              <div>
                <p className="text-xs font-sans font-bold uppercase tracking-[0.2em] text-primary">Documentos</p>
                <h3 className="mt-1 font-serif text-xl font-bold text-grafite dark:text-white">Selecionar pasta da empresa</h3>
                <p className="mt-1 max-w-2xl text-sm font-sans text-gray-500">
                  A navegação fica limitada à pasta base definida em Sistema.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setPickerOpen(false)}
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-700"
              >
                <span className="material-icons-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="border-b border-border-light bg-gray-50 px-5 py-3">
              <p className="truncate font-mono text-xs text-gray-500">
                {folderPickerQuery.data?.basePath || "Pasta base não configurada"}
              </p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <button
                  type="button"
                  onClick={() => setPickerPath("")}
                  className="text-sm font-sans font-bold text-primary hover:underline"
                >
                  Raiz
                </button>
                {(folderPickerQuery.data?.currentPath || "").split("/").filter(Boolean).map((part, index, parts) => {
                  const nextPath = parts.slice(0, index + 1).join("/");
                  return (
                    <React.Fragment key={nextPath}>
                      <span className="text-gray-300">/</span>
                      <button
                        type="button"
                        onClick={() => setPickerPath(nextPath)}
                        className="max-w-[180px] truncate text-sm font-sans text-gray-600 hover:text-primary hover:underline"
                      >
                        {part}
                      </button>
                    </React.Fragment>
                  );
                })}
              </div>
            </div>

            <div className="min-h-[280px] flex-1 overflow-y-auto p-4">
              {folderPickerQuery.isLoading && (
                <div className="flex h-48 items-center justify-center text-sm font-sans text-gray-500">
                  Carregando pastas...
                </div>
              )}
              {folderPickerQuery.isError && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm font-sans text-red-700">
                  {folderPickerQuery.error instanceof Error ? folderPickerQuery.error.message : "Não foi possível carregar as pastas."}
                </div>
              )}
              {!folderPickerQuery.isLoading && !folderPickerQuery.isError && (
                <div className="space-y-1">
                  {folderPickerQuery.data?.parentPath != null && (
                    <button
                      type="button"
                      onClick={() => setPickerPath(folderPickerQuery.data?.parentPath || "")}
                      className="mb-2 flex w-full items-center gap-3 rounded-lg border border-border-light px-3 py-2 text-left text-sm font-sans text-gray-600 transition-colors hover:bg-gray-50"
                    >
                      <span className="material-icons-outlined text-[20px]">arrow_upward</span>
                      Voltar pasta
                    </button>
                  )}
                  {folderPickerQuery.data?.directories.map((directory) => (
                    <button
                      key={directory.path}
                      type="button"
                      onClick={() => setPickerPath(directory.path)}
                      className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left transition-colors hover:bg-primary/5"
                    >
                      <span className="flex min-w-0 items-center gap-3">
                        <span className="material-icons-outlined text-[22px] text-amber-500">folder</span>
                        <span className="truncate text-sm font-sans font-bold text-grafite dark:text-white">{directory.name}</span>
                      </span>
                      <span className="text-xs font-sans text-gray-400">{formatDateTime(directory.modifiedAt)}</span>
                    </button>
                  ))}
                  {folderPickerQuery.data?.directories.length === 0 && (
                    <div className="rounded-lg border border-dashed border-border-light px-4 py-10 text-center text-sm font-sans text-gray-500">
                      Nenhuma subpasta aqui.
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="flex flex-col gap-3 border-t border-border-light bg-gray-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="truncate text-xs font-sans text-gray-500">
                Selecionada: <strong className="font-mono text-grafite dark:text-white">{folderPickerQuery.data?.currentPath || "Raiz"}</strong>
              </p>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setPickerOpen(false)}
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-border-light bg-white px-4 text-sm font-sans font-bold text-gray-600 transition-colors hover:bg-gray-50"
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  onClick={() => mapRoot.mutate(folderPickerQuery.data?.currentPath || ".")}
                  disabled={mapRoot.isPending || !folderPickerQuery.data?.configured || !folderPickerQuery.data?.currentPath}
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-sans font-bold text-white transition-colors hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <span className="material-icons-outlined text-[18px]">check</span>
                  {mapRoot.isPending ? "Selecionando..." : "Selecionar pasta"}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
