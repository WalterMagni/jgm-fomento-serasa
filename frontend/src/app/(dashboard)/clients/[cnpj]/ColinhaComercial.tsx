"use client";

import { useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import Icon from "@/components/ui/Icon";
import { abrirColinhaComercial, iconeArquivo, tamanhoArquivo } from "@/lib/colinhaComercial";

/** O arquivo de referência da empresa, sem os bytes. */
type Colinha = {
  fileName: string | null;
  contentType: string;
  fileSize: number | null;
  uploadedByName: string | null;
  uploadedAt: string;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const MAX_BYTES = 5 * 1024 * 1024;
// SVG fica de fora: pode carregar script. O backend recusa o mesmo conjunto.
const TIPOS = ["application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp"];
const EXTENSOES = /\.(pdf|png|jpe?g|gif|webp)$/i;

function cabecalhoLogin(): Record<string, string> {
  const token = localStorage.getItem("serasa_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function mensagemDeErro(res: Response, padrao: string) {
  const corpo = await res.json().catch(() => ({}));
  return corpo.message || corpo.error || padrao;
}

function quando(valor: string) {
  const data = new Date(valor);
  return Number.isNaN(data.getTime())
    ? ""
    : data.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

/**
 * A "colinha" das informações comerciais: um documento por empresa (PDF ou imagem) com os dados
 * externos que o time consulta ao preencher as fichas. Fica no cabeçalho do painel. Enviar outro
 * substitui o anterior, depois de confirmar.
 */
export function ColinhaComercial({ cnpj }: { cnpj: string }) {
  const queryClient = useQueryClient();
  const entrada = useRef<HTMLInputElement>(null);
  const chave = ["companyCommercialReference", cnpj];

  const { data: colinha, isLoading } = useQuery<Colinha | null>({
    queryKey: chave,
    queryFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/commercial-information/reference`, { headers: cabecalhoLogin() });
      if (res.status === 204) return null;
      if (!res.ok) throw new Error(await mensagemDeErro(res, "Erro ao carregar o documento de referência"));
      return res.json();
    },
    enabled: !!cnpj,
  });

  const enviar = useMutation<Colinha, Error, File>({
    mutationFn: async arquivo => {
      const corpo = new FormData();
      corpo.append("file", arquivo);
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/commercial-information/reference`, {
        method: "PUT",
        headers: cabecalhoLogin(),
        body: corpo,
      });
      if (!res.ok) throw new Error(await mensagemDeErro(res, "Erro ao enviar o documento"));
      return res.json();
    },
    onSuccess: (salvo, arquivo) => {
      queryClient.setQueryData(chave, salvo);
      toast.success(colinha ? `Documento substituído por ${arquivo.name}` : "Documento de referência anexado");
    },
    onError: erro => toast.error(erro.message),
  });

  const remover = useMutation<void, Error, void>({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE_URL}/company/${cnpj}/commercial-information/reference`, {
        method: "DELETE",
        headers: cabecalhoLogin(),
      });
      if (!res.ok) throw new Error(await mensagemDeErro(res, "Erro ao remover o documento"));
    },
    onSuccess: () => {
      queryClient.setQueryData(chave, null);
      toast.success("Documento de referência removido");
    },
    onError: erro => toast.error(erro.message),
  });

  function escolher(arquivo: File | undefined) {
    if (entrada.current) entrada.current.value = "";
    if (!arquivo) return;
    if (!TIPOS.includes(arquivo.type) && !(arquivo.type === "" && EXTENSOES.test(arquivo.name))) {
      toast.error(`"${arquivo.name}" não é PDF nem imagem (PNG, JPG, GIF ou WEBP)`);
      return;
    }
    if (arquivo.size > MAX_BYTES) {
      toast.error(`"${arquivo.name}" passa de 5 MB`);
      return;
    }
    if (colinha && !window.confirm(`Substituir "${colinha.fileName ?? "o documento atual"}" por "${arquivo.name}"? O anterior deixa de existir.`)) {
      return;
    }
    enviar.mutate(arquivo);
  }

  const ocupado = enviar.isPending || remover.isPending;
  const botaoTexto = "cursor-pointer text-[11px] font-semibold text-gray-500 underline-offset-2 hover:text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50";

  return (
    <div className="mt-3">
      <input
        ref={entrada}
        type="file"
        accept=".pdf,.png,.jpg,.jpeg,.gif,.webp,application/pdf,image/png,image/jpeg,image/gif,image/webp"
        className="hidden"
        onChange={evento => escolher(evento.target.files?.[0])}
      />
      {isLoading ? (
        <div className="h-11 w-72 animate-pulse rounded-lg bg-gray-100 dark:bg-gray-800" />
      ) : colinha ? (
        <div className="inline-flex max-w-full flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-border-light bg-background-light px-3 py-2 dark:border-border-dark dark:bg-background-dark">
          <button
            type="button"
            onClick={() => abrirColinhaComercial(cnpj)}
            title="Abrir em nova aba"
            className="flex min-w-0 cursor-pointer items-center gap-2 text-left"
          >
            <Icon name={iconeArquivo(colinha.contentType)} size={18} className="shrink-0 text-primary" />
            <span className="min-w-0">
              <span className="block truncate text-sm font-semibold text-grafite hover:underline dark:text-white">
                {colinha.fileName || "Documento de referência"}
              </span>
              <span className="block text-[11px] text-gray-500">
                Enviado em {quando(colinha.uploadedAt)}
                {colinha.uploadedByName && ` por ${colinha.uploadedByName}`}
                {colinha.fileSize ? ` · ${tamanhoArquivo(colinha.fileSize)}` : ""}
              </span>
            </span>
          </button>
          <span className="flex items-center gap-2">
            <button type="button" onClick={() => entrada.current?.click()} disabled={ocupado} className={botaoTexto}>
              {enviar.isPending ? "enviando…" : "substituir"}
            </button>
            <span className="text-gray-300">|</span>
            <button
              type="button"
              onClick={() => window.confirm(`Remover "${colinha.fileName ?? "o documento"}"?`) && remover.mutate()}
              disabled={ocupado}
              className={botaoTexto}
            >
              remover
            </button>
          </span>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => entrada.current?.click()}
          disabled={ocupado}
          className="inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-lg border border-dashed border-gray-300 px-3 text-xs font-semibold text-gray-500 transition-colors hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-600"
        >
          <Icon name="upload_file" size={16} />
          {enviar.isPending ? "Enviando…" : "Anexar documento de referência"}
          <span className="font-normal text-gray-400">PDF ou imagem, até 5 MB</span>
        </button>
      )}
    </div>
  );
}
