// ── Seção registration (dados pessoais) ──────────────────────────────────────

export interface PersonAddress {
  addressLine?: string;
  addressTypeCode?: number;
  addressTypeDescription?: string;
  addressNumber?: string;
  district?: string;
  zipCode?: string;
  country?: string;
  city?: string;
  state?: string;
  addressComplement?: string;
  updateDate?: string;
}

export interface PersonPhone {
  regionCode?: number;
  areaCode?: number;
  phoneNumber?: number;
  phoneType?: string;
  phoneTypeCode?: number;
  updateDate?: string;
}

export interface PersonRegistration {
  documentNumber?: string;
  consumerName?: string;
  motherName?: string;
  consumerGender?: string;
  maritalStatus?: string;
  maritalStatusDescription?: string;
  birthDate?: string;
  birthSquareCity?: string;
  birthSquareState?: string;
  profession?: string;
  professionCode?: string;
  jobDescription?: string;
  occupationCode?: string;
  occupation?: string;
  corporateCompanyName?: string;
  corporateCompanyDocumentNumber?: string;
  educationLevelCode?: string;
  educationLevelDescription?: string;
  dependentsNumber?: string;
  statusRegistrationCode?: string;
  statusRegistration?: string;
  statusDate?: string;
  address?: PersonAddress;
  addresses?: PersonAddress[];
  phones?: PersonPhone[];
  /** RG / documento alternativo */
  alternativeDocumentNumber?: string;
  alternativeDocumentCode?: string;
  alternativeDocumentDescription?: string;
  alternativeDocumentDate?: string;
  issuingAgency?: string;
  issuingAgencyState?: string;
}

// ── Dados negativos (mesma estrutura que PJ) ──────────────────────────────────

export interface PFDebtBank {
  bankId?: number;
  bankName?: string;
  bankAgencyId?: number;
}

export interface PFPefinRefinRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  contractId?: string;
  creditorName?: string;
  amount?: number;
  principal?: boolean;
  legalSquare?: string;
  bank?: PFDebtBank;
  cadus?: string;
}

export interface PFCollectionRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNature?: string;
  contractId?: string;
  creditorName?: string;
  amount?: number;
  cadus?: string;
}

export interface PFCheckRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  amount?: number;
  bankCode?: string;
  bankName?: string;
  agency?: string;
  cadus?: string;
}

export interface PFNotaryRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  amount?: number;
  officeNumber?: string;
  city?: string;
  federalUnit?: string;
  cadus?: string;
}

export interface PFNegativeSection {
  summary?: { count: number; balance: number };
  pefinResponse?: PFPefinRefinRecord[];
  refinResponse?: PFPefinRefinRecord[];
  notaryResponse?: PFNotaryRecord[];
  collectionRecordsResponse?: PFCollectionRecord[];
  checkResponse?: PFCheckRecord[];
}

export interface PFNegativeSummary {
  pefin?: PFNegativeSection;
  refin?: PFNegativeSection;
  check?: PFNegativeSection;
  notary?: PFNegativeSection;
  collectionRecords?: PFNegativeSection;
}

// ── Fatos (consultas, processos, falências, docs roubados) ────────────────────

export interface PFInquiryItem {
  occurrenceDate?: string;
  segmentDescription?: string;
  daysQuantity?: number;
}

export interface PFMonthlyInquiryItem {
  inquiryDate?: string;
  occurrences?: number;
  bankOccurrences?: number;
  companyOccurrences?: number;
}

export interface PFJudgementFilingRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  amount?: number;
  distributor?: string;
  civilCourt?: string;
  city?: string;
  state?: string;
}

export interface PFBankruptRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  amount?: number;
  city?: string;
  state?: string;
}

export interface PFStolenDocumentRecord {
  occurrenceDate?: string;
  documentType?: string;
  documentNumber?: string;
  sourceDescription?: string;
}

export interface PFFacts {
  inquiry?: {
    inquiryResponse?: PFInquiryItem[];
    summary?: { count: number };
  };
  inquirySummary?: {
    inquiryQuantity?: {
      actual?: number;
      checkActual?: number;
      creditInquiriesQuantity?: PFMonthlyInquiryItem[];
      checkInquiriesQuantity?: PFMonthlyInquiryItem[];
    };
    summary?: { count: number; creditCount: number };
  };
  stolenDocuments?: {
    stolenDocumentsResponse?: PFStolenDocumentRecord[];
    summary?: { count: number; balance: number };
  };
  judgementFilings?: {
    judgementFilingsResponse?: PFJudgementFilingRecord[];
    summary?: { count: number; balance: number };
  };
  bankrupts?: {
    bankruptsResponse?: PFBankruptRecord[];
    summary?: { count: number; balance: number };
  };
}

// ── Empresas onde é sócio ─────────────────────────────────────────────────────

export interface PFPartnerCompany {
  businessDocument?: string;
  companyName?: string;
  participationPercentage?: number;
  companyStatus?: string;
  companyStatusCode?: string;
  companyState?: string;
  companyStatusDate?: string;
  updateDate?: string;
  participationInitialDate?: string;
  hasNegative?: boolean;
}

export interface PFPartnerCompanies {
  partnershipResponse?: PFPartnerCompany[];
  summary?: { count: number; balance: number };
}

// ── PersonAnalysisData — espelho do PersonAnalysisResponse do backend ─────────

export interface PersonAnalysisData {
  id: number;
  cpf: string;
  personName: string;
  registration: PersonRegistration;
  negativeSummary: PFNegativeSummary;
  facts: PFFacts;
  partnerCompanies: PFPartnerCompanies;
  registeredCompanyCnpjs?: string[];
  consultaEm: string;
  status: string;
  createdAt?: string;
  updatedAt?: string;
}

// ── Resumo (retornado no perfil da empresa junto com os sócios) ───────────────

export interface PersonAnalysisSummary {
  cpf: string;
  personName: string;
  consultaEm: string;
  hasNegative: boolean;
  negativeTotalCount: number;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

export function formatCpf(raw: string): string {
  const d = raw.replace(/\D/g, '');
  return d.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, '$1.$2.$3-$4');
}

export function totalNegativesFromPF(data: PersonAnalysisData): number {
  const neg = data.negativeSummary ?? {};
  const facts = data.facts ?? {};
  return (
    (neg.pefin?.summary?.count ?? 0) +
    (neg.refin?.summary?.count ?? 0) +
    (neg.check?.summary?.count ?? 0) +
    (neg.notary?.summary?.count ?? 0) +
    (neg.collectionRecords?.summary?.count ?? 0) +
    (facts.judgementFilings?.summary?.count ?? 0) +
    (facts.bankrupts?.summary?.count ?? 0)
  );
}

export function totalDebtFromPF(data: PersonAnalysisData): number {
  const neg = data.negativeSummary ?? {};
  const facts = data.facts ?? {};
  return (
    (neg.pefin?.summary?.balance ?? 0) +
    (neg.refin?.summary?.balance ?? 0) +
    (neg.check?.summary?.balance ?? 0) +
    (neg.notary?.summary?.balance ?? 0) +
    (neg.collectionRecords?.summary?.balance ?? 0) +
    (facts.judgementFilings?.summary?.balance ?? 0) +
    (facts.bankrupts?.summary?.balance ?? 0)
  );
}
