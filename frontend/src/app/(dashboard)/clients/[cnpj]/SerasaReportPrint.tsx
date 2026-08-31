import React from "react";
import type {
  ClientProfile,
  NegativeOrMessage,
  QSAPartner,
  QSADirector,
  InquiryResult,
  TitleQuantityEntry,
  MonthDetailItem,
  EvolutionCommitmentsEntry,
  BusinessReferenceEntry,
  NotaryRecord,
  AverageDelayPeriodItem,
  PefinRecord,
  CollectionRecord,
  CheckRecord,
  CheckFilingRecord,
  RelationshipSuppliersPeriods,
} from "../../../../types/company-detail";

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtCNPJ(raw: string | undefined): string {
  if (!raw) return "—";
  const d = raw.replace(/\D/g, "");
  if (d.length === 14) return d.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, "$1.$2.$3/$4-$5");
  if (d.length === 11) return d.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4");
  return raw;
}

function fmtDate(raw: string | undefined): string {
  if (!raw) return "—";
  if (/^\d{8}$/.test(raw)) {
    const y = raw.slice(0, 4), m = raw.slice(4, 6), d = raw.slice(6, 8);
    return `${d}/${m}/${y}`;
  }
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(raw)) return raw;
  // YYYY-MM (histórico mensal Serasa) → "MM/YYYY"
  if (/^\d{4}-\d{2}$/.test(raw)) {
    const [y, m] = raw.split("-");
    return `${m}/${y}`;
  }
  if (/^\d{4}-\d{2}-\d{2}/.test(raw)) {
    const [y, m, d] = raw.slice(0, 10).split("-");
    return `${d}/${m}/${y}`;
  }
  const dt = new Date(raw);
  return isNaN(dt.getTime()) ? raw : dt.toLocaleDateString("pt-BR");
}

function fmtCurrency(value: number | undefined): string {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}

function fmtCount(value: string | number | undefined): string {
  if (value == null || value === "") return "—";
  const numeric = typeof value === "number" ? value : Number(String(value).replace(/\D/g, ""));
  return Number.isFinite(numeric)
    ? new Intl.NumberFormat("pt-BR").format(numeric)
    : String(value);
}

/** Formata valor em R$ mil (já em milhares — sem multiplicar) com separador de milhar */
function fmtMil(value: number | undefined): string {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(value);
}

/** Formata um telefone que pode ser string ou objeto {ddd, phoneNumber, numero, ...} */
function fmtPhone(phone: unknown): string {
  if (typeof phone === "string") return phone.trim();
  if (phone && typeof phone === "object") {
    const p = phone as Record<string, unknown>;
    const ddd = String(p.ddd ?? p.areaCode ?? "").trim();
    const num = String(p.phoneNumber ?? p.numero ?? p.number ?? "").trim();
    if (ddd && num) return `(${ddd}) ${num}`;
    if (num) return num;
    if (ddd) return ddd;
  }
  return "";
}

/**
 * Separa o campo serasaActivityCode da Serasa em código e descrição.
 * Formato: "S030300 CONSULTORIA, PLANEJ EMPRESARIAL,ADM FRANQUIA"
 */
function parseActivityCode(raw: string | undefined): { code: string; description: string } {
  if (!raw) return { code: "", description: "" };
  const spaceIdx = raw.indexOf(" ");
  if (spaceIdx === -1) return { code: raw, description: "" };
  return { code: raw.slice(0, spaceIdx).trim(), description: raw.slice(spaceIdx + 1).trim() };
}

const RISK_LABELS: Record<string, string> = {
  "1": "MUITO ALTO RISCO",
  "2": "ALTO RISCO",
  "3": "RISCO MODERADO",
  "4": "BAIXO RISCO",
  "5": "MUITO BAIXO RISCO",
};

const RISK_DECISIONS: Record<string, string> = {
  "1": "NEGAR CRÉDITO",
  "2": "ANÁLISE CRITERIOSA",
  "3": "CRÉDITO CONDICIONADO",
  "4": "CRÉDITO APROVADO",
  "5": "CRÉDITO LIBERADO",
};

// ── Sub-components ────────────────────────────────────────────────────────────

function SectionHeader({ title, date, suffix }: { title: string; date?: string; suffix?: string }) {
  const datePart = date ? ` (ATUALIZAÇÃO EM ${fmtDate(date)})` : "";
  return (
    <div style={{
      background: "#ddd",
      padding: "3px 8px",
      fontWeight: "bold",
      fontSize: "9pt",
      marginTop: "10px",
      marginBottom: "5px",
      borderLeft: "4px solid #000",
      letterSpacing: "0.03em",
    }}>
      {title}{datePart}{suffix ? ` ${suffix}` : ""}
    </div>
  );
}

function SectionDivider() {
  return <hr style={{ border: "none", borderTop: "1px solid #aaa", margin: "6px 0" }} />;
}

/** Lê a mensagem de um campo CONCENTRE/RECHEQUE, que pode ser texto ou NegativeSection */
function resolveNegOrMsg(val: NegativeOrMessage | { summary?: { count: number; balance: number } } | { message?: string; updateDate?: string } | undefined): { msg: string; update?: string; count?: number; balance?: number } {
  if (!val) return { msg: "" };
  const v = val as NegativeOrMessage;
  if (v.summary) {
    const msg = v.summary.count === 0 ? "NADA CONSTA" : `${v.summary.count} ocorrência(s)`;
    return { msg, count: v.summary.count, balance: v.summary.balance, update: v.updateDate };
  }
  if (v.message) return { msg: v.message, update: v.updateDate };
  return { msg: "" };
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", gap: "6px", margin: "2px 0", fontSize: "8.5pt" }}>
      <span style={{ minWidth: "210px", fontWeight: "bold", flexShrink: 0 }}>{label}:</span>
      <span style={{ wordBreak: "break-word" }}>{value}</span>
    </div>
  );
}

function normalizeRelationshipLabel(raw?: string): string {
  return (raw ?? "")
    .replace(/:/g, "")
    .replace(/MES/g, "M")
    .replace(/ANOS/g, "A")
    .replace(/ANO/g, "A")
    .trim();
}

// ── Main component ────────────────────────────────────────────────────────────

interface SerasaReportPrintProps {
  profile: ClientProfile;
  organizationName?: string;
}

export function SerasaReportPrint({
  profile,
  organizationName = "JGM Fomento Mercantil Ltda.",
}: SerasaReportPrintProps) {
  const { companyDetail, creditAnalysis: ca } = profile;
  const cr   = ca?.creditRatingDetails;
  const qsa  = ca?.partnerDetails;
  const neg  = ca?.negativeSummary;
  const inq  = ca?.inquiryHistory;
  const ph   = ca?.paymentHistory;
  const checkFilings: CheckFilingRecord[] = ca?.checkFilingsHistorical?.checkFilingsHistoricalResponse ?? [];

  const cnpj        = ca?.cnpj || companyDetail?.documentNumber || "";
  const companyName = cr?.companyName || companyDetail?.companyName || "—";
  const now         = new Date().toLocaleDateString("pt-BR");
  const nowTime     = new Date().toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });

  const partners:  QSAPartner[]  = qsa?.partnerCompleteReport?.partnersList  ?? [];
  const directors: QSADirector[] = qsa?.directorCompleteReport?.directorsList ?? [];
  const inquiries: InquiryResult[] = inq?.inquiryCompanyResponse?.results ?? [];
  const predecessors = cr?.predecessorList ?? [];
  const inquiryHistory = inq?.inquiryCompanyResponse?.quantity?.historical ?? [];
  const maxInquiryOccurrences = inquiryHistory.reduce((max, item) => Math.max(max, item.occurrences ?? 0), 0);

  const negItems = [
    { label: "PEFIN (Pendências Financeiras)",  data: neg?.pefin?.summary },
    { label: "REFIN (Refinanciamento)",          data: neg?.refin?.summary },
    { label: "CHEQUES SEM FUNDO",               data: neg?.check?.summary },
    { label: "PROTESTOS EM CARTÓRIO",            data: neg?.notary?.summary },
    { label: "COBRANÇAS",                        data: neg?.collectionRecords?.summary },
    { label: "FALÊNCIAS / CONCORDATAS",          data: inq?.bankrupts?.summary },
    { label: "AÇÕES JUDICIAIS",                  data: inq?.judgementFilings?.summary },
  ];

  const totalNegCount = negItems.reduce((acc, x) => acc + (x.data?.count ?? 0), 0);
  const totalNegValue = negItems.reduce((acc, x) => acc + (x.data?.balance ?? 0), 0);

  // CONCENTRE — tenta múltiplas chaves (depende da versão do payload Serasa)
  const concentreVal = inq?.concentre ?? inq?.concentreRecords
    ?? (neg as Record<string, unknown>)?.['concentre'] as NegativeOrMessage | undefined;

  // RECHEQUE — tenta múltiplas chaves
  const recheckVal = inq?.recheckOccurrences ?? inq?.recheckData ?? inq?.checkOccurrences
    ?? (neg as Record<string, unknown>)?.['recheque'] as NegativeOrMessage | undefined;

  const concentreInfo = resolveNegOrMsg(concentreVal);
  const recheckInfo   = resolveNegOrMsg(recheckVal);

  const monoStyle: React.CSSProperties = {
    fontFamily: "'Courier New', Courier, monospace",
    fontSize: "9pt",
    lineHeight: "1.5",
    color: "#000",
    background: "#fff",
  };

  return (
    <div id="serasa-print-report" className="hidden print:block" style={monoStyle}>

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* CABEÇALHO                                                          */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <table style={{ width: "100%", borderBottom: "2px solid #000", paddingBottom: "5px", marginBottom: "6px" }}>
        <tbody>
          <tr>
            <td style={{ fontSize: "8pt" }}>Confidencial p/ {organizationName}</td>
            <td style={{ textAlign: "right", fontSize: "8pt" }}>
              Impressão: {now} {nowTime} &nbsp;&nbsp; Página: 1
            </td>
          </tr>
          <tr>
            <td colSpan={2} style={{ paddingTop: "4px" }}>
              <div style={{ fontWeight: "bold", fontSize: "11pt" }}>
                SERASA SOLUÇÕES EM INFORMAÇÃO — CNPJ: {fmtCNPJ(cnpj)}
              </div>
              <div style={{ fontWeight: "bold", fontSize: "10pt", marginTop: "2px" }}>
                RELATO — RELATÓRIO DE COMPORTAMENTO EM NEGÓCIOS &nbsp;|&nbsp; VALORES EM REAIS
              </div>
              {ca?.analysisDate && (
                <div style={{ fontSize: "8pt", marginTop: "2px" }}>
                  Análise realizada em: {fmtDate(ca.analysisDate)}
                  {ca.consultaEm && ` &nbsp;|&nbsp; Consultado em: ${new Date(ca.consultaEm).toLocaleString("pt-BR")}`}
                </div>
              )}
            </td>
          </tr>
        </tbody>
      </table>

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* SEÇÃO 1 — IDENTIFICAÇÃO / LOCALIZAÇÃO                             */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionHeader
        title="IDENTIFICAÇÃO / LOCALIZAÇÃO"
        date={cr?.updateDate}
      />

      <InfoRow label="CNPJ" value={fmtCNPJ(cnpj)} />
      {cr?.nireNumber && (
        <InfoRow label="NIRE" value={cr.nireNumber} />
      )}
      <InfoRow label="RAZÃO SOCIAL" value={companyName} />
      {(cr?.companyAlias || companyDetail?.alias) && (
        <InfoRow label="NOME FANTASIA" value={cr?.companyAlias || companyDetail?.alias || "—"} />
      )}
      {cr?.partnership && (
        <InfoRow label="TIPO DE SOCIEDADE" value={cr.partnership} />
      )}
      {(cr?.companyRegister || companyDetail?.id) && (
        <InfoRow
          label="REGISTRO / FUNDAÇÃO"
          value={[
            cr?.companyRegister,
            cr?.companyFoundation
              ? `EM: ${fmtDate(cr.companyFoundation)}`
              : companyDetail?.founded
              ? `EM: ${fmtDate(companyDetail.founded)}`
              : undefined,
          ].filter(Boolean).join("  ")}
        />
      )}

      {/* Address */}
      {(cr?.address?.addressLine || companyDetail?.street) && (
        <InfoRow
          label="ENDEREÇO"
          value={[
            cr?.address?.addressLine || [companyDetail?.street, companyDetail?.number].filter(Boolean).join(", "),
            cr?.address?.district || companyDetail?.district,
            [
              cr?.address?.city || companyDetail?.city,
              cr?.address?.state || companyDetail?.state,
            ].filter(Boolean).join(" — "),
            `CEP: ${cr?.address?.zipCode || companyDetail?.zip || companyDetail?.zipCode || "—"}`,
          ].filter(Boolean).join("  |  ")}
        />
      )}
      {(() => {
        const phone = cr?.phone ? fmtPhone(cr.phone) : "";
        return (
          <InfoRow
            label="TELEFONE(S)"
            value={phone || "Não informado"}
          />
        );
      })()}
      {cr?.companyUrl && (
        <InfoRow label="SITE" value={cr.companyUrl} />
      )}

      {/* Activity */}
      {cr?.cnae && (
        <InfoRow label="CNAE" value={cr.cnae} />
      )}
      {cr?.serasaActiveCode && (() => {
        const { code, description } = parseActivityCode(cr.serasaActiveCode);
        return (
          <>
            {code && <InfoRow label="COD. ATIVIDADE SERASA" value={code} />}
            {description && <InfoRow label="RAMO DE ATIVIDADE" value={description} />}
          </>
        );
      })()}
      {cr?.economicActivity && (
        <InfoRow label="ATIVIDADE ECONÔMICA" value={cr.economicActivity} />
      )}
      {companyDetail?.statusText && (
        <InfoRow label="SITUAÇÃO" value={companyDetail.statusText.toUpperCase()} />
      )}
      {predecessors.length > 0 && (
        <>
          <InfoRow label="QTD. NOMES ANTERIORES" value={String(predecessors.length)} />
          <div style={{ marginTop: "6px", marginBottom: "4px" }}>
            <div style={{ fontSize: "8pt", fontWeight: "bold", marginBottom: "3px" }}>
              HISTÓRICO DE RAZÕES SOCIAIS:
            </div>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left", padding: "2px 4px", width: "18%" }}>DATA</th>
                  <th style={{ textAlign: "left", padding: "2px 4px" }}>NOME ANTERIOR</th>
                </tr>
              </thead>
              <tbody>
                {predecessors.map((item, index) => (
                  <tr key={`${item.predecessorName ?? "predecessor"}-${item.predecessorDate ?? index}`} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 4px", whiteSpace: "nowrap" }}>
                      {fmtDate(item.predecessorDate)}
                    </td>
                    <td style={{ padding: "2px 4px", fontWeight: "bold" }}>
                      {item.predecessorName || "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* SEÇÃO 2 — CONTROLE SOCIETÁRIO                                      */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionDivider />
      <SectionHeader
        title="CONTROLE SOCIETÁRIO"
        date={qsa?.companyData?.informationUpdateDate}
        suffix="(VALORES EM R$)"
      />

      {qsa?.companyData ? (
        <>
          <InfoRow
            label="CAPITAL SOCIAL / REALIZADO"
            value={`${fmtCurrency(qsa.companyData.socialCapitalValue)}   REALIZ: ${fmtCurrency(qsa.companyData.accomplishedValue)}`}
          />
          {(qsa.companyData.countryOrigin || qsa.companyData.controlType || qsa.companyData.nature) && (
            <InfoRow
              label="ORIGEM / CONTROLE / NATUREZA"
              value={[qsa.companyData.countryOrigin, qsa.companyData.controlType, qsa.companyData.nature]
                .filter(Boolean)
                .join("  /  ")}
            />
          )}
        </>
      ) : (
        <p style={{ margin: "3px 0", fontSize: "8.5pt" }}>Dados societários não disponíveis.</p>
      )}

      {partners.length > 0 && (
        <div style={{ marginTop: "6px" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                <th style={{ textAlign: "left",  padding: "2px 4px" }}>CPF/CNPJ</th>
                <th style={{ textAlign: "left",  padding: "2px 4px" }}>ACIONISTA / SÓCIO</th>
                <th style={{ textAlign: "center",padding: "2px 4px" }}>ENTRADA</th>
                <th style={{ textAlign: "center",padding: "2px 4px" }}>NACION.</th>
                <th style={{ textAlign: "right", padding: "2px 4px" }}>% VOT.</th>
                <th style={{ textAlign: "right", padding: "2px 4px" }}>% TOTAL</th>
              </tr>
            </thead>
            <tbody>
              {partners.map((p: QSAPartner, i: number) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 4px" }}>{p.documentId ? fmtCNPJ(p.documentId) : "—"}</td>
                  <td style={{ padding: "2px 4px", fontWeight: "bold" }}>{p.name}</td>
                  <td style={{ padding: "2px 4px", textAlign: "center" }}>{fmtDate(p.sinceDate)}</td>
                  <td style={{ padding: "2px 4px", textAlign: "center" }}>{p.nationality || "—"}</td>
                  <td style={{ padding: "2px 4px", textAlign: "right" }}>
                    {p.capitalVoterValue != null ? `${p.capitalVoterValue}%` : "—"}
                  </td>
                  <td style={{ padding: "2px 4px", textAlign: "right" }}>
                    {p.capitalTotalValue != null ? `${p.capitalTotalValue}%` : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* SEÇÃO 3 — ADMINISTRAÇÃO                                            */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {directors.length > 0 && (
        <>
          <SectionDivider />
          <SectionHeader title="ADMINISTRAÇÃO" date={qsa?.companyData?.informationUpdateDate} />
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                <th style={{ textAlign: "left",  padding: "2px 4px" }}>CPF/CNPJ</th>
                <th style={{ textAlign: "left",  padding: "2px 4px" }}>ADMINISTRADOR</th>
                <th style={{ textAlign: "center",padding: "2px 4px" }}>CARGO</th>
                <th style={{ textAlign: "center",padding: "2px 4px" }}>NACION.</th>
                <th style={{ textAlign: "center",padding: "2px 4px" }}>ENTRADA</th>
              </tr>
            </thead>
            <tbody>
              {directors.map((d: QSADirector, i: number) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 4px" }}>{d.documentId ? fmtCNPJ(d.documentId) : "—"}</td>
                  <td style={{ padding: "2px 4px", fontWeight: "bold" }}>{d.name}</td>
                  <td style={{ padding: "2px 4px", textAlign: "center" }}>{d.role || "—"}</td>
                  <td style={{ padding: "2px 4px", textAlign: "center" }}>{d.nationality || "—"}</td>
                  <td style={{ padding: "2px 4px", textAlign: "center" }}>{fmtDate(d.sinceDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* HISTÓRICO DE CHEQUES (SUSTADOS/EXTRAVIADOS)                         */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {checkFilings.length > 0 && (
        <>
          <SectionDivider />
          <SectionHeader title="HISTÓRICO DE CHEQUES (SUSTADOS/EXTRAVIADOS)" />
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                <th style={{ textAlign: "left",  padding: "2px 6px", width: "10%" }}>INCLUSÃO</th>
                <th style={{ textAlign: "left",  padding: "2px 6px" }}>BANCO / AGÊNCIA</th>
                <th style={{ textAlign: "left",  padding: "2px 6px", width: "10%" }}>CONTA</th>
                <th style={{ textAlign: "left",  padding: "2px 6px", width: "12%" }}>CHEQUE(S)</th>
                <th style={{ textAlign: "right", padding: "2px 6px", width: "8%" }}>QTDE</th>
                <th style={{ textAlign: "right", padding: "2px 6px", width: "14%" }}>VALOR</th>
                <th style={{ textAlign: "left",  padding: "2px 6px", width: "16%" }}>MOTIVO</th>
              </tr>
            </thead>
            <tbody>
              {checkFilings.map((r, i) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 6px" }}>{fmtDate(r.dateTimeInclusion?.slice(0, 10))}</td>
                  <td style={{ padding: "2px 6px", fontWeight: "bold" }}>
                    {r.bankName ?? "—"}{r.agencyNumber ? ` — ag. ${r.agencyNumber}` : ""}
                  </td>
                  <td style={{ padding: "2px 6px" }}>{r.accountNumberCheck ?? "—"}</td>
                  <td style={{ padding: "2px 6px" }}>
                    {r.initialCheckNumber == null ? "—"
                      : r.initialCheckNumber === r.finalCheckNumber ? r.initialCheckNumber
                      : `${r.initialCheckNumber}–${r.finalCheckNumber}`}
                  </td>
                  <td style={{ padding: "2px 6px", textAlign: "right" }}>{r.quantityCheck ?? "—"}</td>
                  <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.checkAmount)}</td>
                  <td style={{ padding: "2px 6px" }}>{r.briefDescriptionReason ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
            REGISTROS EXIBIDOS = {checkFilings.length} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(checkFilings.reduce((sum, r) => sum + (r.checkAmount ?? 0), 0))}
          </div>
        </>
      )}

      {/* Situação Cadastral — resumo rápido no lugar do card de Risco removido */}
      {cr && (
        <>
          <SectionDivider />
          {cr.statusRegistration && (
            <InfoRow label="SITUAÇÃO CADASTRAL" value={`${cr.statusCodeDescription || cr.statusRegistration}`} />
          )}
          {cr.numberEmployees != null && (
            <InfoRow label="Nº DE FUNCIONÁRIOS" value={String(cr.numberEmployees)} />
          )}
          {cr.branchOffices != null && (
            <InfoRow label="QUANTIDADE DE FILIAIS" value={fmtCount(cr.branchOffices)} />
          )}
          {cr.taxOption && (
            <InfoRow label="REGIME FISCAL" value={cr.taxOption} />
          )}
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* SEÇÃO 5 — CONSULTAS À SERASA                                       */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionDivider />
      <SectionHeader title="CONSULTAS À SERASA" />

      {inquiries.length === 0 ? (
        <p style={{ margin: "4px 0", fontSize: "8.5pt" }}>=== NENHUMA CONSULTA REGISTRADA ===</p>
      ) : (
        <>
          {inq?.inquiryCompanyResponse?.quantity?.actual != null && (
            <InfoRow
              label="CONSULTAS NOS ÚLTIMOS 30 DIAS"
              value={String(inq.inquiryCompanyResponse.quantity.actual)}
            />
          )}
          <table style={{ width: "100%", borderCollapse: "collapse", marginTop: "6px", fontSize: "8.5pt" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                <th style={{ textAlign: "left",  padding: "2px 4px", width: "15%" }}>DATA</th>
                <th style={{ textAlign: "left",  padding: "2px 4px" }}>EMPRESA CONSULTANTE</th>
                <th style={{ textAlign: "right", padding: "2px 4px", width: "20%" }}>CNPJ</th>
              </tr>
            </thead>
            <tbody>
              {inquiries.map((r: InquiryResult, i: number) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 4px", whiteSpace: "nowrap" }}>{fmtDate(r.occurrenceDate)}</td>
                  <td style={{ padding: "2px 4px" }}>{r.companyName}</td>
                  <td style={{ padding: "2px 4px", textAlign: "right", whiteSpace: "nowrap" }}>
                    {r.companyDocumentId ? fmtCNPJ(r.companyDocumentId) : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {inq?.inquiryCompanyResponse?.quantity?.historical && inq.inquiryCompanyResponse.quantity.historical.length > 0 && (
            <div style={{ marginTop: "6px" }}>
              <div style={{ fontSize: "8pt", fontWeight: "bold", marginBottom: "3px" }}>
                HISTÓRICO MENSAL DE CONSULTAS:
              </div>
              <div style={{ marginBottom: "6px" }}>
                {inquiryHistory.map((item, index) => {
                  const width = maxInquiryOccurrences > 0
                    ? Math.max(((item.occurrences ?? 0) / maxInquiryOccurrences) * 100, 4)
                    : 0;
                  return (
                    <div
                      key={`${item.inquiryDate ?? "month"}-${index}`}
                      style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "3px", fontSize: "8pt" }}
                    >
                      <div style={{ width: "52px", fontWeight: "bold", whiteSpace: "nowrap" }}>
                        {fmtDate(item.inquiryDate)}
                      </div>
                      <div style={{ flex: 1, height: "8px", background: "#e5e7eb", borderRadius: "999px", overflow: "hidden" }}>
                        <div
                          style={{
                            width: `${width}%`,
                            height: "100%",
                            background: "#612035",
                            borderRadius: "999px",
                          }}
                        />
                      </div>
                      <div style={{ width: "34px", textAlign: "right", fontWeight: "bold" }}>
                        {item.occurrences ?? 0}
                      </div>
                    </div>
                  );
                })}
              </div>
              {/* Grid compacta: 4 pares MÊS/ANO | QTDE por linha */}
              <table style={{ borderCollapse: "collapse", fontSize: "8pt", width: "100%" }}>
                <tbody>
                  {(() => {
                    const hist = inq.inquiryCompanyResponse!.quantity!.historical!;
                    const COLS = 4;
                    const rows: typeof hist[] = [];
                    for (let i = 0; i < hist.length; i += COLS) rows.push(hist.slice(i, i + COLS));
                    return rows.map((row, ri) => (
                      <tr key={ri}>
                        {row.map((h, ci) => (
                          <React.Fragment key={ci}>
                            <td style={{
                              border: "1px solid #ccc",
                              padding: "2px 6px",
                              background: "#f0f0f0",
                              fontWeight: "bold",
                              whiteSpace: "nowrap",
                              width: "13%",
                            }}>
                              {fmtDate(h.inquiryDate)}
                            </td>
                            <td style={{
                              border: "1px solid #ccc",
                              padding: "2px 6px",
                              textAlign: "center",
                              width: "10%",
                            }}>
                              {h.occurrences}
                            </td>
                          </React.Fragment>
                        ))}
                        {/* preenche colunas vazias na última linha */}
                        {Array.from({ length: COLS - row.length }).map((_, ci) => (
                          <React.Fragment key={`empty-${ci}`}>
                            <td style={{ border: "1px solid #ccc", background: "#f9f9f9" }} />
                            <td style={{ border: "1px solid #ccc" }} />
                          </React.Fragment>
                        ))}
                      </tr>
                    ));
                  })()}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* Pendências, Concentre e Recheque movidos para o final do relatório */}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* SEÇÃO 7 — HISTÓRICO DE PAGAMENTOS                                  */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {ca?.paymentHistory && (() => {
        const ph = ca.paymentHistory!;
        const titles      = ph.paymentHistory?.titlesQuantity ?? [];
        const months      = ph.paymentHistory?.monthDetail?.months ?? [];
        const evo         = ph.evolutionCommitmentsSuppliers;
        const bizRefs     = ph.businessReferences;
        const evoList     = evo?.evolutionCommitmentsSuppliersList ?? [];
        const refList     = bizRefs?.businessReferencesList ?? [];
        const seg         = ph.segmentData;
        const drawee      = seg?.drawee;
        const assignor    = seg?.assignor;
        const draweeMonths   = drawee?.paymentHistory?.monthDetail?.months ?? [];
        const assignorMonths = assignor?.paymentHistory?.monthDetail?.months ?? [];
        const draweeEvoList   = drawee?.evolutionCommitmentsSuppliers?.evolutionCommitmentsSuppliersList ?? [];
        const assignorEvoList = assignor?.evolutionCommitmentsSuppliers?.evolutionCommitmentsSuppliersList ?? [];
        const draweeRefList   = drawee?.businessReferences?.businessReferencesList ?? [];
        const assignorRefList = assignor?.businessReferences?.businessReferencesList ?? [];
        const draweeRelationship = drawee?.relationshipSuppliersPeriods;

        // ── Helpers reutilizáveis ─────────────────────────────────────────
        const SubHeader = ({ label, color = "#612035" }: { label: string; color?: string }) => (
          <div style={{ fontSize: "8pt", fontWeight: "bold", background: color, color: "#fff", padding: "3px 6px", marginTop: "6px", marginBottom: "3px" }}>
            {label}
          </div>
        );

        const MonthlyTable = ({ data }: { data: MonthDetailItem[] }) => {
          if (!data.length) return null;
          const periodNames = ["PONTUAL", "8-15", "16-30", "31-60", "+60", "A VISTA", "TOTAL MES"];
          return (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "7.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left", padding: "2px 4px", width: "10%" }}>MÊS/ANO</th>
                  {periodNames.map(n => (
                    <th key={n} style={{ textAlign: "center", padding: "2px 3px" }}>{n === "TOTAL MES" ? "TOTAL" : n}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.map((m: MonthDetailItem, i: number) => {
                  const getP = (name: string) => m.periodList?.find(p => p.name === name);
                  const cell = (name: string) => {
                    const p = getP(name);
                    const range = p?.range && p.range !== "-" ? p.range : null;
                    const pct   = p?.percentage && p.percentage !== "0.0% e 0.0%" ? p.percentage : null;
                    return (
                      <td key={name} style={{ padding: "2px 3px", textAlign: "center", verticalAlign: "top" }}>
                        {range ? <div>{range}</div> : <div style={{ color: "#aaa" }}>—</div>}
                        {pct && <div style={{ fontSize: "6.5pt", color: "#555" }}>{pct}</div>}
                      </td>
                    );
                  };
                  return (
                    <tr key={i} style={{ borderBottom: "1px dotted #bbb", background: i % 2 === 0 ? "#fff" : "#f9f9f9" }}>
                      <td style={{ padding: "2px 4px", fontWeight: "bold", whiteSpace: "nowrap" }}>{m.month}</td>
                      {periodNames.map(n => cell(n))}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          );
        };

        const EvoTable = ({ data }: { data: EvolutionCommitmentsEntry[] }) => {
          if (!data.length) return null;
          return (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left", padding: "2px 6px", width: "14%" }}>MÊS/ANO</th>
                  <th style={{ textAlign: "left", padding: "2px 6px" }}>A VENCER</th>
                  <th style={{ textAlign: "left", padding: "2px 6px" }}>VENCIDO</th>
                  <th style={{ textAlign: "left", padding: "2px 6px" }}>TOTAL MÊS</th>
                </tr>
              </thead>
              <tbody>
                {data.map((e: EvolutionCommitmentsEntry, i: number) => {
                  const hasOverdue = e.expiredTrackDescription && e.expiredTrackDescription !== "-";
                  return (
                    <tr key={i} style={{ borderBottom: "1px dotted #bbb", background: i % 2 === 0 ? "#fff" : "#f9f9f9" }}>
                      <td style={{ padding: "2px 6px", fontWeight: "bold" }}>{e.descriptionMonthCommitment}/{e.yearCommitment}</td>
                      <td style={{ padding: "2px 6px" }}>
                        {e.trackDescriptionToExpire && e.trackDescriptionToExpire !== "-" ? e.trackDescriptionToExpire : "—"}
                      </td>
                      <td style={{ padding: "2px 6px", fontWeight: hasOverdue ? "bold" : "normal", color: hasOverdue ? "#c00" : "inherit" }}>
                        {hasOverdue ? e.expiredTrackDescription : "—"}
                      </td>
                      <td style={{ padding: "2px 6px" }}>{e.totalMonthRangeDescription ?? "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          );
        };

        const RefTable = ({ data }: { data: BusinessReferenceEntry[] }) => {
          if (!data.length) return null;
          return (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left", padding: "2px 6px", width: "30%" }}>REFERENCIAL</th>
                  <th style={{ textAlign: "center", padding: "2px 6px", width: "18%" }}>DATA</th>
                  <th style={{ textAlign: "left", padding: "2px 6px" }}>VALOR</th>
                  <th style={{ textAlign: "left", padding: "2px 6px" }}>MÉDIA</th>
                </tr>
              </thead>
              <tbody>
                {data.map((ref: BusinessReferenceEntry, i: number) => {
                  const month = ref.monthPotentialDate ? String(ref.monthPotentialDate).padStart(2, "0") : null;
                  const dateLabel = month && ref.yearPotentialDate ? `${month}/${ref.yearPotentialDate}` : "—";
                  return (
                    <tr key={i} style={{ borderBottom: "1px dotted #bbb", background: i % 2 === 0 ? "#fff" : "#f9f9f9" }}>
                      <td style={{ padding: "2px 6px", fontWeight: "bold" }}>{ref.businessDescription}</td>
                      <td style={{ padding: "2px 6px", textAlign: "center" }}>{dateLabel}</td>
                      <td style={{ padding: "2px 6px" }}>{ref.potentialValueRangeDescription ?? "—"}</td>
                      <td style={{ padding: "2px 6px" }}>{ref.potentialMidrangeDescription ?? "—"}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          );
        };

        const RelationshipTable = ({ data }: { data: RelationshipSuppliersPeriods }) => {
          const periods = data.relationshipSuppliersPeriodList ?? [];
          const summary = data.summary;
          if (!periods.length && !summary) return null;

          return (
            <>
              {summary && (
                <div style={{ fontSize: "8pt", marginBottom: "4px" }}>
                  FONTES CONSULTADAS: {String(summary.sourcesTotal ?? 0).padStart(3, "0")}
                  &nbsp;&nbsp;|&nbsp;&nbsp; HIST. PAGAMENTOS: {summary.paymentHistorySources ?? 0}
                  &nbsp;&nbsp;|&nbsp;&nbsp; VALORES: {summary.paymentHistoryValuesSources ?? 0}
                  &nbsp;&nbsp;|&nbsp;&nbsp; COMPROMISSOS: {summary.evolutionCommitmentsSources ?? 0}
                  &nbsp;&nbsp;|&nbsp;&nbsp; REFERÊNCIAS: {summary.businessReferencesSources ?? 0}
                </div>
              )}
              {periods.length > 0 && (
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8pt", marginBottom: "4px" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                      {periods.map((item, index) => (
                        <th key={index} style={{ textAlign: "center", padding: "2px 4px" }}>
                          {normalizeRelationshipLabel(item.relationshipPeriodDescription)}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      {periods.map((item, index) => (
                        <td key={index} style={{ textAlign: "center", padding: "3px 4px", fontWeight: "bold" }}>
                          {item.relationshipSourceQuantity ?? 0}
                        </td>
                      ))}
                    </tr>
                  </tbody>
                </table>
              )}
            </>
          );
        };

        return (
          <>
            <SectionDivider />
            <SectionHeader title="HISTÓRICO DE PAGAMENTOS" />

            {/* ── 1. QTDE DE TÍTULOS (resumo geral) ── */}
            {titles.some((t: TitleQuantityEntry) => (t.range && t.range !== "-") || t.percentage) && (
              <>
                <SubHeader label="HISTÓRICO DE PAGAMENTOS — QTDE DE TÍTULOS (GERAL)" />
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "6px" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                      <th style={{ textAlign: "left",   padding: "2px 6px", width: "22%" }}>PRAZO</th>
                      <th style={{ textAlign: "center", padding: "2px 6px" }}>QTDE</th>
                      <th style={{ textAlign: "center", padding: "2px 6px" }}>%</th>
                    </tr>
                  </thead>
                  <tbody>
                    {titles.map((t: TitleQuantityEntry, i: number) => (
                      <tr key={i} style={{ borderBottom: "1px dotted #bbb", fontWeight: t.name === "PONTUAL" ? "bold" : "normal" }}>
                        <td style={{ padding: "2px 6px" }}>{t.name}</td>
                        <td style={{ padding: "2px 6px", textAlign: "center" }}>{t.range && t.range !== "-" ? t.range : "—"}</td>
                        <td style={{ padding: "2px 6px", textAlign: "center" }}>{t.percentage ?? "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}

            {/* ── 2. HISTÓRICO NO MERCADO (mensal) ── */}
            {months.length > 0 && (
              <>
                <SubHeader label="HISTÓRICO DE PAGAMENTOS NO MERCADO — VALORES EM R$" color="#2563EB" />
                <MonthlyTable data={months} />
              </>
            )}

            {/* ── 3. HISTÓRICO FACTORINGS — SACADO ── */}
            {draweeMonths.length > 0 && (
              <>
                <SubHeader label={`HISTÓRICO DE PAGAMENTOS — FACTORINGS (SACADO)${seg?.segmentDescription ? " — " + seg.segmentDescription : ""}`} />
                <MonthlyTable data={draweeMonths} />
              </>
            )}

            {draweeRelationship && ((draweeRelationship.relationshipSuppliersPeriodList?.length ?? 0) > 0 || draweeRelationship.summary) && (
              <>
                <SubHeader label="RELACIONAMENTO COM FACTORINGS (SACADO)" />
                <div style={{ fontSize: "7.5pt", marginBottom: "4px" }}>
                  {draweeRelationship.lastUpdateDate && <>ATUALIZAÇÃO EM {fmtDate(draweeRelationship.lastUpdateDate)}<br /></>}
                </div>
                <RelationshipTable data={draweeRelationship} />
              </>
            )}

            {/* ── 4. HISTÓRICO VISÃO CEDENTE ── */}
            {assignorMonths.length > 0 && (
              <>
                <SubHeader label="HISTÓRICO DE PAGAMENTOS — VISÃO CEDENTE" color="#16A34A" />
                <MonthlyTable data={assignorMonths} />
              </>
            )}

            {/* ── 5. EVOLUÇÃO DE COMPROMISSOS NO MERCADO ── */}
            {evoList.length > 0 && (
              <>
                <SectionDivider />
                <SectionHeader title="EVOLUÇÃO DE COMPROMISSOS NO MERCADO" date={evo?.lastUpdateDate} />
                <EvoTable data={evoList} />
              </>
            )}

            {/* ── 6. EVOLUÇÃO FACTORINGS — SACADO ── */}
            {draweeEvoList.length > 0 && (
              <>
                <SubHeader label="EVOLUÇÃO DE COMPROMISSOS — FACTORINGS (SACADO)" />
                <EvoTable data={draweeEvoList} />
              </>
            )}

            {/* ── 7. EVOLUÇÃO VISÃO CEDENTE ── */}
            {assignorEvoList.length > 0 && (
              <>
                <SubHeader label="EVOLUÇÃO DE COMPROMISSOS — VISÃO CEDENTE" color="#16A34A" />
                <EvoTable data={assignorEvoList} />
              </>
            )}

            {/* ── 8. REFERENCIAIS DE NEGÓCIOS NO MERCADO ── */}
            {refList.length > 0 && (
              <>
                <SectionDivider />
                <SectionHeader title="REFERENCIAIS DE NEGÓCIOS NO MERCADO" date={bizRefs?.lastUpdateDate} />
                <RefTable data={refList} />
              </>
            )}

            {/* ── 9. REFERENCIAIS FACTORINGS — SACADO ── */}
            {draweeRefList.length > 0 && (
              <>
                <SubHeader label="REFERENCIAIS DE NEGÓCIOS — FACTORINGS (SACADO)" />
                <RefTable data={draweeRefList} />
              </>
            )}

            {/* ── 10. REFERENCIAIS VISÃO CEDENTE ── */}
            {assignorRefList.length > 0 && (
              <>
                <SubHeader label="REFERENCIAIS DE NEGÓCIOS — VISÃO CEDENTE" color="#16A34A" />
                <RefTable data={assignorRefList} />
              </>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* PRAZO MÉDIO DE ATRASO (averageDelayPeriod)                         */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(ph?.paymentHistory?.averageDelayPeriod?.periodList ?? []).length > 0 && (
        <>
          <SectionDivider />
          <SectionHeader title="PRAZO MÉDIO DE ATRASO" />
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
            <thead>
              <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                <th style={{ textAlign: "left", padding: "2px 6px", width: "40%" }}>PERÍODO</th>
                <th style={{ textAlign: "left", padding: "2px 6px" }}>DIAS DE ATRASO</th>
              </tr>
            </thead>
            <tbody>
              {ph!.paymentHistory!.averageDelayPeriod!.periodList!.map((a: AverageDelayPeriodItem, i: number) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 6px", fontWeight: "bold" }}>{a.period}</td>
                  <td style={{ padding: "2px 6px" }}>
                    {a.averageDelayDaysFrom != null && a.averageDelayDaysTo != null
                      ? `${a.averageDelayDaysFrom} – ${a.averageDelayDaysTo} dias`
                      : a.averageDelayDaysFrom != null
                      ? `${a.averageDelayDaysFrom} dias`
                      : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* ANOTAÇÕES DA EMPRESA — PENDÊNCIAS FINANCEIRAS                       */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionDivider />
      <SectionHeader title="ANOTAÇÕES DA EMPRESA CONSULTADA" />

      {/* PENDÊNCIAS FINANCEIRAS */}
      <div style={{ fontWeight: "bold", fontSize: "8.5pt", marginTop: "6px", marginBottom: "3px", textDecoration: "underline" }}>
        PENDÊNCIAS FINANCEIRAS
      </div>
      {totalNegCount === 0 ? (
        <p style={{ margin: "3px 0", fontSize: "8.5pt" }}>
          {neg?.message
            ? `=== ${neg.message} ===`
            : "=== NADA CONSTA PARA O CNPJ CONSULTADO ==="}
        </p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "6px" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
              <th style={{ textAlign: "left",  padding: "2px 6px" }}>TIPO DE PENDÊNCIA</th>
              <th style={{ textAlign: "right", padding: "2px 6px", width: "12%" }}>QTDE</th>
              <th style={{ textAlign: "right", padding: "2px 6px", width: "22%" }}>VALOR TOTAL</th>
            </tr>
          </thead>
          <tbody>
            {negItems
              .filter(x => (x.data?.count ?? 0) > 0)
              .map((x, i) => (
                <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                  <td style={{ padding: "2px 6px" }}>{x.label}</td>
                  <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{x.data!.count}</td>
                  <td style={{ padding: "2px 6px", textAlign: "right" }}>{fmtCurrency(x.data!.balance)}</td>
                </tr>
              ))}
            <tr style={{ borderTop: "2px solid #000", fontWeight: "bold", background: "#f0f0f0" }}>
              <td style={{ padding: "3px 6px" }}>TOTAL GERAL</td>
              <td style={{ padding: "3px 6px", textAlign: "right" }}>{totalNegCount}</td>
              <td style={{ padding: "3px 6px", textAlign: "right" }}>{fmtCurrency(totalNegValue)}</td>
            </tr>
          </tbody>
        </table>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO PEFIN                                                  */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records: PefinRecord[] = (neg?.pefin as { pefinResponse?: PefinRecord[] } | undefined)?.pefinResponse ?? [];
        const totalCount = neg?.pefin?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="PEFIN — PENDÊNCIAS FINANCEIRAS (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px" }}>CREDOR</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "18%" }}>CONTRATO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "14%" }}>NATUREZA</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "14%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.creditorName ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.contractId ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.legalNature ?? r.legalNatureId ?? "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(neg?.pefin?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO REFIN                                                  */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records: PefinRecord[] = (neg?.refin as { refinResponse?: PefinRecord[] } | undefined)?.refinResponse ?? [];
        const totalCount = neg?.refin?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="REFIN — REFINANCIAMENTOS (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px" }}>CREDOR</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "18%" }}>CONTRATO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "14%" }}>NATUREZA</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "14%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.creditorName ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.contractId ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.legalNature ?? r.legalNatureId ?? "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(neg?.refin?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO COBRANÇAS                                              */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records: CollectionRecord[] = (neg?.collectionRecords as { collectionRecordsResponse?: CollectionRecord[] } | undefined)?.collectionRecordsResponse ?? [];
        const totalCount = neg?.collectionRecords?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="COBRANÇAS (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px" }}>CREDOR</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "18%" }}>CONTRATO</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "14%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.creditorName ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.contractId ?? "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(neg?.collectionRecords?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO CHEQUES                                                */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records: CheckRecord[] = (neg?.check as { checkResponse?: CheckRecord[] } | undefined)?.checkResponse ?? [];
        const totalCount = neg?.check?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="CHEQUES SEM FUNDO (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "14%" }}>BANCO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "14%" }}>AGÊNCIA</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "14%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.bankCode ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{r.agency ?? "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(neg?.check?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO AÇÕES JUDICIAIS                                        */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records = (inq?.judgementFilings as { judgementFilingsResponse?: { occurrenceDate?: string; inclusionDate?: string; legalNature?: string; legalNatureId?: string; amount?: number; distributor?: string; civilCourt?: string; city?: string; state?: string }[] } | undefined)?.judgementFilingsResponse ?? [];
        const totalCount = inq?.judgementFilings?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="AÇÕES JUDICIAIS (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "18%" }}>NATUREZA</th>
                  <th style={{ textAlign: "center",padding: "2px 6px", width: "12%" }}>DIST / VARA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px" }}>CIDADE / UF</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "16%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.legalNature ?? r.legalNatureId ?? "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "center" }}>{[r.distributor, r.civilCourt].filter(Boolean).join(" / ") || "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{[r.city, r.state].filter(Boolean).join(" / ") || "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(inq?.judgementFilings?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* DETALHAMENTO FALÊNCIAS / CONCORDATAS                                */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {(() => {
        const records = (inq?.bankrupts as { bankruptsResponse?: { occurrenceDate?: string; inclusionDate?: string; legalNature?: string; legalNatureId?: string; amount?: number; city?: string; state?: string }[] } | undefined)?.bankruptsResponse ?? [];
        const totalCount = inq?.bankrupts?.summary?.count ?? 0;
        if (records.length === 0) return null;
        return (
          <>
            <SectionDivider />
            <SectionHeader title="FALÊNCIAS / CONCORDATAS (DETALHAMENTO)" />
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
              <thead>
                <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>OCORRÊNCIA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "11%" }}>INCLUSÃO</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px", width: "20%" }}>NATUREZA</th>
                  <th style={{ textAlign: "left",  padding: "2px 6px" }}>CIDADE / UF</th>
                  <th style={{ textAlign: "right", padding: "2px 6px", width: "16%" }}>VALOR</th>
                </tr>
              </thead>
              <tbody>
                {records.map((r, i) => (
                  <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{fmtDate(r.inclusionDate)}</td>
                    <td style={{ padding: "2px 6px" }}>{r.legalNature ?? r.legalNatureId ?? "—"}</td>
                    <td style={{ padding: "2px 6px" }}>{[r.city, r.state].filter(Boolean).join(" / ") || "—"}</td>
                    <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{fmtCurrency(r.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
              REGISTROS EXIBIDOS = {records.length}{totalCount > records.length ? ` de ${totalCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(inq?.bankrupts?.summary?.balance)}
            </div>
            {totalCount > records.length && (
              <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                ATENÇÃO: A Serasa retornou {records.length} de {totalCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
              </div>
            )}
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* INFORMAÇÕES DO CONCENTRE (VALORES EM REAIS)                         */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionDivider />
      <SectionHeader
        title="INFORMAÇÕES DO CONCENTRE (VALORES EM REAIS)"
        date={concentreVal && 'updateDate' in concentreVal ? (concentreVal as { updateDate?: string }).updateDate : undefined}
      />

      {(() => {
        const notaryRecords = neg?.notary?.notaryResponse ?? [];
        const notaryCount   = neg?.notary?.summary?.count ?? 0;
        const notaryBalance = neg?.notary?.summary?.balance ?? 0;

        if (notaryCount === 0 && !concentreInfo.msg) {
          return (
            <p style={{ margin: "3px 0", fontSize: "8.5pt" }}>=== NADA CONSTA ===</p>
          );
        }

        return (
          <>
            {/* RESUMO */}
            {notaryCount > 0 && (
              <>
                <div style={{ fontSize: "8pt", fontWeight: "bold", marginBottom: "2px" }}>RESUMO</div>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "6px" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                      <th style={{ textAlign: "right",  padding: "2px 6px", width: "8%" }}>QTDE</th>
                      <th style={{ textAlign: "left",   padding: "2px 6px" }}>DISCRIMINAÇÃO</th>
                      <th style={{ textAlign: "right",  padding: "2px 6px", width: "20%" }}>VALOR</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{notaryCount}</td>
                      <td style={{ padding: "2px 6px" }}>PROTESTO(S) EM CARTÓRIO</td>
                      <td style={{ padding: "2px 6px", textAlign: "right" }}>{fmtCurrency(notaryBalance)}</td>
                    </tr>
                  </tbody>
                </table>
              </>
            )}

            {/* OCORRÊNCIAS MAIS RECENTES */}
            {notaryRecords.length > 0 && (
              <>
                <div style={{ fontSize: "8pt", fontWeight: "bold", marginBottom: "2px" }}>
                  OCORRÊNCIAS MAIS RECENTES (ATÉ {notaryRecords.length})
                </div>
                <div style={{ fontSize: "8pt", fontWeight: "bold", marginBottom: "3px" }}>PROTESTO</div>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt", marginBottom: "4px" }}>
                  <thead>
                    <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
                      <th style={{ textAlign: "left",  padding: "2px 6px", width: "14%" }}>DATA</th>
                      <th style={{ textAlign: "right", padding: "2px 6px", width: "18%" }}>VALOR</th>
                      <th style={{ textAlign: "center",padding: "2px 6px", width: "10%" }}>CARTÓRIO</th>
                      <th style={{ textAlign: "left",  padding: "2px 6px" }}>CIDADE</th>
                      <th style={{ textAlign: "center",padding: "2px 6px", width: "6%" }}>UF</th>
                    </tr>
                  </thead>
                  <tbody>
                    {notaryRecords.map((r: NotaryRecord, i: number) => (
                      <tr key={i} style={{ borderBottom: "1px dotted #bbb" }}>
                        <td style={{ padding: "2px 6px" }}>{fmtDate(r.occurrenceDate)}</td>
                        <td style={{ padding: "2px 6px", textAlign: "right" }}>{fmtCurrency(r.amount)}</td>
                        <td style={{ padding: "2px 6px", textAlign: "center" }}>{r.officeNumber ?? "—"}</td>
                        <td style={{ padding: "2px 6px" }}>{r.city || "—"}</td>
                        <td style={{ padding: "2px 6px", textAlign: "center" }}>{r.federalUnit || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div style={{ fontSize: "7.5pt", marginBottom: "3px" }}>
                  REGISTROS EXIBIDOS = {notaryRecords.length}{notaryCount > notaryRecords.length ? ` de ${notaryCount}` : ""} &nbsp;&nbsp; VALOR TOTAL = {fmtCurrency(notaryBalance)}
                </div>
                {notaryCount > notaryRecords.length && (
                  <div style={{ fontSize: "7.5pt", color: "#92400e", background: "#fffbeb", border: "1px solid #fcd34d", padding: "3px 6px", marginBottom: "4px" }}>
                    ATENÇÃO: A Serasa retornou {notaryRecords.length} de {notaryCount} registro(s). Podem existir pendências adicionais não exibidas neste relatório.
                  </div>
                )}
              </>
            )}

            {/* Participantes */}
            <p style={{ margin: "3px 0", fontSize: "8.5pt" }}>=== NADA CONSTA PARA O(S) PARTICIPANTE(S) ===</p>
          </>
        );
      })()}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* INFORMAÇÕES DO RECHEQUE (CHEQUES EXTRAVIADOS/SUSTADOS)              */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <SectionDivider />
      <SectionHeader
        title="INFORMAÇÕES DO RECHEQUE (CHEQUES EXTRAVIADOS/SUSTADOS)"
        date={recheckVal && 'updateDate' in recheckVal ? (recheckVal as { updateDate?: string }).updateDate : undefined}
      />
      {recheckInfo.count != null && recheckInfo.count > 0 ? (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "8.5pt" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #000", background: "#f0f0f0" }}>
              <th style={{ textAlign: "left",  padding: "2px 6px" }}>TIPO</th>
              <th style={{ textAlign: "right", padding: "2px 6px", width: "12%" }}>QTDE</th>
              <th style={{ textAlign: "right", padding: "2px 6px", width: "22%" }}>VALOR</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style={{ padding: "2px 6px" }}>CHEQUES EXTRAVIADOS / SUSTADOS</td>
              <td style={{ padding: "2px 6px", textAlign: "right", fontWeight: "bold" }}>{recheckInfo.count}</td>
              <td style={{ padding: "2px 6px", textAlign: "right" }}>{fmtCurrency(recheckInfo.balance)}</td>
            </tr>
          </tbody>
        </table>
      ) : (
        <p style={{ margin: "3px 0", fontSize: "8.5pt" }}>=== NADA CONSTA PARA O CNPJ CONSULTADO ===</p>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* RODAPÉ — AVISO DE CONFIDENCIALIDADE                                */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      <div style={{
        marginTop: "16px",
        borderTop: "2px solid #000",
        paddingTop: "8px",
        fontSize: "7.5pt",
        letterSpacing: "0.02em",
      }}>
        <div style={{ marginBottom: "2px" }}>
          ─────────────────────────────────────────────────────────────────────────────
        </div>
        <p style={{ margin: "2px 0" }}>
          ESTE RELATÓRIO É ESTRITAMENTE CONFIDENCIAL E DESTINADO A APOIAR DECISÕES DE CRÉDITO E NEGÓCIOS.
        </p>
        <p style={{ margin: "2px 0" }}>
          É PROIBIDA A REPRODUÇÃO, TOTAL OU PARCIAL, BEM COMO SUA DIVULGAÇÃO A TERCEIROS, POR QUALQUER FORMA.
        </p>
        <p style={{ margin: "2px 0" }}>
          A DECISÃO DE CONCEDER OU NÃO CRÉDITO É DE INTEIRA RESPONSABILIDADE DA EMPRESA CONCEDENTE.
        </p>
        <p style={{ marginTop: "6px", color: "#555" }}>
          Relatório gerado via Portal Serasa — {organizationName} — {now}
        </p>
      </div>

    </div>
  );
}
