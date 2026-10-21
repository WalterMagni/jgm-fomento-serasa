import { toast } from "sonner";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";

/**
 * Baixa a planilha .xlsx das fichas no modelo do time (ficha vertical, uma coluna por parceiro,
 * um bloco por empresa, vencido em vermelho). Quem monta é o backend: o Excel abre sem o aviso
 * de "formato e extensão não batem" que o .xls em HTML dava.
 */
export async function baixarPlanilhaFichas(ids: string[]) {
  if (ids.length === 0) {
    toast.error("Nenhuma ficha para exportar");
    return;
  }
  const token = localStorage.getItem("serasa_token");
  try {
    const resposta = await fetch(`${API_BASE_URL}/company/commercial-information/export`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: JSON.stringify({ ids }),
    });
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => ({}));
      throw new Error(corpo.message || corpo.error || "Falha ao gerar a planilha");
    }
    const nome =
      /filename="?([^";]+)"?/.exec(resposta.headers.get("Content-Disposition") ?? "")?.[1] ??
      `informacoes-comerciais-${new Date().toISOString().slice(0, 10)}.xlsx`;
    const url = URL.createObjectURL(await resposta.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = nome;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch (erro) {
    toast.error((erro as Error).message);
  }
}
