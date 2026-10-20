import { toast } from "sonner";
import { reserveNewTab } from "./newTab";

/** Anexo da ficha comercial (PDF ou imagem). O arquivo vem à parte, por id. */
export type AnexoFicha = {
  id: string;
  fileName: string | null;
  contentType: string | null;
  fileSize: number | null;
};

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

async function buscarArquivo(cnpj: string, fichaId: string, anexo: AnexoFicha) {
  const token = localStorage.getItem("serasa_token");
  const resposta = await fetch(`${API_BASE_URL}/company/${cnpj}/commercial-information/${fichaId}/attachments/${anexo.id}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!resposta.ok) {
    const corpo = await resposta.json().catch(() => ({}));
    throw new Error(corpo.message || corpo.error || "Erro ao buscar o anexo");
  }
  return URL.createObjectURL(await resposta.blob());
}

/** Abre numa aba nova; PDF e imagem o navegador mostra direto. */
export async function abrirAnexoFicha(cnpj: string, fichaId: string, anexo: AnexoFicha) {
  const aba = reserveNewTab();
  try {
    const url = await buscarArquivo(cnpj, fichaId, anexo);
    aba.go(url);
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (erro) {
    aba.cancel();
    toast.error((erro as Error).message);
  }
}

export async function baixarAnexoFicha(cnpj: string, fichaId: string, anexo: AnexoFicha) {
  try {
    const url = await buscarArquivo(cnpj, fichaId, anexo);
    const link = document.createElement("a");
    link.href = url;
    link.download = anexo.fileName || "anexo";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (erro) {
    toast.error((erro as Error).message);
  }
}

export function tamanhoArquivo(bytes: number | null | undefined) {
  if (!bytes) return "";
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function iconeAnexo(contentType: string | null | undefined) {
  return contentType === "application/pdf" ? "picture_as_pdf" : "image";
}
