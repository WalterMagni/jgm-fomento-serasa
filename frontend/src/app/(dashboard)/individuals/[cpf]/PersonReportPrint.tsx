import type {
  PersonAnalysisData,
  PersonAddress,
  PFPefinRefinRecord,
  PFCollectionRecord,
  PFCheckRecord,
  PFNotaryRecord,
  PFJudgementFilingRecord,
  PFBankruptRecord,
} from "../../../../types/person-analysis";

function formatDate(raw?: string) {
  if (!raw) return "—";
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [year, month, day] = raw.split("-");
    return `${day}/${month}/${year}`;
  }
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? raw : date.toLocaleDateString("pt-BR");
}

function formatCurrency(value?: number) {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

function formatCpf(raw: string) {
  return raw.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
}

function formatCnpj(raw: string) {
  return raw.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
}

function formatPhone(areaCode?: number, phoneNumber?: number) {
  if (!areaCode && !phoneNumber) return "—";
  if (areaCode && phoneNumber) return `(${areaCode}) ${phoneNumber}`;
  return String(phoneNumber ?? areaCode);
}

function formatAddress(address?: PersonAddress | null) {
  if (!address) return "—";
  return [
    address.addressLine,
    address.addressNumber,
    address.addressComplement,
    address.district,
    [address.city, address.state].filter(Boolean).join(" / "),
    address.zipCode ? `CEP ${address.zipCode}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function sectionTitle() {
  return {
    fontSize: 14,
    fontWeight: 700,
    color: "#7d1d39",
    borderBottom: "1px solid #d6c9cf",
    paddingBottom: 6,
    marginBottom: 12,
  } as const;
}

function cardStyle() {
  return {
    border: "1px solid #d9d9df",
    borderRadius: 14,
    padding: 16,
    background: "#fff",
  } as const;
}

function labelStyle() {
  return {
    display: "block",
    fontSize: 10,
    fontWeight: 700,
    color: "#8a8f9c",
    textTransform: "uppercase" as const,
    marginBottom: 4,
    letterSpacing: 0.4,
  };
}

function valueStyle() {
  return {
    fontSize: 14,
    color: "#1f2937",
    lineHeight: 1.5,
  };
}

function SummaryCell({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ ...cardStyle(), padding: 14 }}>
      <span style={labelStyle()}>{label}</span>
      <div style={valueStyle()}>{value}</div>
    </div>
  );
}

function TableSection<T>({
  title,
  records,
  columns,
}: {
  title: string;
  records: T[];
  columns: { label: string; render: (record: T) => string }[];
}) {
  if (records.length === 0) return null;
  return (
    <section style={{ marginTop: 20 }}>
      <h3 style={sectionTitle()}>{title}</h3>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
        <thead>
          <tr style={{ background: "#f5f1f3" }}>
            {columns.map((column) => (
              <th
                key={column.label}
                style={{
                  textAlign: "left",
                  padding: "8px 10px",
                  border: "1px solid #e5e7eb",
                  color: "#6b7280",
                  fontSize: 10,
                  textTransform: "uppercase",
                  letterSpacing: 0.4,
                }}
              >
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {records.map((record, index) => (
            <tr key={index}>
              {columns.map((column) => (
                <td key={column.label} style={{ padding: "8px 10px", border: "1px solid #e5e7eb", color: "#111827" }}>
                  {column.render(record)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

export function PersonReportPrint({ profile }: { profile: PersonAnalysisData }) {
  const registration = profile.registration ?? {};
  const negatives = profile.negativeSummary ?? {};
  const facts = profile.facts ?? {};
  const partnerCompanies = profile.partnerCompanies?.partnershipResponse ?? [];

  return (
    <div
      style={{
        background: "#ffffff",
        color: "#111827",
        width: "100%",
        padding: "28px 32px 40px",
        fontFamily: "Georgia, 'Times New Roman', serif",
      }}
    >
      <header style={{ marginBottom: 24 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "#6b7280", letterSpacing: 1, marginBottom: 6 }}>
          JGM FOMENTO • PORTAL SERASA
        </div>
        <h1 style={{ fontSize: 28, margin: 0, color: "#2a1620" }}>Relatório de Pessoa Física</h1>
        <p style={{ fontSize: 13, color: "#6b7280", marginTop: 8 }}>
          Consulta realizada em {formatDate(profile.consultaEm)}
        </p>
      </header>

      <section style={{ marginBottom: 22 }}>
        <h2 style={sectionTitle()}>Identificação</h2>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <SummaryCell label="Nome" value={profile.personName || "—"} />
          <SummaryCell label="CPF" value={formatCpf(profile.cpf)} />
          <SummaryCell label="Telefone" value={formatPhone(registration.phones?.[0]?.areaCode, registration.phones?.[0]?.phoneNumber)} />
          <SummaryCell label="Endereço" value={formatAddress(registration.address)} />
        </div>
      </section>

      <section style={{ marginBottom: 22 }}>
        <h2 style={sectionTitle()}>Dados Pessoais</h2>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
          <SummaryCell label="Data de Nascimento" value={formatDate(registration.birthDate)} />
          <SummaryCell label="Estado Civil" value={registration.maritalStatusDescription ?? "—"} />
          <SummaryCell label="Escolaridade" value={registration.educationLevelDescription ?? "—"} />
          <SummaryCell label="Profissão" value={registration.jobDescription ?? registration.profession ?? "—"} />
          <SummaryCell label="Empresa" value={registration.corporateCompanyName ?? "—"} />
          <SummaryCell
            label="CNPJ Empresa"
            value={
              registration.corporateCompanyDocumentNumber
                ? formatCnpj(registration.corporateCompanyDocumentNumber.replace(/\D/g, ""))
                : "—"
            }
          />
        </div>
      </section>

      <section style={{ marginBottom: 22 }}>
        <h2 style={sectionTitle()}>Resumo de Negativos</h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12 }}>
          <SummaryCell label="PEFIN" value={`${negatives.pefin?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="REFIN" value={`${negatives.refin?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Cheques" value={`${negatives.check?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Protestos" value={`${negatives.notary?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Cobranças" value={`${negatives.collectionRecords?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Docs Roubados" value={`${facts.stolenDocuments?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Ações Judiciais" value={`${facts.judgementFilings?.summary?.count ?? 0} ocorrência(s)`} />
          <SummaryCell label="Falências" value={`${facts.bankrupts?.summary?.count ?? 0} ocorrência(s)`} />
        </div>
      </section>

      <TableSection<PFPefinRefinRecord>
        title="PEFIN"
        records={negatives.pefin?.pefinResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Credor", render: (r) => r.creditorName ?? "—" },
          { label: "Contrato", render: (r) => r.contractId ?? "—" },
          { label: "Natureza", render: (r) => r.legalNature ?? r.legalNatureId ?? "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFPefinRefinRecord>
        title="REFIN"
        records={negatives.refin?.refinResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Credor", render: (r) => r.creditorName ?? "—" },
          { label: "Contrato", render: (r) => r.contractId ?? "—" },
          { label: "Natureza", render: (r) => r.legalNature ?? r.legalNatureId ?? "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFCollectionRecord>
        title="Cobranças"
        records={negatives.collectionRecords?.collectionRecordsResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Credor", render: (r) => r.creditorName ?? "—" },
          { label: "Contrato", render: (r) => r.contractId ?? "—" },
          { label: "Natureza", render: (r) => r.legalNature ?? "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFCheckRecord>
        title="Cheques"
        records={negatives.check?.checkResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Banco", render: (r) => r.bankName ?? r.bankCode ?? "—" },
          { label: "Agência", render: (r) => r.agency ?? "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFNotaryRecord>
        title="Protestos"
        records={negatives.notary?.notaryResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Cartório", render: (r) => r.officeNumber ?? "—" },
          { label: "Cidade / UF", render: (r) => [r.city, r.federalUnit].filter(Boolean).join(" / ") || "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFJudgementFilingRecord>
        title="Ações Judiciais"
        records={facts.judgementFilings?.judgementFilingsResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Natureza", render: (r) => r.legalNature ?? r.legalNatureId ?? "—" },
          { label: "Distribuidor / Vara", render: (r) => [r.distributor, r.civilCourt].filter(Boolean).join(" / ") || "—" },
          { label: "Cidade / UF", render: (r) => [r.city, r.state].filter(Boolean).join(" / ") || "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      <TableSection<PFBankruptRecord>
        title="Falências / Recuperação Judicial"
        records={facts.bankrupts?.bankruptsResponse ?? []}
        columns={[
          { label: "Ocorrência", render: (r) => formatDate(r.occurrenceDate) },
          { label: "Inclusão", render: (r) => formatDate(r.inclusionDate) },
          { label: "Natureza", render: (r) => r.legalNature ?? r.legalNatureId ?? "—" },
          { label: "Cidade / UF", render: (r) => [r.city, r.state].filter(Boolean).join(" / ") || "—" },
          { label: "Valor", render: (r) => formatCurrency(r.amount) },
        ]}
      />

      {partnerCompanies.length > 0 && (
        <section style={{ marginTop: 20 }}>
          <h2 style={sectionTitle()}>Empresas onde participa</h2>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead>
              <tr style={{ background: "#f5f1f3" }}>
                {["Empresa", "CNPJ", "Participação", "Situação", "Atualização"].map((label) => (
                  <th
                    key={label}
                    style={{
                      textAlign: "left",
                      padding: "8px 10px",
                      border: "1px solid #e5e7eb",
                      color: "#6b7280",
                      fontSize: 10,
                      textTransform: "uppercase",
                      letterSpacing: 0.4,
                    }}
                  >
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {partnerCompanies.map((company, index) => {
                const cnpj = (company.businessDocument ?? "").replace(/\D/g, "");
                return (
                  <tr key={`${cnpj || "company"}-${index}`}>
                    <td style={{ padding: "8px 10px", border: "1px solid #e5e7eb" }}>{company.companyName ?? "—"}</td>
                    <td style={{ padding: "8px 10px", border: "1px solid #e5e7eb" }}>{cnpj.length === 14 ? formatCnpj(cnpj) : "—"}</td>
                    <td style={{ padding: "8px 10px", border: "1px solid #e5e7eb" }}>
                      {company.participationPercentage != null ? `${company.participationPercentage}%` : "—"}
                    </td>
                    <td style={{ padding: "8px 10px", border: "1px solid #e5e7eb" }}>{company.companyStatus ?? "—"}</td>
                    <td style={{ padding: "8px 10px", border: "1px solid #e5e7eb" }}>{formatDate(company.updateDate)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}
