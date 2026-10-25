import { toast } from "sonner";
import { reserveNewTab } from "./newTab";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

/**
 * Abre a colinha das informações comerciais (o arquivo de referência da empresa) numa aba nova.
 *
 * <p>Pede ao servidor um link curto (o ticket vale minutos) e aponta a aba para ele. A aba abre um
 * endereço de verdade, que o servidor manda mostrar na tela: abrir um blob local acabava em
 * download no Safari e no Chrome configurado para baixar PDF.</p>
 */
export async function abrirColinhaComercial(cnpj: string) {
  const aba = reserveNewTab();
  try {
    const token = localStorage.getItem("serasa_token");
    const resposta = await fetch(`${API_BASE_URL}/company/${cnpj}/commercial-information/reference/link`, {
      method: "POST",
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => ({}));
      throw new Error(corpo.message || corpo.error || "Erro ao abrir o arquivo");
    }
    const { ticket } = (await resposta.json()) as { ticket: string };
    aba.go(`${API_BASE_URL}/arquivos/${encodeURIComponent(ticket)}`);
  } catch (erro) {
    aba.cancel();
    toast.error((erro as Error).message);
  }
}

export function tamanhoArquivo(bytes: number | null | undefined) {
  if (!bytes) return "";
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function iconeArquivo(contentType: string | null | undefined) {
  return contentType === "application/pdf" ? "picture_as_pdf" : "image";
}
