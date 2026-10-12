import { headers } from "next/headers";
import { SerasaReportPrint } from "../../../(dashboard)/clients/[cnpj]/SerasaReportPrint";
import type { ClientProfile } from "../../../../types/company-detail";

export const dynamic = "force-dynamic";

function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
}

/** @param analysisId consulta do histórico; sem ela, a mais recente */
async function getProfile(cnpj: string, analysisId: string | null) {
  const headerStore = await headers();
  const authHeader = headerStore.get("authorization");
  const consulta = analysisId && /^\d+$/.test(analysisId) ? `?analysisId=${analysisId}` : "";

  const response = await fetch(`${getApiBaseUrl()}/company/${cnpj}/profile${consulta}`, {
    headers: authHeader ? { Authorization: authHeader } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error("Nao foi possivel carregar o perfil da empresa para impressao.");
  }

  return (await response.json()) as ClientProfile;
}

export default async function PrintCompanyPage({
  params,
  searchParams,
}: {
  params: Promise<{ cnpj: string }>;
  searchParams: Promise<{ analysisId?: string }>;
}) {
  const { cnpj } = await params;
  const { analysisId } = await searchParams;
  const cleanCnpj = cnpj.replace(/\D/g, "");
  const profile = await getProfile(cleanCnpj, analysisId ?? null);

  return (
    <main style={{ padding: 0, background: "#fff" }}>
      <SerasaReportPrint profile={profile} />
    </main>
  );
}
