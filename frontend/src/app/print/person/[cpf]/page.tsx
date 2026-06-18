import { headers } from "next/headers";
import { PersonReportPrint } from "../../../(dashboard)/individuals/[cpf]/PersonReportPrint";
import type { PersonAnalysisData } from "../../../../types/person-analysis";

export const dynamic = "force-dynamic";

function getApiBaseUrl() {
  return process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
}

async function getProfile(cpf: string) {
  const headerStore = await headers();
  const authHeader = headerStore.get("authorization");

  const response = await fetch(`${getApiBaseUrl()}/person/${cpf}/profile`, {
    headers: authHeader ? { Authorization: authHeader } : {},
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error("Nao foi possivel carregar o perfil da pessoa fisica para impressao.");
  }

  return (await response.json()) as PersonAnalysisData;
}

export default async function PrintPersonPage({
  params,
}: {
  params: Promise<{ cpf: string }>;
}) {
  const { cpf } = await params;
  const cleanCpf = cpf.replace(/\D/g, "");
  const profile = await getProfile(cleanCpf);

  return (
    <main style={{ padding: 0, background: "#fff" }}>
      <PersonReportPrint profile={profile} />
    </main>
  );
}
