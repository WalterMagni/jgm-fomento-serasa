import { headers } from "next/headers";
import { SerasaReportPrint } from "../../../(dashboard)/clients/[cnpj]/SerasaReportPrint";
import type { ClientProfile } from "../../../../types/company-detail";

export const dynamic = "force-dynamic";

function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
}

async function getProfile(cnpj: string) {
  const headerStore = await headers();
  const authHeader = headerStore.get("authorization");

  const response = await fetch(`${getApiBaseUrl()}/company/${cnpj}/profile`, {
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
}: {
  params: Promise<{ cnpj: string }>;
}) {
  const { cnpj } = await params;
  const cleanCnpj = cnpj.replace(/\D/g, "");
  const profile = await getProfile(cleanCnpj);

  return (
    <main style={{ padding: 0, background: "#fff" }}>
      <SerasaReportPrint profile={profile} />
    </main>
  );
}
