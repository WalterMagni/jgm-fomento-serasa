export type Provedor = "cnpja" | "serasa";

export const NOME_PROVEDOR: Record<Provedor, string> = { cnpja: "CNPJ Já", serasa: "Serasa" };

/** Custo de uma consulta Serasa PJ (billing.serasa.cost-per-query no backend). */
export const CUSTO_CONSULTA_PJ = "R$ 6,33";

/**
 * Deixa legível o erro que veio do backend. Corpo bruto de provedor externo
 * (ex.: {"code":500,"message":"unexpected error","traceId":"..."}) nunca vai para a tela.
 */
function descreverErro(status: number, corpo: Record<string, unknown>, provedor: Provedor) {
  const bruto = typeof corpo?.message === "string" ? corpo.message.trim() : "";
  const corpoDoProvedor = bruto.startsWith("{") || bruto.startsWith("[") || bruto.length > 200;
  if (bruto && !corpoDoProvedor) return bruto;
  if (status === 503 || status >= 500) return `${NOME_PROVEDOR[provedor]} está fora do ar no momento.`;
  if (status === 404) return `CNPJ não encontrado na base do ${NOME_PROVEDOR[provedor]}.`;
  if (status === 429) return `Limite de consultas do ${NOME_PROVEDOR[provedor]} atingido. Tente em alguns minutos.`;
  return `Falha ao buscar dados no ${NOME_PROVEDOR[provedor]} (erro ${status}).`;
}

/** Consulta o CNPJ no provedor e grava na carteira. Lança Error com texto pronto para a tela. */
export async function consultarEmpresa(provedor: Provedor, cnpj: string) {
  const token = localStorage.getItem("serasa_token");
  const base = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
  let resposta: Response;
  try {
    resposta = await fetch(`${base}/company/enrich/${provedor}/${cnpj.replace(/\D/g, "")}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    });
  } catch {
    throw new Error("Não foi possível concluir a consulta. Verifique sua conexão e tente novamente.");
  }
  if (!resposta.ok) {
    const corpo = await resposta.json().catch(() => ({}));
    throw new Error(descreverErro(resposta.status, corpo, provedor));
  }
}
