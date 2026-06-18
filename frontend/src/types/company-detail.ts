// ── Negative data blobs ───────────────────────────────────────────────────────

export interface PefinRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  contractId?: string;
  creditorName?: string;
  amount?: number;
  principal?: boolean;
  cadus?: string;
}

/** REFIN tem o mesmo shape que PEFIN */
export type RefinRecord = PefinRecord;

export interface CollectionRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNature?: string;
  contractId?: string;
  creditorName?: string;
  amount?: number;
  cadus?: string;
}

export interface CheckRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  amount?: number;
  bankCode?: string;
  agency?: string;
  cadus?: string;
}

export interface NotaryRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  amount?: number;
  officeNumber?: string;
  city?: string;
  federalUnit?: string;
  cadus?: string;
}

export interface JudgementFilingRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  amount?: number;
  distributor?: string;
  civilCourt?: string;
  city?: string;
  state?: string;
  principal?: boolean;
  cadus?: string;
}

export interface BankruptRecord {
  occurrenceDate?: string;
  inclusionDate?: string;
  legalNatureId?: string;
  legalNature?: string;
  amount?: number;
  city?: string;
  state?: string;
  cadus?: string;
}

export interface NegativeSection {
  summary?: { count: number; balance: number; firstOccurrence?: string; lastOccurrence?: string };
  /** Registros individuais de PEFIN */
  pefinResponse?: PefinRecord[];
  /** Registros individuais de REFIN */
  refinResponse?: RefinRecord[];
  /** Registros individuais de protesto (notary) */
  notaryResponse?: NotaryRecord[];
  /** Registros individuais de cobranças */
  collectionRecordsResponse?: CollectionRecord[];
  /** Registros individuais de cheques */
  checkResponse?: CheckRecord[];
  /** Registros individuais de ações judiciais */
  judgementFilingsResponse?: JudgementFilingRecord[];
  /** Registros individuais de falências/concordatas */
  bankruptsResponse?: BankruptRecord[];
}

// ── Check Filings Historical (companyParticipationsReport no backend) ─────────

export interface CheckFilingsHistorical {
  checkFilingsHistoricalResponse?: unknown;
}

export interface NegativeSummary {
  message?: string;
  pefin?: NegativeSection;
  refin?: NegativeSection;
  check?: NegativeSection;
  notary?: NegativeSection;
  collectionRecords?: NegativeSection;
  concentre?: NegativeSection | { message?: string; updateDate?: string };
  recheque?: NegativeSection | { message?: string; updateDate?: string };
}

// ── Inquiry history (facts) ───────────────────────────────────────────────────

export interface InquiryResult {
  companyName: string;
  occurrenceDate: string;
  daysQuantity?: number;
  companyDocumentId?: string;
  companyAlias?: string;
}

export interface NegativeOrMessage {
  message?: string;
  updateDate?: string;
  summary?: { count: number; balance: number };
}

export interface InquiryHistory {
  bankrupts?: NegativeSection;
  judgementFilings?: NegativeSection;
  inquiryCompanyResponse?: {
    results: InquiryResult[];
    quantity?: {
      actual?: number;
      historical?: { inquiryDate: string; occurrences: number }[];
    };
  };
  concentre?: NegativeOrMessage;
  concentreRecords?: NegativeOrMessage;
  recheckOccurrences?: NegativeOrMessage;
  recheckData?: NegativeOrMessage;
  checkOccurrences?: NegativeOrMessage;
}

// ── QSA Report (partnerDetails) ───────────────────────────────────────────────

export interface QSAPartner {
  documentType?: string;
  documentId?: string;
  name: string;
  status?: string;
  sinceDate?: string;
  nationality?: string;
  restrictionSign?: boolean;
  capitalTotalValue?: number;
  capitalVoterValue?: number;
}

export interface QSADirector {
  documentType?: string;
  documentId?: string;
  name: string;
  status?: string;
  role?: string;
  sinceDate?: string;
  nationality?: string;
  maritalStatus?: string;
  restrictionSign?: boolean;
  documentConsistency?: boolean;
  informationUpdateDate?: string;
}

export interface QSAReport {
  companyData?: {
    socialCapitalValue?: number;
    accomplishedValue?: number;
    informationUpdateDate?: string;
    countryOrigin?: string;
    controlType?: string;
    nature?: string;
  };
  partnerCompleteReport?: {
    partnersList?: QSAPartner[];
  };
  directorCompleteReport?: {
    directorsList?: QSADirector[];
  };
}

// ── Identification Report (creditRatingDetails no backend) ────────────────────
// Armazena reports[0].identificationReport do RELATORIO_AVANCADO_PJ_ANALITICO

export interface IdentificationAddress {
  addressLine?: string;
  zipCode?: string;
  district?: string;
  city?: string;
  state?: string;
}

export interface IdentificationPhone {
  areaCode?: string;
  phoneNumber?: string;
}

export interface CompanyPredecessor {
  predecessorName?: string;
  predecessorDate?: string;
}

export interface IdentificationReport {
  updateDate?: string;
  documentNumber?: string;
  statusRegistration?: string;
  statusCode?: string;
  statusCodeDescription?: string;
  companyName?: string;
  companyAlias?: string;
  address?: IdentificationAddress;
  phone?: IdentificationPhone;
  companyUrl?: string;
  partnership?: string;
  companyRegister?: string;
  companyRegisterDate?: string;
  companyFoundation?: string;
  numberEmployees?: number;
  taxOption?: string;
  stateRegistration?: string;
  economicActivity?: string;
  importPurchases?: unknown;
  exportSales?: unknown;
  cnae?: string;
  serasaActiveCode?: string;
  branchOffices?: string | number;
  nireNumber?: string;
  predecessorList?: CompanyPredecessor[];
  reorganizations?: unknown;
  legalNatureCode?: string;
}

// ── Advanced Commercial Payment History ───────────────────────────────────────
// Armazena reports[0].advancedCommercialPaymentHistory

export interface TitleQuantityEntry {
  rangeCode?: string;
  name: string;
  range?: string;
  rangeValueFrom?: number;
  rangeValueTo?: number;
  percentage?: string;
  percentageFrom?: number;
  percentageTo?: number;
}

export interface MonthDetailSummaryEntry {
  periodDescription?: string;
  totalValueRangeDescription?: string;
  totalValueFrom?: number;
  totalValueTo?: number;
  averageValueRangeDescription?: string;
  percentageValueFrom?: number;
  percentageValueTo?: number;
  averagePaymentDelayPeriodRangeValueFrom?: number;
  averagePaymentDelayPeriodRangeValueTo?: number;
}

export interface MonthDetailSummary {
  punctual?: MonthDetailSummaryEntry;
  period8To15?: MonthDetailSummaryEntry;
  period16To30?: MonthDetailSummaryEntry;
  period31To60?: MonthDetailSummaryEntry;
  periodGT60?: MonthDetailSummaryEntry;
  spotPayment?: MonthDetailSummaryEntry;
  total?: MonthDetailSummaryEntry;
}

export interface MonthDetailItem {
  month: string;
  periodList: TitleQuantityEntry[];
  summary?: MonthDetailSummary;
}

export interface AverageDelayPeriodItem {
  period: string;
  averageDelayDaysFrom?: number;
  averageDelayDaysTo?: number;
}

export interface EvolutionCommitmentsEntry {
  yearCommitment: string;
  monthCommitment: string;
  descriptionMonthCommitment: string;
  expiredTrackCode?: string;
  expiredTrackDescription?: string;
  valueOverdueCommitmentsFrom?: string;
  valueOverdueCommitmentsTo?: string;
  trackCodeToExpire?: string;
  trackDescriptionToExpire?: string;
  valueCommitmentsDueFrom?: string;
  valueCommitmentsDueTo?: string;
  totalMonthRangeCode?: string;
  totalMonthRangeDescription?: string;
  totalMonthlyRangeValueFrom?: string;
  totalMonthlyRangeValueTo?: string;
}

export interface BusinessReferenceEntry {
  businessDescription: string;
  yearPotentialDate?: string;
  monthPotentialDate?: string;
  potentialValueRangeCode?: string;
  potentialValueRangeDescription?: string;
  potentialValueFrom?: string;
  potentialValueTo?: string;
  potentialMidrangeCode?: string;
  potentialMidrangeDescription?: string;
}

export interface RelationshipSuppliersPeriodItem {
  relationshipPeriodDescription?: string;
  relationshipSourceQuantity?: number;
}

export interface RelationshipSuppliersSummary {
  sourcesTotal?: number;
  paymentHistorySources?: number;
  paymentHistoryValuesSources?: number;
  evolutionCommitmentsSources?: number;
  businessReferencesSources?: number;
  spotPaymentBusinessReferencesSources?: number;
}

export interface RelationshipSuppliersPeriods {
  lastUpdateDate?: string;
  relationshipSuppliersPeriodList?: RelationshipSuppliersPeriodItem[];
  summary?: RelationshipSuppliersSummary;
}

export interface SegmentPaymentHistory {
  paymentHistory?: {
    monthDetail?: { months?: MonthDetailItem[]; summary?: MonthDetailSummary };
    titlesQuantity?: TitleQuantityEntry[];
    averageDelayPeriod?: { periodList?: AverageDelayPeriodItem[]; summary?: { averageDelayDaysFrom?: number; averageDelayDaysTo?: number } };
  };
  evolutionCommitmentsSuppliers?: {
    lastUpdateDate?: string;
    summary?: { count?: number; balance?: number };
    evolutionCommitmentsSuppliersList?: EvolutionCommitmentsEntry[];
  };
  businessReferences?: {
    lastUpdateDate?: string;
    businessReferencesList?: BusinessReferenceEntry[];
  };
  relationshipSuppliersPeriods?: RelationshipSuppliersPeriods;
}

export interface AdvancedCommercialPaymentHistory {
  paymentHistory?: {
    titlesQuantity?: TitleQuantityEntry[];
    monthDetail?: { months?: MonthDetailItem[]; summary?: MonthDetailSummary };
    averageDelayPeriod?: { periodList?: AverageDelayPeriodItem[]; summary?: { averageDelayDaysFrom?: number; averageDelayDaysTo?: number } };
  };
  evolutionCommitmentsSuppliers?: {
    lastUpdateDate?: string;
    summary?: { count?: number; balance?: number };
    evolutionCommitmentsSuppliersList?: EvolutionCommitmentsEntry[];
  };
  businessReferences?: {
    lastUpdateDate?: string;
    businessReferencesList?: BusinessReferenceEntry[];
  };
  segmentData?: {
    segmentDescription?: string;
    assignor?: SegmentPaymentHistory;
    drawee?: SegmentPaymentHistory;
    individual?: SegmentPaymentHistory;
  };
}

// ── CreditAnalysisData: espelho do CreditAnalysisResponse do backend ──────────

export interface CreditAnalysisData {
  id: number;
  clientId: string;
  cnpj: string;
  companyName: string;
  /** Não disponível no RELATORIO_AVANCADO_PJ_ANALITICO */
  score: number | null;
  /** Não disponível no RELATORIO_AVANCADO_PJ_ANALITICO */
  riskClass: string | null;
  /** Não disponível no RELATORIO_AVANCADO_PJ_ANALITICO */
  probability: number | null;
  analysisDate: string | null;
  negativeSummary: NegativeSummary;
  inquiryHistory: InquiryHistory;
  /** QSA avançado: sócios, diretores, dados da empresa */
  partnerDetails: QSAReport;
  /** Identificação da empresa (identificationReport do Serasa) */
  creditRatingDetails?: IdentificationReport;
  /** Histórico de pagamentos comerciais avançado */
  paymentHistory?: AdvancedCommercialPaymentHistory;
  /** Histórico de emissões de cheques */
  companyParticipationsReport?: CheckFilingsHistorical;
  /** PENDENTE | SIM | NAO — se a empresa opera como cedente em factoring/fomento */
  visaoCedente?: 'PENDENTE' | 'SIM' | 'NAO';
  consultaEm: string;
  aiAnalysis?: string | null;
  aiAnalysisDate?: string | null;
  status: string;
}

// ── Helpers ───────────────────────────────────────────────────────────────────

export function riskLabel(riskClass: string | undefined | null): string {
  const map: Record<string, string> = {
    '1': 'Muito Alto Risco',
    '2': 'Alto Risco',
    '3': 'Risco Moderado',
    '4': 'Baixo Risco',
    '5': 'Muito Baixo Risco',
  };
  return riskClass ? (map[riskClass] ?? `Classe ${riskClass}`) : 'Pendente';
}

export function totalDebtFromAnalysis(ca: CreditAnalysisData): number {
  const neg = ca.negativeSummary ?? {};
  const inq = ca.inquiryHistory ?? {};
  return (
    (neg.pefin?.summary?.balance ?? 0) +
    (neg.refin?.summary?.balance ?? 0) +
    (neg.check?.summary?.balance ?? 0) +
    (neg.notary?.summary?.balance ?? 0) +
    (neg.collectionRecords?.summary?.balance ?? 0) +
    (inq.bankrupts?.summary?.balance ?? 0) +
    (inq.judgementFilings?.summary?.balance ?? 0)
  );
}

export function totalPendingFromAnalysis(ca: CreditAnalysisData): number {
  const neg = ca.negativeSummary ?? {};
  const inq = ca.inquiryHistory ?? {};
  return (
    (neg.pefin?.summary?.count ?? 0) +
    (neg.refin?.summary?.count ?? 0) +
    (neg.check?.summary?.count ?? 0) +
    (neg.notary?.summary?.count ?? 0) +
    (neg.collectionRecords?.summary?.count ?? 0) +
    (inq.bankrupts?.summary?.count ?? 0) +
    (inq.judgementFilings?.summary?.count ?? 0)
  );
}

// ── Main entity types ─────────────────────────────────────────────────────────

export interface CompanyMember {
  name?: string;
  role?: { text: string } | string;
  person?: { name: string };
  documentNumber?: string;
}

export interface CompanyDetail {
  id: string;
  documentNumber: string;
  companyName: string;
  alias?: string;
  city: string;
  state: string;
  statusId: string;
  statusText: string;
  street?: string;
  number?: string;
  district?: string;
  zip?: string;
  zipCode?: string;
  details?: string;
  founded?: string;
  companyEquity?: number;
  updatedAt?: string;
  latitude?: number;
  longitude?: number;
  members?: CompanyMember[];
}

export interface Client {
  id: string;
  documentNumber: string;
  clientCode?: string | null;
  name: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreditAnalysisHistoryItem {
  id: number;
  companyName?: string;
  status?: string;
  visaoCedente?: string;
  riskClass?: string;
  consultaEm?: string;
  createdAt?: string;
}

export interface ClientProfile {
  client: Client | null;
  companyDetail: CompanyDetail | null;
  creditAnalysis: CreditAnalysisData | null;
  analysisHistory?: CreditAnalysisHistoryItem[];
  /** Análises PF dos sócios: key = CPF (11 dígitos). Apenas sócios já consultados. */
  partnerPfAnalyses?: Record<string, import('./person-analysis').PersonAnalysisSummary>;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
